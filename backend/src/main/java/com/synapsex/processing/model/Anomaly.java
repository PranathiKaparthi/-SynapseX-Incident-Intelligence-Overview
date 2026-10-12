package com.synapsex.processing.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "anomalies")
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @Column(nullable = false, length = 50)
    private String severity = "WARNING";

    @Column(nullable = false, length = 50)
    private String status = "ACTIVE"; // ACTIVE, RECOVERED

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "current_value")
    private Double currentValue;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Anomaly() {}

    public Anomaly(
            UUID workspaceId,
            UUID ruleId,
            UUID serviceId,
            String serviceName,
            String metricName,
            String severity,
            Instant startTime,
            Double currentValue,
            Double thresholdValue,
            String evidenceJson) {
        this.workspaceId = workspaceId;
        this.ruleId = ruleId;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.metricName = metricName;
        this.severity = severity;
        this.status = "ACTIVE";
        this.startTime = startTime;
        this.currentValue = currentValue;
        this.thresholdValue = thresholdValue;
        this.evidenceJson = evidenceJson;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getRuleId() { return ruleId; }
    public UUID getServiceId() { return serviceId; }
    public String getServiceName() { return serviceName; }
    public String getMetricName() { return metricName; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public Double getCurrentValue() { return currentValue; }
    public Double getThresholdValue() { return thresholdValue; }
    public String getEvidenceJson() { return evidenceJson; }
}
