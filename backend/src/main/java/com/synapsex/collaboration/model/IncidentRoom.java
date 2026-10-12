package com.synapsex.collaboration.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_rooms")
public class IncidentRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false, unique = true)
    private UUID incidentId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public IncidentRoom() {}

    public IncidentRoom(UUID incidentId, UUID workspaceId, String topic) {
        this.incidentId = incidentId;
        this.workspaceId = workspaceId;
        this.topic = topic;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getTopic() { return topic; }
    public Instant getCreatedAt() { return createdAt; }
}
