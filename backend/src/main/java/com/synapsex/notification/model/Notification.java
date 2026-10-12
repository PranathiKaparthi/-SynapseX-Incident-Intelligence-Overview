package com.synapsex.notification.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(nullable = false, length = 50)
    private String type; // INCIDENT_CREATED, SEVERITY_ESCALATION, AI_INVESTIGATION_COMPLETED, WAR_ROOM_MENTION

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "dedupe_key", length = 255)
    private String dedupeKey;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Notification() {}

    public Notification(
            UUID workspaceId,
            UUID userId,
            UUID incidentId,
            String type,
            String title,
            String message,
            String dedupeKey) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.incidentId = incidentId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.dedupeKey = dedupeKey;
        this.isRead = false;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getUserId() { return userId; }
    public UUID getIncidentId() { return incidentId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getDedupeKey() { return dedupeKey; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public Instant getCreatedAt() { return createdAt; }
}
