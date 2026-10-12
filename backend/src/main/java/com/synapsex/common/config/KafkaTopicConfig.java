package com.synapsex.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String TELEMETRY_RAW_TOPIC = "synapsex.telemetry.raw";
    public static final String TELEMETRY_RAW_RETRY = "synapsex.telemetry.raw.retry";
    public static final String TELEMETRY_RAW_DLT = "synapsex.telemetry.raw.dlt";
    public static final String TELEMETRY_PROCESSED_TOPIC = "synapsex.telemetry.processed";
    public static final String ANOMALIES_TOPIC = "synapsex.anomalies";
    public static final String INCIDENTS_TOPIC = "synapsex.incidents";
    public static final String AI_TASKS_TOPIC = "synapsex.ai.tasks";
    public static final String NOTIFICATIONS_TOPIC = "synapsex.notifications";

    @Bean
    public NewTopic telemetryRawTopic() {
        return TopicBuilder.name(TELEMETRY_RAW_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic telemetryRawRetryTopic() {
        return TopicBuilder.name(TELEMETRY_RAW_RETRY).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic telemetryRawDltTopic() {
        return TopicBuilder.name(TELEMETRY_RAW_DLT).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic telemetryProcessedTopic() {
        return TopicBuilder.name(TELEMETRY_PROCESSED_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic anomaliesTopic() {
        return TopicBuilder.name(ANOMALIES_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic incidentsTopic() {
        return TopicBuilder.name(INCIDENTS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic aiTasksTopic() {
        return TopicBuilder.name(AI_TASKS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic notificationsTopic() {
        return TopicBuilder.name(NOTIFICATIONS_TOPIC).partitions(3).replicas(1).build();
    }
}
