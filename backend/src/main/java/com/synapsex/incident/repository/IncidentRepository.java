package com.synapsex.incident.repository;

import com.synapsex.incident.model.Incident;
import com.synapsex.incident.model.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    Optional<Incident> findByWorkspaceIdAndCorrelationKey(UUID workspaceId, String correlationKey);
    Page<Incident> findByWorkspaceIdOrderByStartedAtDesc(UUID workspaceId, Pageable pageable);
    List<Incident> findByWorkspaceIdAndStatus(UUID workspaceId, IncidentStatus status);
    long countByWorkspaceIdAndStatus(UUID workspaceId, IncidentStatus status);
    long countByWorkspaceId(UUID workspaceId);
}
