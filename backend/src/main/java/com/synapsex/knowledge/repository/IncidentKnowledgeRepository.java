package com.synapsex.knowledge.repository;

import com.synapsex.knowledge.model.IncidentKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentKnowledgeRepository extends JpaRepository<IncidentKnowledge, UUID> {
    List<IncidentKnowledge> findByWorkspaceId(UUID workspaceId);
    Optional<IncidentKnowledge> findByWorkspaceIdAndIncidentId(UUID workspaceId, UUID incidentId);
}
