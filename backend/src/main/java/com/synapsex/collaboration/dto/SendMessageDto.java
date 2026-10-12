package com.synapsex.collaboration.dto;

import com.synapsex.collaboration.model.MessageType;
import jakarta.validation.constraints.NotBlank;

public record SendMessageDto(
    String clientMessageId,
    @NotBlank(message = "Message content cannot be blank")
    String content,
    MessageType messageType,
    String attachmentsJson
) {}
