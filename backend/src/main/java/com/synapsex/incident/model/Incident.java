package com.synapsex.incident.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workspace_id", "correlation_key"})
})
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IncidentStatus status = IncidentStatus.DETECTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSeverity severity = IncidentSeverity.SEV2;

    @Column(name = "correlation_key", nullable = false)
    private String correlationKey;

    @Column(name = "root_cause_service_id")
    private UUID rootCauseServiceId;

    @Column(name = "root_cause_service_name", length = 100)
    private String rootCauseServiceName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Incident() {}

    public Incident(
            UUID workspaceId,
            String title,
            String summary,
            IncidentStatus status,
            IncidentSeverity severity,
            String correlationKey,
            String rootCauseServiceName,
            Instant startedAt) {
        this.workspaceId = workspaceId;
        this.title = title;
        this.summary = summary;
        this.status = status;
        this.severity = severity;
        this.correlationKey = correlationKey;
        this.rootCauseServiceName = rootCauseServiceName;
        this.startedAt = startedAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public IncidentSeverity getSeverity() { return severity; }
    public void setSeverity(IncidentSeverity severity) { this.severity = severity; }
    public String getCorrelationKey() { return correlationKey; }
    public UUID getRootCauseServiceId() { return rootCauseServiceId; }
    public void setRootCauseServiceId(UUID rootCauseServiceId) { this.rootCauseServiceId = rootCauseServiceId; }
    public String getRootCauseServiceName() { return rootCauseServiceName; }
    public void setRootCauseServiceName(String rootCauseServiceName) { this.rootCauseServiceName = rootCauseServiceName; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
