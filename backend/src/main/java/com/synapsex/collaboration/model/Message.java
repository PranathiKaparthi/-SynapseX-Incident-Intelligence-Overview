package com.synapsex.collaboration.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"room_id", "seq_num"})
})
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    @Column(name = "seq_num", nullable = false)
    private long seqNum;

    @Column(name = "sender_id")
    private UUID senderId;

    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 50)
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "attachments_json", columnDefinition = "TEXT")
    private String attachmentsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Message() {}

    public Message(
            UUID roomId,
            UUID workspaceId,
            String clientMessageId,
            long seqNum,
            UUID senderId,
            String senderName,
            String content,
            MessageType messageType,
            String attachmentsJson) {
        this.roomId = roomId;
        this.workspaceId = workspaceId;
        this.clientMessageId = clientMessageId;
        this.seqNum = seqNum;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.messageType = messageType;
        this.attachmentsJson = attachmentsJson;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRoomId() { return roomId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getClientMessageId() { return clientMessageId; }
    public long getSeqNum() { return seqNum; }
    public UUID getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getContent() { return content; }
    public MessageType getMessageType() { return messageType; }
    public String getAttachmentsJson() { return attachmentsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
