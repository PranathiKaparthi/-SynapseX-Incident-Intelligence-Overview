package com.synapsex.incident.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_dependencies", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"upstream_service_id", "downstream_service_id"})
})
public class ServiceDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "upstream_service_id", nullable = false)
    private UUID upstreamServiceId;

    @Column(name = "downstream_service_id", nullable = false)
    private UUID downstreamServiceId;

    @Column(name = "dependency_type", nullable = false, length = 50)
    private String dependencyType = "SYNCHRONOUS_HTTP";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ServiceDependency() {}

    public ServiceDependency(UUID workspaceId, UUID upstreamServiceId, UUID downstreamServiceId, String dependencyType) {
        this.workspaceId = workspaceId;
        this.upstreamServiceId = upstreamServiceId;
        this.downstreamServiceId = downstreamServiceId;
        this.dependencyType = dependencyType;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getUpstreamServiceId() { return upstreamServiceId; }
    public UUID getDownstreamServiceId() { return downstreamServiceId; }
    public String getDependencyType() { return dependencyType; }
}
