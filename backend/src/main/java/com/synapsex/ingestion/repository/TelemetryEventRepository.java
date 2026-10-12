package com.synapsex.ingestion.repository;

import com.synapsex.ingestion.model.TelemetryEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TelemetryEventRepository extends JpaRepository<TelemetryEvent, UUID> {

    boolean existsByWorkspaceIdAndEventId(UUID workspaceId, String eventId);

    Page<TelemetryEvent> findByWorkspaceIdOrderByEventTimeDesc(UUID workspaceId, Pageable pageable);

    List<TelemetryEvent> findByWorkspaceIdAndServiceNameAndEventTimeBetweenOrderByEventTimeAsc(
            UUID workspaceId, String serviceName, Instant from, Instant to);

    List<TelemetryEvent> findByWorkspaceIdAndEventTimeBetweenOrderByEventTimeAsc(
            UUID workspaceId, Instant from, Instant to);

    @Modifying
    @Query("DELETE FROM TelemetryEvent t WHERE t.eventTime < :cutoff")
    int deleteOldTelemetry(@Param("cutoff") Instant cutoff);
}
