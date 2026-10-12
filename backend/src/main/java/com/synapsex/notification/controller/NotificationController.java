package com.synapsex.notification.controller;

import com.synapsex.auth.security.UserPrincipal;
import com.synapsex.notification.model.Notification;
import com.synapsex.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Workspace-scoped notification stream and alert management")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List recent notifications for the workspace")
    public ResponseEntity<List<Notification>> getNotifications(@AuthenticationPrincipal UserPrincipal principal) {
        UUID workspaceId = extractWorkspaceId(principal);
        return ResponseEntity.ok(notificationService.getNotifications(workspaceId));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread notifications")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        UUID workspaceId = extractWorkspaceId(principal);
        long count = notificationService.getUnreadCount(workspaceId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<Notification> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID workspaceId = extractWorkspaceId(principal);
        return ResponseEntity.ok(notificationService.markAsRead(id, workspaceId));
    }

    @PostMapping("/test")
    @Operation(summary = "Trigger a test notification")
    public ResponseEntity<Notification> sendTestNotification(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        UUID workspaceId = extractWorkspaceId(principal);
        UUID userId = principal != null ? principal.getId() : null;
        String title = body.getOrDefault("title", "Test Alert");
        String message = body.getOrDefault("message", "This is an alert dispatched by SynapseX");
        String type = body.getOrDefault("type", "INCIDENT_CREATED");
        String dedupeKey = body.get("dedupeKey");

        Notification notification = notificationService.sendNotification(
                workspaceId, userId, null, type, title, message, dedupeKey
        );
        return ResponseEntity.ok(notification);
    }

    private UUID extractWorkspaceId(UserPrincipal principal) {
        return principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
