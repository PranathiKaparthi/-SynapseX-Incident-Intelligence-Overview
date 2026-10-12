package com.synapsex.incident.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_assignments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"incident_id", "user_id"})
})
public class IncidentAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public IncidentAssignment() {}

    public IncidentAssignment(UUID incidentId, UUID userId, UUID assignedBy) {
        this.incidentId = incidentId;
        this.userId = userId;
        this.assignedBy = assignedBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getUserId() { return userId; }
    public UUID getAssignedBy() { return assignedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
