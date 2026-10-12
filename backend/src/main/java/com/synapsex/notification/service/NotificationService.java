package com.synapsex.notification.service;

import com.synapsex.notification.model.Notification;
import com.synapsex.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(
            NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public Notification sendNotification(
            UUID workspaceId,
            UUID userId,
            UUID incidentId,
            String type,
            String title,
            String message,
            String dedupeKey) {

        // Check deduplication
        if (dedupeKey != null && !dedupeKey.isBlank()) {
            Optional<Notification> existing = notificationRepository
                    .findByWorkspaceIdAndDedupeKey(workspaceId, dedupeKey);
            if (existing.isPresent()) {
                log.info("Suppressed duplicate notification with dedupeKey={}", dedupeKey);
                return existing.get();
            }
        }

        Notification notification = new Notification(
                workspaceId, userId, incidentId, type, title, message, dedupeKey
        );
        Notification saved = notificationRepository.save(notification);

        // Fan out real-time WebSocket alert to the workspace
        try {
            messagingTemplate.convertAndSend(
                    "/topic/workspace/" + workspaceId + "/notifications",
                    saved
            );
        } catch (Exception e) {
            log.warn("Failed to broadcast notification over WebSocket: {}", e.getMessage());
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotifications(UUID workspaceId) {
        return notificationRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }

    @Transactional
    public Notification markAsRead(UUID id, UUID workspaceId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));

        if (!notification.getWorkspaceId().equals(workspaceId)) {
            throw new SecurityException("Cross-workspace access denied");
        }

        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID workspaceId) {
        return notificationRepository.countByWorkspaceIdAndIsReadFalse(workspaceId);
    }
}
