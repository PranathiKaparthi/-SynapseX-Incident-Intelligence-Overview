package com.synapsex.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TelemetryEnvelopeDto(
    @NotBlank(message = "eventId is required")
    String eventId,

    @NotBlank(message = "serviceName is required")
    String serviceName,

    @NotNull(message = "timestamp is required")
    Instant timestamp,

    @NotBlank(message = "eventType is required")
    String eventType,

    String environment,
    String severity,
    Map<String, Double> metrics,
    List<LogEntryDto> logs,
    Map<String, String> attributes,
    Boolean simulated
) {
    public record LogEntryDto(
        String level,
        String message,
        String traceId
    ) {}
}
