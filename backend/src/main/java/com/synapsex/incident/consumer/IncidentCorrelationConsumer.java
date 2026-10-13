package com.synapsex.incident.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.common.config.KafkaTopicConfig;
import com.synapsex.incident.model.Incident;
import com.synapsex.incident.service.CorrelationEngine;
import com.synapsex.processing.model.Anomaly;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class IncidentCorrelationConsumer {

    private static final Logger log = LoggerFactory.getLogger(IncidentCorrelationConsumer.class);

    private final CorrelationEngine correlationEngine;
    private final ObjectMapper objectMapper;

    public IncidentCorrelationConsumer(CorrelationEngine correlationEngine, ObjectMapper objectMapper) {
        this.correlationEngine = correlationEngine;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.ANOMALIES_TOPIC,
            groupId = "${spring.kafka.consumer.group-id:synapsex-consumer-group}-correlator",
            autoStartup = "${synapsex.kafka.listener.auto-startup:true}"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            Anomaly anomaly = objectMapper.readValue(record.value(), Anomaly.class);
            Incident incident = correlationEngine.correlateAnomaly(anomaly);
            if (incident != null) {
                log.info("Correlated anomaly into incident: {} (Root Cause: {})",
                        incident.getId(), incident.getRootCauseServiceName());
            }

            if (ack != null) {
                ack.acknowledge();
            }
        } catch (Exception e) {
            log.error("Failed to process anomaly correlation: {}", e.getMessage(), e);
            if (ack != null) {
                ack.acknowledge();
            }
        }
    }
}
