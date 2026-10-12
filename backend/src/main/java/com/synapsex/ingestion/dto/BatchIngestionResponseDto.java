package com.synapsex.ingestion.dto;

import java.util.List;

public record BatchIngestionResponseDto(
    int total,
    int accepted,
    int rejected,
    List<EventResultDto> results
) {
    public record EventResultDto(
        String eventId,
        String status,
        String reason
    ) {
        public static EventResultDto accepted(String eventId) {
            return new EventResultDto(eventId, "ACCEPTED", null);
        }

        public static EventResultDto rejected(String eventId, String reason) {
            return new EventResultDto(eventId, "REJECTED", reason);
        }
    }
}
