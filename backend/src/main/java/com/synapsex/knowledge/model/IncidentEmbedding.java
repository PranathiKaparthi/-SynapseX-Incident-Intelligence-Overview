package com.synapsex.knowledge.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_embeddings")
public class IncidentEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "knowledge_id", nullable = false)
    private UUID knowledgeId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "embedding_text", columnDefinition = "TEXT")
    private String embeddingText;

    @Column(name = "embedding_vector", columnDefinition = "TEXT")
    private String embeddingVector; // Stored as normalized comma-separated floats for portable vector distance calculation

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public IncidentEmbedding() {}

    public IncidentEmbedding(UUID knowledgeId, UUID workspaceId, String embeddingText, String embeddingVector) {
        this.knowledgeId = knowledgeId;
        this.workspaceId = workspaceId;
        this.embeddingText = embeddingText;
        this.embeddingVector = embeddingVector;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getKnowledgeId() { return knowledgeId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getEmbeddingText() { return embeddingText; }
    public String getEmbeddingVector() { return embeddingVector; }
    public Instant getCreatedAt() { return createdAt; }
}
