package com.synapsex.incident.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_events")
public class IncidentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // DETECTED, SEVERITY_CHANGED, STATUS_CHANGED, ASSIGNED, AI_INVESTIGATED, RESOLVED

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String actor = "SYSTEM";

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public IncidentEvent() {}

    public IncidentEvent(UUID incidentId, String eventType, String description, String actor, String payloadJson) {
        this.incidentId = incidentId;
        this.eventType = eventType;
        this.description = description;
        this.actor = actor;
        this.payloadJson = payloadJson;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getIncidentId() { return incidentId; }
    public String getEventType() { return eventType; }
    public String getDescription() { return description; }
    public String getActor() { return actor; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
}
