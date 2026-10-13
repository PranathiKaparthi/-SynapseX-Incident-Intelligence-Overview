package com.synapsex.knowledge.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_knowledge")
public class IncidentKnowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "root_cause", nullable = false, columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "resolution_procedure", nullable = false, columnDefinition = "TEXT")
    private String resolutionProcedure;

    @Column(name = "evidence_refs_json", columnDefinition = "TEXT")
    private String evidenceRefsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public IncidentKnowledge() {}

    public IncidentKnowledge(
            UUID workspaceId,
            UUID incidentId,
            String title,
            String summary,
            String rootCause,
            String resolutionProcedure,
            String evidenceRefsJson) {
        this.workspaceId = workspaceId;
        this.incidentId = incidentId;
        this.title = title;
        this.summary = summary;
        this.rootCause = rootCause;
        this.resolutionProcedure = resolutionProcedure;
        this.evidenceRefsJson = evidenceRefsJson;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getIncidentId() { return incidentId; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getRootCause() { return rootCause; }
    public String getResolutionProcedure() { return resolutionProcedure; }
    public String getEvidenceRefsJson() { return evidenceRefsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
