package com.synapsex.collaboration.service;

import com.synapsex.auth.model.SystemRole;
import com.synapsex.collaboration.dto.MessageDto;
import com.synapsex.collaboration.dto.SendMessageDto;
import com.synapsex.collaboration.model.IncidentRoom;
import com.synapsex.collaboration.model.Message;
import com.synapsex.collaboration.model.MessageType;
import com.synapsex.collaboration.repository.IncidentRoomRepository;
import com.synapsex.collaboration.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WarRoomService {

    private static final Logger log = LoggerFactory.getLogger(WarRoomService.class);

    private final IncidentRoomRepository roomRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public WarRoomService(
            IncidentRoomRepository roomRepository,
            MessageRepository messageRepository,
            @Autowired(required = false) SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public IncidentRoom getOrCreateRoom(UUID incidentId, UUID workspaceId, String topic) {
        return roomRepository.findByIncidentId(incidentId).orElseGet(() ->
                roomRepository.save(new IncidentRoom(incidentId, workspaceId, topic != null ? topic : "Incident War Room")));
    }

    @Transactional
    public MessageDto sendMessage(
            UUID incidentId, UUID workspaceId, SendMessageDto dto,
            UUID senderId, String senderName, SystemRole role) {

        if (role == SystemRole.VIEWER) {
            throw new AccessDeniedException("Users with role VIEWER are restricted to read-only access in war rooms.");
        }

        IncidentRoom room = getOrCreateRoom(incidentId, workspaceId, "Incident " + incidentId);

        // Idempotent client send check
        if (dto.clientMessageId() != null && !dto.clientMessageId().isBlank()) {
            Optional<Message> existing = messageRepository.findByRoomIdAndClientMessageId(
                    room.getId(), dto.clientMessageId());
            if (existing.isPresent()) {
                log.debug("Duplicate client message id {} detected in room {}", dto.clientMessageId(), room.getId());
                return toDto(existing.get());
            }
        }

        // Sanitize content
        String sanitizedContent = sanitizeContent(dto.content());

        // Monotonic sequence numbering per room
        long nextSeq = messageRepository.findTopByRoomIdOrderBySeqNumDesc(room.getId())
                .map(m -> m.getSeqNum() + 1)
                .orElse(1L);

        MessageType type = dto.messageType() != null ? dto.messageType() : MessageType.TEXT;

        Message message = new Message(
                room.getId(),
                workspaceId,
                dto.clientMessageId(),
                nextSeq,
                senderId,
                senderName != null ? senderName : "Engineer",
                sanitizedContent,
                type,
                dto.attachmentsJson()
        );

        message = messageRepository.save(message);
        MessageDto resultDto = toDto(message);

        // Broadcast to WebSocket subscribers for this incident war room
        if (messagingTemplate != null) {
            try {
                messagingTemplate.convertAndSend("/topic/incidents/" + incidentId + "/war-room", resultDto);
            } catch (Exception e) {
                log.error("Failed to broadcast war room message to STOMP destination: {}", e.getMessage());
            }
        }

        return resultDto;
    }

    public List<MessageDto> getMessages(UUID incidentId, long sinceSeq) {
        Optional<IncidentRoom> roomOpt = roomRepository.findByIncidentId(incidentId);
        if (roomOpt.isEmpty()) {
            return List.of();
        }

        UUID roomId = roomOpt.get().getId();
        List<Message> list = sinceSeq > 0
                ? messageRepository.findByRoomIdAndSeqNumGreaterThanOrderBySeqNumAsc(roomId, sinceSeq)
                : messageRepository.findByRoomIdOrderBySeqNumAsc(roomId);

        return list.stream().map(this::toDto).toList();
    }

    private String sanitizeContent(String raw) {
        if (raw == null) return "";
        return raw.replace("<script>", "")
                  .replace("</script>", "")
                  .replace("<javascript>", "")
                  .trim();
    }

    private MessageDto toDto(Message m) {
        return new MessageDto(
                m.getId(),
                m.getRoomId(),
                m.getClientMessageId(),
                m.getSeqNum(),
                m.getSenderId(),
                m.getSenderName(),
                m.getContent(),
                m.getMessageType(),
                m.getAttachmentsJson(),
                m.getCreatedAt()
        );
    }
}
