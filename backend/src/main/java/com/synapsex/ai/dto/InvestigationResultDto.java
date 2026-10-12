package com.synapsex.ai.dto;

import java.util.List;

public record InvestigationResultDto(
    String incidentId,
    String mode, // LLM | DEMO
    List<ObservationDto> observations,
    List<HypothesisDto> hypotheses,
    List<RecommendationDto> recommendedInvestigation,
    List<SimilarIncidentDto> similarIncidents,
    boolean insufficientEvidence
) {
    public record ObservationDto(
        String text,
        List<String> evidenceRefs
    ) {}

    public record HypothesisDto(
        int rank,
        String cause,
        String confidence, // LOW | MEDIUM | HIGH
        List<String> supportingEvidence,
        List<String> contradictingEvidence
    ) {}

    public record RecommendationDto(
        String step,
        String risk // READ_ONLY | ADVISORY | MANUAL_ACTION
    ) {}

    public record SimilarIncidentDto(
        String incidentId,
        double similarity,
        String resolution
    ) {}
}
