package com.synapsex.collaboration;

import com.synapsex.auth.model.SystemRole;
import com.synapsex.collaboration.dto.MessageDto;
import com.synapsex.collaboration.dto.PresenceHeartbeatDto;
import com.synapsex.collaboration.dto.SendMessageDto;
import com.synapsex.collaboration.model.IncidentRoom;
import com.synapsex.collaboration.model.Message;
import com.synapsex.collaboration.model.MessageType;
import com.synapsex.collaboration.repository.IncidentRoomRepository;
import com.synapsex.collaboration.repository.MessageRepository;
import com.synapsex.collaboration.service.PresenceService;
import com.synapsex.collaboration.service.WarRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarRoomServiceTest {

    @Mock private IncidentRoomRepository roomRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private WarRoomService warRoomService;
    private PresenceService presenceService;

    private UUID incidentId;
    private UUID workspaceId;
    private IncidentRoom sampleRoom;

    @BeforeEach
    void setUp() {
        warRoomService = new WarRoomService(roomRepository, messageRepository, messagingTemplate);
        presenceService = new PresenceService(null);

        incidentId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        sampleRoom = new IncidentRoom(incidentId, workspaceId, "War Room");
    }

    @Test
    @DisplayName("Monotonic sequence numbers: successive messages increment seqNum")
    void testMonotonicSequenceNumbering() {
        when(roomRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(sampleRoom));
        when(messageRepository.findByRoomIdAndClientMessageId(any(), any())).thenReturn(Optional.empty());

        // Previous message had seqNum 10
        Message prevMessage = new Message(sampleRoom.getId(), workspaceId, "c1", 10L, UUID.randomUUID(), "Bob", "Hello", MessageType.TEXT, null);
        when(messageRepository.findTopByRoomIdOrderBySeqNumDesc(sampleRoom.getId())).thenReturn(Optional.of(prevMessage));
        when(messageRepository.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

        SendMessageDto dto = new SendMessageDto("c2", "Investigating query locks", MessageType.TEXT, null);
        MessageDto result = warRoomService.sendMessage(incidentId, workspaceId, dto, UUID.randomUUID(), "Alice", SystemRole.ENGINEER);

        assertNotNull(result);
        assertEquals(11L, result.seqNum(), "Sequence number must be monotonically incremented from 10 to 11");
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/incidents/" + incidentId + "/war-room"), any(MessageDto.class));
    }

    @Test
    @DisplayName("Gap Recovery: since_seq parameter retrieves only subsequent messages")
    void testGapRecovery() {
        when(roomRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(sampleRoom));

        Message m3 = new Message(sampleRoom.getId(), workspaceId, "c3", 3L, UUID.randomUUID(), "Alice", "Msg 3", MessageType.TEXT, null);
        Message m4 = new Message(sampleRoom.getId(), workspaceId, "c4", 4L, UUID.randomUUID(), "Bob", "Msg 4", MessageType.TEXT, null);

        when(messageRepository.findByRoomIdAndSeqNumGreaterThanOrderBySeqNumAsc(sampleRoom.getId(), 2L))
                .thenReturn(List.of(m3, m4));

        List<MessageDto> recovered = warRoomService.getMessages(incidentId, 2L);

        assertEquals(2, recovered.size());
        assertEquals(3L, recovered.get(0).seqNum());
        assertEquals(4L, recovered.get(1).seqNum());
    }

    @Test
    @DisplayName("Idempotency: duplicate clientMessageId returns original message")
    void testClientMessageDeduplication() {
        when(roomRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(sampleRoom));

        Message existing = new Message(sampleRoom.getId(), workspaceId, "uuid-duplicate", 5L, UUID.randomUUID(), "Alice", "Original", MessageType.TEXT, null);
        when(messageRepository.findByRoomIdAndClientMessageId(sampleRoom.getId(), "uuid-duplicate"))
                .thenReturn(Optional.of(existing));

        SendMessageDto dto = new SendMessageDto("uuid-duplicate", "Original", MessageType.TEXT, null);
        MessageDto result = warRoomService.sendMessage(incidentId, workspaceId, dto, UUID.randomUUID(), "Alice", SystemRole.ENGINEER);

        assertEquals(5L, result.seqNum());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    @DisplayName("Role RBAC: VIEWER role is strictly prohibited from posting messages")
    void testViewerRoleRejection() {
        SendMessageDto dto = new SendMessageDto("c-view", "I am a viewer", MessageType.TEXT, null);

        assertThrows(AccessDeniedException.class, () ->
                warRoomService.sendMessage(incidentId, workspaceId, dto, UUID.randomUUID(), "Dana", SystemRole.VIEWER));
    }

    @Test
    @DisplayName("Presence: records online users with active heartbeats")
    void testPresenceTracking() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        presenceService.recordHeartbeat(incidentId, user1, "Alice", "ONLINE");
        presenceService.recordHeartbeat(incidentId, user2, "Bob", "TYPING");

        List<PresenceHeartbeatDto> online = presenceService.getOnlineUsers(incidentId);
        assertEquals(2, online.size());
    }
}
