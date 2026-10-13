package com.synapsex.processing.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.common.config.KafkaTopicConfig;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.processing.model.Anomaly;
import com.synapsex.processing.service.DetectionEngine;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class AnomalyDetectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionConsumer.class);

    private final DetectionEngine detectionEngine;
    private final ObjectMapper objectMapper;

    public AnomalyDetectionConsumer(DetectionEngine detectionEngine, ObjectMapper objectMapper) {
        this.detectionEngine = detectionEngine;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.TELEMETRY_PROCESSED_TOPIC,
            groupId = "${spring.kafka.consumer.group-id:synapsex-consumer-group}-anomaly-detector",
            autoStartup = "${synapsex.kafka.listener.auto-startup:true}"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String key = record.key();
            UUID workspaceId = extractWorkspaceId(key);
            TelemetryEnvelopeDto dto = objectMapper.readValue(record.value(), TelemetryEnvelopeDto.class);

            List<Anomaly> detected = detectionEngine.evaluateTelemetry(workspaceId, dto);
            if (!detected.isEmpty()) {
                log.info("Detected {} anomalies from event {} for workspace {}", detected.size(), dto.eventId(), workspaceId);
            }

            if (ack != null) {
                ack.acknowledge();
            }
        } catch (Exception e) {
            log.error("Failed to evaluate processed telemetry for anomalies: {}", e.getMessage(), e);
            if (ack != null) {
                ack.acknowledge();
            }
        }
    }

    private UUID extractWorkspaceId(String key) {
        if (key != null && key.contains(":")) {
            return UUID.fromString(key.split(":")[0]);
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
