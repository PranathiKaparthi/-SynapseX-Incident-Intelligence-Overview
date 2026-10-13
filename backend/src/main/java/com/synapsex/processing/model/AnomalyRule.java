package com.synapsex.processing.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "anomaly_rules")
public class AnomalyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @Column(nullable = false, length = 20)
    private String operator = "GREATER_THAN"; // GREATER_THAN, LESS_THAN, MONOTONIC_INCREASING

    @Column(nullable = false)
    private double threshold;

    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds = 120;

    @Column(name = "cooldown_seconds", nullable = false)
    private int cooldownSeconds = 300;

    @Column(nullable = false, length = 50)
    private String severity = "WARNING";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AnomalyRule() {}

    public AnomalyRule(
            UUID workspaceId,
            String name,
            String metricName,
            String operator,
            double threshold,
            int windowSeconds,
            int cooldownSeconds,
            String severity) {
        this.workspaceId = workspaceId;
        this.name = name;
        this.metricName = metricName;
        this.operator = operator;
        this.threshold = threshold;
        this.windowSeconds = windowSeconds;
        this.cooldownSeconds = cooldownSeconds;
        this.severity = severity;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    public UUID getServiceId() { return serviceId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }
    public String getMetricName() { return metricName; }
    public String getOperator() { return operator; }
    public double getThreshold() { return threshold; }
    public int getWindowSeconds() { return windowSeconds; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public String getSeverity() { return severity; }
    public boolean isEnabled() { return enabled; }
}
