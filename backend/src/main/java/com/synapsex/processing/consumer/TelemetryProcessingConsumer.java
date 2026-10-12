package com.synapsex.processing.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.common.config.KafkaTopicConfig;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.ingestion.model.ProcessedEvent;
import com.synapsex.ingestion.model.TelemetryEvent;
import com.synapsex.ingestion.repository.ProcessedEventRepository;
import com.synapsex.ingestion.repository.TelemetryEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class TelemetryProcessingConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProcessingConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final TelemetryEventRepository telemetryEventRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public TelemetryProcessingConsumer(
            ProcessedEventRepository processedEventRepository,
            TelemetryEventRepository telemetryEventRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate) {
        this.processedEventRepository = processedEventRepository;
        this.telemetryEventRepository = telemetryEventRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.TELEMETRY_RAW_TOPIC,
            groupId = "${spring.kafka.consumer.group-id:synapsex-consumer-group}",
            autoStartup = "${synapsex.kafka.listener.auto-startup:true}"
    )
    @Transactional
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String key = record.key();
        String payload = record.value();

        try {
            UUID workspaceId = extractWorkspaceId(key);
            TelemetryEnvelopeDto dto = objectMapper.readValue(payload, TelemetryEnvelopeDto.class);

            // Idempotency check: if eventId was already processed for this workspace, discard duplicate
            if (processedEventRepository.existsByWorkspaceIdAndEventId(workspaceId, dto.eventId())) {
                log.debug("Duplicate event ignored: workspace={}, eventId={}", workspaceId, dto.eventId());
                if (ack != null) ack.acknowledge();
                return;
            }

            // Persist deduplication record
            processedEventRepository.save(new ProcessedEvent(workspaceId, dto.eventId()));

            // Serialize internal sub-structures
            String metricsJson = dto.metrics() != null ? objectMapper.writeValueAsString(dto.metrics()) : null;
            String logsJson = dto.logs() != null ? objectMapper.writeValueAsString(dto.logs()) : null;
            String attrsJson = dto.attributes() != null ? objectMapper.writeValueAsString(dto.attributes()) : null;

            TelemetryEvent entity = new TelemetryEvent(
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
            );

            telemetryEventRepository.save(entity);

            // Forward to downstream processed telemetry topic for anomaly detection
            if (kafkaTemplate != null) {
                kafkaTemplate.send(KafkaTopicConfig.TELEMETRY_PROCESSED_TOPIC, key, payload);
            }

            if (ack != null) {
                ack.acknowledge();
            }
        } catch (Exception e) {
            log.error("Failed to process telemetry event. Forwarding to DLT. Error: {}", e.getMessage(), e);
            if (kafkaTemplate != null) {
                kafkaTemplate.send(KafkaTopicConfig.TELEMETRY_RAW_DLT, key, payload);
            }
            if (ack != null) {
                ack.acknowledge();
            }
        }
    }

    private UUID extractWorkspaceId(String key) {
        if (key != null && key.contains(":")) {
            return UUID.fromString(key.split(":")[0]);
        }
        // Fallback demo workspace
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
