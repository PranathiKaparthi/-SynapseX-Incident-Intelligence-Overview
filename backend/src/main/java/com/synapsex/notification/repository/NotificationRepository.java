package com.synapsex.notification.repository;

import com.synapsex.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<Notification> findByWorkspaceIdAndDedupeKey(UUID workspaceId, String dedupeKey);

    long countByWorkspaceIdAndIsReadFalse(UUID workspaceId);
}
