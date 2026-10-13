package com.synapsex.reliability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.processing.service.TelemetryPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Dead Letter Queue (DLQ) & Dead Letter Topic (DLT) Handler.
 * Intercepts poisoned, malformed, or unprocessable events with diagnostic metadata,
 * and provides safe replay mechanisms once root issues are remediated.
 */
@Service
public class DeadLetterQueueService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueService.class);

    public record DeadLetterEntry(
            String id,
            UUID workspaceId,
            String originalKey,
            String rawPayload,
            String failureReason,
            String exceptionClass,
            Instant queuedAt,
            int retryAttempts,
            String status // PENDING, REPLAYED, DISCARDED
    ) {}

    private final Map<String, DeadLetterEntry> deadLetters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final TelemetryPipelineService pipelineService;

    public DeadLetterQueueService(
            ObjectMapper objectMapper,
            @Autowired(required = false) TelemetryPipelineService pipelineService) {
        this.objectMapper = objectMapper;
        this.pipelineService = pipelineService;
    }

    public DeadLetterEntry routeToDeadLetter(
            UUID workspaceId, String key, String rawPayload, String reason, Throwable cause) {

        String id = "dlq-" + UUID.randomUUID().toString().substring(0, 8);
        String exc = cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : "UnknownError";

        DeadLetterEntry entry = new DeadLetterEntry(
                id,
                workspaceId,
                key,
                rawPayload,
                reason,
                exc,
                Instant.now(),
                0,
                "PENDING"
        );

        deadLetters.put(id, entry);
        log.warn("Event routed to Dead Letter Queue: id={}, reason={}, key={}", id, reason, key);
        return entry;
    }

    public List<DeadLetterEntry> getPendingEntries() {
        return deadLetters.values().stream()
                .filter(e -> "PENDING".equalsIgnoreCase(e.status()))
                .sorted(Comparator.comparing(DeadLetterEntry::queuedAt).reversed())
                .toList();
    }

    public boolean replayEntry(String id) {
        DeadLetterEntry entry = deadLetters.get(id);
        if (entry == null) {
            throw new NoSuchElementException("Dead letter entry not found: " + id);
        }

        try {
            TelemetryEnvelopeDto envelope = objectMapper.readValue(entry.rawPayload(), TelemetryEnvelopeDto.class);
            if (pipelineService != null) {
                TelemetryPipelineService.ProcessingResult result =
                        pipelineService.processSingle(envelope, entry.workspaceId());
                if (result.processed() > 0) {
                    deadLetters.put(id, new DeadLetterEntry(
                            entry.id(), entry.workspaceId(), entry.originalKey(),
                            entry.rawPayload(), entry.failureReason(), entry.exceptionClass(),
                            entry.queuedAt(), entry.retryAttempts() + 1, "REPLAYED"
                    ));
                    log.info("Dead letter entry {} successfully REPLAYED and processed", id);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Replay failed for dead letter {}: {}", id, e.getMessage());
            deadLetters.put(id, new DeadLetterEntry(
                    entry.id(), entry.workspaceId(), entry.originalKey(),
                    entry.rawPayload(), "Replay failure: " + e.getMessage(), e.getClass().getSimpleName(),
                    entry.queuedAt(), entry.retryAttempts() + 1, "PENDING"
            ));
            return false;
        }
    }

    public int getQueueDepth() {
        return (int) deadLetters.values().stream().filter(e -> "PENDING".equals(e.status())).count();
    }
}
