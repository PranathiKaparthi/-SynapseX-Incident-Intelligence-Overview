package com.synapsex.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.auth.model.Workspace;
import com.synapsex.auth.security.JwtService;
import com.synapsex.common.config.KafkaTopicConfig;
import com.synapsex.ingestion.dto.BatchIngestionResponseDto;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.ingestion.model.IngestionApiKey;
import com.synapsex.ingestion.repository.IngestionApiKeyRepository;
import com.synapsex.ingestion.repository.TelemetryEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionApiKeyRepository apiKeyRepository;
    private final TelemetryEventRepository telemetryEventRepository;
    private final RateLimiterService rateLimiterService;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private final Counter ingestedCounter;
    private final Counter rejectedCounter;

    @Value("${synapsex.ingestion.max-batch-size:500}")
    private int maxBatchSize = 500;

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public IngestionService(
            IngestionApiKeyRepository apiKeyRepository,
            TelemetryEventRepository telemetryEventRepository,
            RateLimiterService rateLimiterService,
            JwtService jwtService,
            ObjectMapper objectMapper,
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.apiKeyRepository = apiKeyRepository;
        this.telemetryEventRepository = telemetryEventRepository;
        this.rateLimiterService = rateLimiterService;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;

        this.ingestedCounter = meterRegistry.counter("synapsex_telemetry_ingested_total", "status", "accepted");
        this.rejectedCounter = meterRegistry.counter("synapsex_telemetry_ingested_total", "status", "rejected");
    }

    public Workspace authenticateApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new SecurityException("Missing API key header 'X-API-Key'");
        }

        String keyHash = jwtService.hashToken(rawApiKey.trim());
        Optional<IngestionApiKey> keyOpt = apiKeyRepository.findByKeyHash(keyHash);

        if (keyOpt.isEmpty() || !keyOpt.get().isActive()) {
            throw new SecurityException("Invalid or inactive ingestion API key");
        }

        return keyOpt.get().getWorkspace();
    }

    public void ingestSingle(TelemetryEnvelopeDto dto, Workspace workspace) {
        if (!rateLimiterService.tryConsume(workspace.getId().toString())) {
            rejectedCounter.increment();
            throw new IllegalArgumentException("Rate limit exceeded for workspace " + workspace.getId());
        }

        validateEnvelope(dto);
        publishToKafka(dto, workspace);
        ingestedCounter.increment();
    }

    public BatchIngestionResponseDto ingestBatch(List<TelemetryEnvelopeDto> batch, Workspace workspace) {
        if (batch.size() > maxBatchSize) {
            throw new IllegalArgumentException("Batch size " + batch.size() + " exceeds maximum allowed limit of " + maxBatchSize);
        }

        List<BatchIngestionResponseDto.EventResultDto> results = new ArrayList<>(batch.size());
        int accepted = 0;
        int rejected = 0;

        for (TelemetryEnvelopeDto dto : batch) {
            try {
                if (!rateLimiterService.tryConsume(workspace.getId().toString())) {
                    rejected++;
                    rejectedCounter.increment();
                    results.add(BatchIngestionResponseDto.EventResultDto.rejected(
                            dto.eventId() != null ? dto.eventId() : "unknown", "RATE_LIMIT_EXCEEDED"));
                    continue;
                }

                validateEnvelope(dto);
                publishToKafka(dto, workspace);
                accepted++;
                ingestedCounter.increment();
                results.add(BatchIngestionResponseDto.EventResultDto.accepted(dto.eventId()));
            } catch (Exception e) {
                rejected++;
                rejectedCounter.increment();
                results.add(BatchIngestionResponseDto.EventResultDto.rejected(
                        dto.eventId() != null ? dto.eventId() : "unknown", e.getMessage()));
            }
        }

        return new BatchIngestionResponseDto(batch.size(), accepted, rejected, results);
    }

    private void validateEnvelope(TelemetryEnvelopeDto dto) {
        if (dto.eventId() == null || dto.eventId().isBlank()) {
            throw new IllegalArgumentException("Field 'eventId' is mandatory");
        }
        if (dto.serviceName() == null || dto.serviceName().isBlank()) {
            throw new IllegalArgumentException("Field 'serviceName' is mandatory");
        }
        if (dto.timestamp() == null) {
            throw new IllegalArgumentException("Field 'timestamp' is mandatory");
        }
    }

    private void publishToKafka(TelemetryEnvelopeDto dto, Workspace workspace) {
        String partitionKey = workspace.getId() + ":" + dto.serviceName();
        try {
            String payloadJson = objectMapper.writeValueAsString(dto);
            if (kafkaTemplate != null) {
                kafkaTemplate.send(KafkaTopicConfig.TELEMETRY_RAW_TOPIC, partitionKey, payloadJson);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize telemetry envelope: {}", dto.eventId(), e);
            throw new IllegalArgumentException("Payload serialization error: " + e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 * * * *") // Run hourly
    @Transactional
    public void pruneOldTelemetry() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = telemetryEventRepository.deleteOldTelemetry(cutoff);
        if (deleted > 0) {
            log.info("Pruned {} expired telemetry events older than {}", deleted, cutoff);
        }
    }
}
