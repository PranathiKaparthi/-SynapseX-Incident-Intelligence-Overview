package com.synapsex.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.common.config.KafkaTopicConfig;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.processing.model.Anomaly;
import com.synapsex.processing.model.AnomalyRule;
import com.synapsex.processing.repository.AnomalyRepository;
import com.synapsex.processing.repository.AnomalyRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(DetectionEngine.class);

    private final AnomalyRuleRepository ruleRepository;
    private final AnomalyRepository anomalyRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // In-memory sliding windows: key = workspaceId:serviceName:metricName -> deque of MetricPoint
    private final Map<String, Deque<MetricPoint>> slidingWindows = new ConcurrentHashMap<>();

    // EWMA state tracker for statistical detection: key -> double
    private final Map<String, Double> ewmaTrackers = new ConcurrentHashMap<>();

    public record MetricPoint(Instant time, double value, String eventId) {}

    public DetectionEngine(
            AnomalyRuleRepository ruleRepository,
            AnomalyRepository anomalyRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate) {
        this.ruleRepository = ruleRepository;
        this.anomalyRepository = anomalyRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public List<Anomaly> evaluateTelemetry(UUID workspaceId, TelemetryEnvelopeDto dto) {
        if (dto.metrics() == null || dto.metrics().isEmpty()) {
            return List.of();
        }

        List<AnomalyRule> rules = ruleRepository.findByWorkspaceIdAndEnabledTrue(workspaceId);
        List<Anomaly> newlyDetected = new ArrayList<>();

        for (Map.Entry<String, Double> entry : dto.metrics().entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();
            if (value == null) continue;

            String windowKey = workspaceId + ":" + dto.serviceName() + ":" + metricName;
            updateSlidingWindow(windowKey, dto.timestamp(), value, dto.eventId());

            // 1. Rule-Based Evaluation
            for (AnomalyRule rule : rules) {
                if (!rule.getMetricName().equalsIgnoreCase(metricName)) continue;
                if (rule.getServiceId() != null) continue; // Generic rule applies to all services unless scoped

                boolean isBreaching = evaluateCondition(rule.getOperator(), value, rule.getThreshold());

                Optional<Anomaly> activeAnomalyOpt = anomalyRepository
                        .findFirstByWorkspaceIdAndServiceNameAndMetricNameAndStatus(
                                workspaceId, dto.serviceName(), metricName, "ACTIVE");

                if (isBreaching) {
                    if (activeAnomalyOpt.isEmpty()) {
                        // Open new anomaly
                        Anomaly anomaly = createAnomaly(workspaceId, rule, dto, metricName, value);
                        anomalyRepository.save(anomaly);
                        newlyDetected.add(anomaly);
                        publishAnomaly(anomaly);
                        log.warn("Anomaly DETECTED: service={}, metric={}, value={}, threshold={}",
                                dto.serviceName(), metricName, value, rule.getThreshold());
                    }
                } else {
                    // Check Auto-Recovery Detection: condition cleared
                    if (activeAnomalyOpt.isPresent()) {
                        Anomaly activeAnomaly = activeAnomalyOpt.get();
                        activeAnomaly.setStatus("RECOVERED");
                        activeAnomaly.setEndTime(Instant.now());
                        anomalyRepository.save(activeAnomaly);
                        log.info("Anomaly AUTO-RECOVERED: service={}, metric={}", dto.serviceName(), metricName);
                    }
                }
            }

            // 2. Statistical Detection (P2: EWMA & Z-Score)
            evaluateStatisticalAnomaly(workspaceId, dto, metricName, value, windowKey);
        }

        return newlyDetected;
    }

    private boolean evaluateCondition(String operator, double value, double threshold) {
        return switch (operator.toUpperCase()) {
            case "GREATER_THAN" -> value > threshold;
            case "LESS_THAN" -> value < threshold;
            case "MONOTONIC_INCREASING" -> value >= threshold;
            default -> value > threshold;
        };
    }

    private void updateSlidingWindow(String key, Instant time, double value, String eventId) {
        Deque<MetricPoint> window = slidingWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(new MetricPoint(time, value, eventId));
            // Retain points within last 5 minutes
            Instant cutoff = Instant.now().minusSeconds(300);
            while (!window.isEmpty() && window.peekFirst().time().isBefore(cutoff)) {
                window.pollFirst();
            }
        }
    }

    private void evaluateStatisticalAnomaly(
            UUID workspaceId, TelemetryEnvelopeDto dto, String metricName, double value, String windowKey) {

        // EWMA with alpha = 0.3
        double alpha = 0.3;
        ewmaTrackers.compute(windowKey, (k, prev) -> {
            if (prev == null) return value;
            double nextEwma = alpha * value + (1 - alpha) * prev;
            // If current value is over 2.5x the EWMA, flag statistical deviation
            if (value > prev * 2.5 && value > 50.0) {
                log.debug("Statistical EWMA spike detected on {}: val={}, ewma={}", windowKey, value, prev);
            }
            return nextEwma;
        });
    }

    private Anomaly createAnomaly(
            UUID workspaceId, AnomalyRule rule, TelemetryEnvelopeDto dto, String metricName, double value) {

        Map<String, Object> evidence = Map.of(
                "ruleName", rule.getName(),
                "operator", rule.getOperator(),
                "threshold", rule.getThreshold(),
                "observedValue", value,
                "eventIds", List.of(dto.eventId()),
                "detectionType", "RULE_THRESHOLD",
                "eventTime", dto.timestamp().toString()
        );

        String evidenceJson;
        try {
            evidenceJson = objectMapper.writeValueAsString(evidence);
        } catch (Exception e) {
            evidenceJson = "{}";
        }

        return new Anomaly(
                workspaceId,
                rule.getId(),
                null,
                dto.serviceName(),
                metricName,
                rule.getSeverity(),
                dto.timestamp(),
                value,
                rule.getThreshold(),
                evidenceJson
        );
    }

    private void publishAnomaly(Anomaly anomaly) {
        if (kafkaTemplate != null) {
            try {
                String payload = objectMapper.writeValueAsString(anomaly);
                kafkaTemplate.send(KafkaTopicConfig.ANOMALIES_TOPIC, anomaly.getWorkspaceId() + ":" + anomaly.getServiceName(), payload);
            } catch (Exception e) {
                log.error("Failed to publish anomaly event: {}", anomaly.getId(), e);
            }
        }
    }
}
