package com.synapsex.collaboration.repository;

import com.synapsex.collaboration.model.IncidentRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRoomRepository extends JpaRepository<IncidentRoom, UUID> {
    Optional<IncidentRoom> findByIncidentId(UUID incidentId);
}
