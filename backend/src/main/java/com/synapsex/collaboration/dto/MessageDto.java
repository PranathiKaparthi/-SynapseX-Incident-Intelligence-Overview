package com.synapsex.collaboration.dto;

import com.synapsex.collaboration.model.MessageType;
import java.time.Instant;
import java.util.UUID;

public record MessageDto(
    UUID id,
    UUID roomId,
    String clientMessageId,
    long seqNum,
    UUID senderId,
    String senderName,
    String content,
    MessageType messageType,
    String attachmentsJson,
    Instant createdAt
) {}
