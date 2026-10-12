package com.synapsex.ai.repository;

import com.synapsex.ai.model.AiInvestigation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiInvestigationRepository extends JpaRepository<AiInvestigation, UUID> {
    Optional<AiInvestigation> findTopByIncidentIdOrderByCreatedAtDesc(UUID incidentId);
}
