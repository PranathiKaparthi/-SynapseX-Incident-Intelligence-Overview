package com.synapsex.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_investigations")
public class AiInvestigation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 50)
    private String mode = "DEMO"; // LLM | DEMO

    @Column(nullable = false, length = 50)
    private String status = "COMPLETED";

    @Column(name = "insufficient_evidence", nullable = false)
    private boolean insufficientEvidence = false;

    @Column(name = "observations_json", columnDefinition = "TEXT")
    private String observationsJson;

    @Column(name = "hypotheses_json", columnDefinition = "TEXT")
    private String hypothesesJson;

    @Column(name = "recommendations_json", columnDefinition = "TEXT")
    private String recommendationsJson;

    @Column(name = "similar_incidents_json", columnDefinition = "TEXT")
    private String similarIncidentsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AiInvestigation() {}

    public AiInvestigation(
            UUID incidentId,
            UUID workspaceId,
            String mode,
            String status,
            boolean insufficientEvidence,
            String observationsJson,
            String hypothesesJson,
            String recommendationsJson,
            String similarIncidentsJson) {
        this.incidentId = incidentId;
        this.workspaceId = workspaceId;
        this.mode = mode;
        this.status = status;
        this.insufficientEvidence = insufficientEvidence;
        this.observationsJson = observationsJson;
        this.hypothesesJson = hypothesesJson;
        this.recommendationsJson = recommendationsJson;
        this.similarIncidentsJson = similarIncidentsJson;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getMode() { return mode; }
    public String getStatus() { return status; }
    public boolean isInsufficientEvidence() { return insufficientEvidence; }
    public String getObservationsJson() { return observationsJson; }
    public String getHypothesesJson() { return hypothesesJson; }
    public String getRecommendationsJson() { return recommendationsJson; }
    public String getSimilarIncidentsJson() { return similarIncidentsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
