package com.synapsex.processing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.incident.model.Incident;
import com.synapsex.incident.service.CorrelationEngine;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.ingestion.model.ProcessedEvent;
import com.synapsex.ingestion.model.TelemetryEvent;
import com.synapsex.ingestion.repository.ProcessedEventRepository;
import com.synapsex.ingestion.repository.TelemetryEventRepository;
import com.synapsex.processing.model.Anomaly;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelemetryPipelineService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryPipelineService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final TelemetryEventRepository telemetryEventRepository;
    private final DetectionEngine detectionEngine;
    private final CorrelationEngine correlationEngine;
    private final ObjectMapper objectMapper;
    private final Timer processingTimer;

    // Cache of recently processed eventIds to avoid redundant DB roundtrips during microbursts
    private final Set<String> recentProcessedIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public record ProcessingResult(
            int received,
            int processed,
            int duplicates,
            int anomaliesDetected,
            int incidentsCreatedOrUpdated,
            long durationNanos
    ) {}

    public TelemetryPipelineService(
            ProcessedEventRepository processedEventRepository,
            TelemetryEventRepository telemetryEventRepository,
            DetectionEngine detectionEngine,
            CorrelationEngine correlationEngine,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.processedEventRepository = processedEventRepository;
        this.telemetryEventRepository = telemetryEventRepository;
        this.detectionEngine = detectionEngine;
        this.correlationEngine = correlationEngine;
        this.objectMapper = objectMapper;
        this.processingTimer = meterRegistry.timer("synapsex_pipeline_processing_time");
    }

    @Transactional
    public ProcessingResult processBatch(List<TelemetryEnvelopeDto> batch, UUID workspaceId) {
        long start = System.nanoTime();
        if (batch == null || batch.isEmpty()) {
            return new ProcessingResult(0, 0, 0, 0, 0, 0);
        }

        List<TelemetryEvent> eventsToSave = new ArrayList<>(batch.size());
        List<ProcessedEvent> dedupeToSave = new ArrayList<>(batch.size());
        int duplicates = 0;
        int anomaliesCount = 0;
        int incidentsCount = 0;

        for (TelemetryEnvelopeDto dto : batch) {
            String dedupKey = workspaceId + ":" + dto.eventId();
            if (recentProcessedIds.contains(dedupKey) ||
                    processedEventRepository.existsByWorkspaceIdAndEventId(workspaceId, dto.eventId())) {
                duplicates++;
                continue;
            }

            recentProcessedIds.add(dedupKey);
            dedupeToSave.add(new ProcessedEvent(workspaceId, dto.eventId()));

            String metricsJson = serializeJson(dto.metrics());
            String logsJson = serializeJson(dto.logs());
            String attrsJson = serializeJson(dto.attributes());

            eventsToSave.add(new TelemetryEvent(
                    workspaceId,
                    dto.eventId(),
                    null,
                    dto.serviceName(),
                    dto.eventType(),
                    dto.environment() != null ? dto.environment() : "production",
                    dto.severity() != null ? dto.severity() : "INFO",
                    dto.timestamp(),
                    metricsJson,
                    logsJson,
                    attrsJson,
                    Boolean.TRUE.equals(dto.simulated())
            ));

            // Anomaly Detection
            List<Anomaly> detected = detectionEngine.evaluateTelemetry(workspaceId, dto);
            if (!detected.isEmpty()) {
                anomaliesCount += detected.size();
                for (Anomaly anomaly : detected) {
                    Incident incident = correlationEngine.correlateAnomaly(anomaly);
                    if (incident != null) {
                        incidentsCount++;
                    }
                }
            }
        }

        if (!dedupeToSave.isEmpty()) {
            processedEventRepository.saveAll(dedupeToSave);
        }
        if (!eventsToSave.isEmpty()) {
            telemetryEventRepository.saveAll(eventsToSave);
        }

        // Keep cache bounded to 50,000 recent items
        if (recentProcessedIds.size() > 50_000) {
            recentProcessedIds.clear();
        }

        long duration = System.nanoTime() - start;
        return new ProcessingResult(batch.size(), eventsToSave.size(), duplicates, anomaliesCount, incidentsCount, duration);
    }

    public ProcessingResult processSingle(TelemetryEnvelopeDto dto, UUID workspaceId) {
        return processBatch(List.of(dto), workspaceId);
    }

    private String serializeJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
