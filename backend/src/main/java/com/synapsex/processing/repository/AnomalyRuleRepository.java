package com.synapsex.processing.repository;

import com.synapsex.processing.model.AnomalyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnomalyRuleRepository extends JpaRepository<AnomalyRule, UUID> {
    List<AnomalyRule> findByWorkspaceIdAndEnabledTrue(UUID workspaceId);
}
