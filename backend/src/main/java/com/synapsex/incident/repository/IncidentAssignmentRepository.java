package com.synapsex.incident.repository;

import com.synapsex.incident.model.IncidentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentAssignmentRepository extends JpaRepository<IncidentAssignment, UUID> {
    List<IncidentAssignment> findByIncidentId(UUID incidentId);
    boolean existsByIncidentIdAndUserId(UUID incidentId, UUID userId);
}
