package com.synapsex.ingestion.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telemetry_events", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workspace_id", "event_id"})
})
public class TelemetryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(length = 50)
    private String environment = "production";

    @Column(length = 50)
    private String severity = "INFO";

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "ingested_time", nullable = false)
    private Instant ingestedTime = Instant.now();

    @Column(name = "metrics_json", columnDefinition = "TEXT")
    private String metricsJson;

    @Column(name = "logs_json", columnDefinition = "TEXT")
    private String logsJson;

    @Column(name = "attributes_json", columnDefinition = "TEXT")
    private String attributesJson;

    @Column(nullable = false)
    private boolean simulated = false;

    public TelemetryEvent() {}

    public TelemetryEvent(
            UUID workspaceId,
            String eventId,
            UUID serviceId,
            String serviceName,
            String eventType,
            String environment,
            String severity,
            Instant eventTime,
            String metricsJson,
            String logsJson,
            String attributesJson,
            boolean simulated) {
        this.workspaceId = workspaceId;
        this.eventId = eventId;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.eventType = eventType;
        this.environment = environment;
        this.severity = severity;
        this.eventTime = eventTime;
        this.ingestedTime = Instant.now();
        this.metricsJson = metricsJson;
        this.logsJson = logsJson;
        this.attributesJson = attributesJson;
        this.simulated = simulated;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getEventId() { return eventId; }
    public UUID getServiceId() { return serviceId; }
    public String getServiceName() { return serviceName; }
    public String getEventType() { return eventType; }
    public String getEnvironment() { return environment; }
    public String getSeverity() { return severity; }
    public Instant getEventTime() { return eventTime; }
    public Instant getIngestedTime() { return ingestedTime; }
    public String getMetricsJson() { return metricsJson; }
    public String getLogsJson() { return logsJson; }
    public String getAttributesJson() { return attributesJson; }
    public boolean isSimulated() { return simulated; }
}
