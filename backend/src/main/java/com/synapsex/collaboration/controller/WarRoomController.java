package com.synapsex.collaboration.controller;

import com.synapsex.auth.model.SystemRole;
import com.synapsex.auth.security.UserPrincipal;
import com.synapsex.collaboration.dto.MessageDto;
import com.synapsex.collaboration.dto.PresenceHeartbeatDto;
import com.synapsex.collaboration.dto.SendMessageDto;
import com.synapsex.collaboration.service.PresenceService;
import com.synapsex.collaboration.service.WarRoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class WarRoomController {

    private final WarRoomService warRoomService;
    private final PresenceService presenceService;

    public WarRoomController(WarRoomService warRoomService, PresenceService presenceService) {
        this.warRoomService = warRoomService;
        this.presenceService = presenceService;
    }

    @GetMapping("/api/incidents/{incidentId}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(
            @PathVariable UUID incidentId,
            @RequestParam(defaultValue = "0") long since_seq) {
        return ResponseEntity.ok(warRoomService.getMessages(incidentId, since_seq));
    }

    @PostMapping("/api/incidents/{incidentId}/messages")
    public ResponseEntity<MessageDto> postMessage(
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SendMessageDto dto) {

        UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID senderId = principal != null ? principal.getId() : null;
        String senderName = principal != null ? principal.getUsername() : "Engineer";
        SystemRole role = principal != null ? principal.getRole() : SystemRole.ENGINEER;

        MessageDto message = warRoomService.sendMessage(incidentId, workspaceId, dto, senderId, senderName, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @GetMapping("/api/incidents/{incidentId}/presence")
    public ResponseEntity<List<PresenceHeartbeatDto>> getPresence(@PathVariable UUID incidentId) {
        return ResponseEntity.ok(presenceService.getOnlineUsers(incidentId));
    }

    @PostMapping("/api/incidents/{incidentId}/presence")
    public ResponseEntity<Void> sendPresence(
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "ONLINE") String status) {

        if (principal != null) {
            presenceService.recordHeartbeat(incidentId, principal.getId(), principal.getUsername(), status);
        }
        return ResponseEntity.ok().build();
    }

    // STOMP Message Mappings
    @MessageMapping("/incidents/{incidentId}/send")
    public void handleStompMessage(@DestinationVariable UUID incidentId, SendMessageDto dto) {
        UUID demoWs = UUID.fromString("00000000-0000-0000-0000-000000000001");
        warRoomService.sendMessage(incidentId, demoWs, dto, null, "WebSocket Client", SystemRole.ENGINEER);
    }

    @MessageMapping("/incidents/{incidentId}/presence")
    public void handleStompPresence(@DestinationVariable UUID incidentId, PresenceHeartbeatDto dto) {
        presenceService.recordHeartbeat(incidentId, dto.userId(), dto.userName(), dto.status());
    }
}
