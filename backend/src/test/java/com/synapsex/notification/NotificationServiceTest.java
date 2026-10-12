package com.synapsex.notification;

import com.synapsex.notification.model.Notification;
import com.synapsex.notification.repository.NotificationRepository;
import com.synapsex.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private SimpMessagingTemplate messagingTemplate;
    private NotificationService notificationService;

    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        notificationRepository = Mockito.mock(NotificationRepository.class);
        messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        notificationService = new NotificationService(notificationRepository, messagingTemplate);

        workspaceId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should successfully persist notification and broadcast to workspace WebSocket topic")
    void testSendNotificationSuccess() {
        Notification saved = new Notification(
                workspaceId, null, null, "INCIDENT_CREATED", "New Incident SEV1", "Details", "dedupe-key-1"
        );
        saved.setId(UUID.randomUUID());

        when(notificationRepository.findByWorkspaceIdAndDedupeKey(workspaceId, "dedupe-key-1"))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        Notification result = notificationService.sendNotification(
                workspaceId, null, null, "INCIDENT_CREATED", "New Incident SEV1", "Details", "dedupe-key-1"
        );

        assertNotNull(result);
        assertEquals(saved.getId(), result.getId());
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/workspace/" + workspaceId + "/notifications"),
                eq(saved)
        );
    }

    @Test
    @DisplayName("Should suppress duplicate notification when dedupeKey exists to prevent alert fatigue")
    void testDuplicateNotificationSuppression() {
        Notification existing = new Notification(
                workspaceId, null, null, "INCIDENT_CREATED", "Existing Alert", "Details", "dedupe-key-2"
        );
        existing.setId(UUID.randomUUID());

        when(notificationRepository.findByWorkspaceIdAndDedupeKey(workspaceId, "dedupe-key-2"))
                .thenReturn(Optional.of(existing));

        Notification result = notificationService.sendNotification(
                workspaceId, null, null, "INCIDENT_CREATED", "Existing Alert", "Details", "dedupe-key-2"
        );

        assertEquals(existing.getId(), result.getId());
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should prevent cross-workspace access when marking notification as read")
    void testMarkAsReadCrossWorkspaceDefense() {
        UUID otherWorkspaceId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        Notification notif = new Notification(
                otherWorkspaceId, null, null, "INCIDENT_CREATED", "Alert", "Details", null
        );
        notif.setId(notifId);

        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(notif));

        assertThrows(SecurityException.class, () -> {
            notificationService.markAsRead(notifId, workspaceId);
        });
    }
}
