package com.synapsex.ai.service;

import com.synapsex.ai.dto.InvestigationResultDto;
import com.synapsex.ingestion.repository.TelemetryEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiInvestigationValidator {

    private static final Logger log = LoggerFactory.getLogger(AiInvestigationValidator.class);

    private final TelemetryEventRepository telemetryEventRepository;

    public AiInvestigationValidator(TelemetryEventRepository telemetryEventRepository) {
        this.telemetryEventRepository = telemetryEventRepository;
    }

    public InvestigationResultDto validateAndEnforce(UUID workspaceId, InvestigationResultDto raw) {
        if (raw == null) {
            return createInsufficientEvidenceFallback("unknown");
        }

        // Validate observation evidence citations
        List<InvestigationResultDto.ObservationDto> validObservations = new ArrayList<>();

        if (raw.observations() != null) {
            for (InvestigationResultDto.ObservationDto obs : raw.observations()) {
                if (obs.evidenceRefs() == null || obs.evidenceRefs().isEmpty()) {
                    log.warn("Dropping uncited AI observation: '{}'", obs.text());
                    continue; // Drop uncited claims
                }

                // Verify cited eventIds against DB
                List<String> verifiedRefs = new ArrayList<>();
                for (String refId : obs.evidenceRefs()) {
                    if (telemetryEventRepository.existsByWorkspaceIdAndEventId(workspaceId, refId)) {
                        verifiedRefs.add(refId);
                    } else {
                        log.warn("Observation references non-existent eventId '{}'", refId);
                    }
                }

                if (!verifiedRefs.isEmpty()) {
                    validObservations.add(new InvestigationResultDto.ObservationDto(obs.text(), verifiedRefs));
                } else {
                    log.warn("Dropping observation whose citations could not be verified in database: '{}'", obs.text());
                }
            }
        }

        // Check if evidence is insufficient
        if (validObservations.isEmpty()) {
            log.info("Insufficient verified evidence found for incident {}. Triggering fallback.", raw.incidentId());
            return createInsufficientEvidenceFallback(raw.incidentId());
        }

        // Ensure hypotheses only keep verified evidence
        List<InvestigationResultDto.HypothesisDto> validHypotheses = new ArrayList<>();
        if (raw.hypotheses() != null) {
            for (InvestigationResultDto.HypothesisDto h : raw.hypotheses()) {
                List<String> validRefs = h.supportingEvidence() != null
                        ? h.supportingEvidence().stream()
                            .filter(id -> telemetryEventRepository.existsByWorkspaceIdAndEventId(workspaceId, id))
                            .toList()
                        : List.of();

                validHypotheses.add(new InvestigationResultDto.HypothesisDto(
                        h.rank(),
                        h.cause(),
                        h.confidence(),
                        validRefs,
                        h.contradictingEvidence() != null ? h.contradictingEvidence() : List.of()
                ));
            }
        }

        return new InvestigationResultDto(
                raw.incidentId(),
                raw.mode() != null ? raw.mode() : "DEMO",
                validObservations,
                validHypotheses,
                raw.recommendedInvestigation() != null ? raw.recommendedInvestigation() : List.of(),
                raw.similarIncidents() != null ? raw.similarIncidents() : List.of(),
                false
        );
    }

    public InvestigationResultDto createInsufficientEvidenceFallback(String incidentId) {
        return new InvestigationResultDto(
                incidentId,
                "DEMO",
                List.of(),
                List.of(new InvestigationResultDto.HypothesisDto(
                        1,
                        "Insufficient evidence to determine the root cause.",
                        "LOW",
                        List.of(),
                        List.of()
                )),
                List.of(new InvestigationResultDto.RecommendationDto(
                        "Collect additional application trace summaries and diagnostic logs", "READ_ONLY")),
                List.of(),
                true
        );
    }
}
