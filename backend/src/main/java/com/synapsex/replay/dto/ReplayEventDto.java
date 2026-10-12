package com.synapsex.replay.dto;

import java.time.Instant;
import java.util.Map;

public record ReplayEventDto(
    String eventId,
    long sequenceId,
    Instant timestamp,
    String eventType, // TELEMETRY | ANOMALY | TIMELINE_EVENT | WAR_ROOM_MESSAGE | AI_UPDATE
    String source,
    String severity,
    String description,
    Map<String, Object> data
) {}
