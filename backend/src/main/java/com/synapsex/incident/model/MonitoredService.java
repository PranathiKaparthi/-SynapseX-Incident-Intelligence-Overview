package com.synapsex.incident.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "services", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workspace_id", "name"})
})
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String environment = "production";

    @Column(nullable = false, length = 20)
    private String tier = "TIER_1";

    @Column(name = "health_status", nullable = false, length = 50)
    private String healthStatus = "HEALTHY";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public MonitoredService() {}

    public MonitoredService(UUID id, UUID workspaceId, String name, String environment, String tier, String healthStatus) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.name = name;
        this.environment = environment;
        this.tier = tier;
        this.healthStatus = healthStatus;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    public String getEnvironment() { return environment; }
    public String getTier() { return tier; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
}
