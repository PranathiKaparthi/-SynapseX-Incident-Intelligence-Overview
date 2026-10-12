package com.synapsex.collaboration.dto;

import java.util.UUID;

public record PresenceHeartbeatDto(
    UUID incidentId,
    UUID userId,
    String userName,
    String status, // ONLINE, TYPING, AWAY
    long timestamp
) {}
