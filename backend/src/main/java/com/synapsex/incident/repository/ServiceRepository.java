package com.synapsex.incident.repository;

import com.synapsex.incident.model.MonitoredService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<MonitoredService, UUID> {
    Optional<MonitoredService> findByWorkspaceIdAndName(UUID workspaceId, String name);
    List<MonitoredService> findByWorkspaceId(UUID workspaceId);
    long countByWorkspaceIdAndHealthStatus(UUID workspaceId, String healthStatus);
    long countByWorkspaceId(UUID workspaceId);
}
