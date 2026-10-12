package com.synapsex.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_executions")
public class AgentExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Column(name = "agent_type", nullable = false, length = 50)
    private String agentType; // TELEMETRY, LOG, DEPENDENCY, HISTORICAL, REVIEW

    @Column(nullable = false, length = 50)
    private String status = "SUCCESS"; // SUCCESS, FAILED, TIMED_OUT

    @Column(name = "duration_ms", nullable = false)
    private long durationMs = 0;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AgentExecution() {}

    public AgentExecution(
            UUID investigationId,
            String agentType,
            String status,
            long durationMs,
            String resultSummary,
            String errorMessage) {
        this.investigationId = investigationId;
        this.agentType = agentType;
        this.status = status;
        this.durationMs = durationMs;
        this.resultSummary = resultSummary;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getInvestigationId() { return investigationId; }
    public String getAgentType() { return agentType; }
    public String getStatus() { return status; }
    public long getDurationMs() { return durationMs; }
    public String getResultSummary() { return resultSummary; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
