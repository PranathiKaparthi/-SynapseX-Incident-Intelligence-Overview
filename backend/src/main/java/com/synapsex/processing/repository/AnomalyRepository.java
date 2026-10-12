package com.synapsex.processing.repository;

import com.synapsex.processing.model.Anomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnomalyRepository extends JpaRepository<Anomaly, UUID> {
    List<Anomaly> findByWorkspaceIdAndStatus(UUID workspaceId, String status);
    Optional<Anomaly> findFirstByWorkspaceIdAndServiceNameAndMetricNameAndStatus(
            UUID workspaceId, String serviceName, String metricName, String status);
    List<Anomaly> findByWorkspaceIdOrderByStartTimeDesc(UUID workspaceId);
}
