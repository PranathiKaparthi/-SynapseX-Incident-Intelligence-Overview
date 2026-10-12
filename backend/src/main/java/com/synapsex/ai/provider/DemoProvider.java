package com.synapsex.ai.provider;

import com.synapsex.ai.dto.InvestigationResultDto;
import com.synapsex.incident.model.Incident;
import com.synapsex.ingestion.model.TelemetryEvent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Component
public class DemoProvider implements LlmProvider, EmbeddingProvider {

    public static final String DEMO_BANNER = "DEMO — not a real AI result (heuristic rule-based diagnosis)";

    @Override
    public String getProviderName() {
        return "DEMO";
    }

    @Override
    public int getDimension() {
        return 384;
    }

    @Override
    public float[] embed(String text) {
        // Deterministic 384-dimensional unit vector computed from text SHA-256 seed
        float[] vector = new float[384];
        if (text == null || text.isBlank()) {
            return vector;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.toLowerCase().getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 384; i++) {
                int b = digest[i % digest.length] & 0xFF;
                vector[i] = (float) ((b - 128) / 128.0);
            }
            // Normalize L2 norm
            double sumSq = 0.0;
            for (float v : vector) sumSq += v * v;
            double norm = Math.sqrt(sumSq);
            if (norm > 0) {
                for (int i = 0; i < 384; i++) vector[i] /= (float) norm;
            }
        } catch (Exception ignored) {}

        return vector;
    }

    @Override
    public InvestigationResultDto investigate(
            Incident incident, List<TelemetryEvent> evidenceEvents, String promptContext) {

        String incId = incident != null && incident.getId() != null ? incident.getId().toString() : "inc-demo";

        if (evidenceEvents == null || evidenceEvents.isEmpty()) {
            return new InvestigationResultDto(
                    incId,
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
                            "Collect additional trace summaries and error logs", "READ_ONLY")),
                    List.of(),
                    true
            );
        }

        List<InvestigationResultDto.ObservationDto> observations = new ArrayList<>();
        List<String> eventIds = evidenceEvents.stream().map(TelemetryEvent::getEventId).toList();

        TelemetryEvent primary = evidenceEvents.get(0);
        String service = incident.getRootCauseServiceName() != null
                ? incident.getRootCauseServiceName()
                : primary.getServiceName();

        observations.add(new InvestigationResultDto.ObservationDto(
                String.format("[%s] Telemetry anomaly detected on service '%s' with severity %s",
                        DEMO_BANNER, service, incident.getSeverity()),
                List.of(primary.getEventId())
        ));

        if (evidenceEvents.size() > 1) {
            TelemetryEvent second = evidenceEvents.get(1);
            observations.add(new InvestigationResultDto.ObservationDto(
                    String.format("[%s] Correlated cascading symptom identified on '%s'",
                            DEMO_BANNER, second.getServiceName()),
                    List.of(second.getEventId())
            ));
        }

        List<InvestigationResultDto.HypothesisDto> hypotheses = List.of(
                new InvestigationResultDto.HypothesisDto(
                        1,
                        "Database connection pool exhaustion or query lock contention on " + service,
                        "HIGH",
                        eventIds.subList(0, Math.min(2, eventIds.size())),
                        List.of()
                ),
                new InvestigationResultDto.HypothesisDto(
                        2,
                        "Downstream network timeout causing upstream thread starvation",
                        "MEDIUM",
                        eventIds.subList(0, 1),
                        List.of()
                )
        );

        List<InvestigationResultDto.RecommendationDto> recommendations = List.of(
                new InvestigationResultDto.RecommendationDto(
                        "Execute read-only inspection of active database connections (pg_stat_activity)",
                        "READ_ONLY"
                ),
                new InvestigationResultDto.RecommendationDto(
                        "Inspect slow query log for transactions exceeding 2000ms threshold",
                        "READ_ONLY"
                ),
                new InvestigationResultDto.RecommendationDto(
                        "Recommend scaling connection pool or restarting service pod (requires human sign-off)",
                        "ADVISORY"
                )
        );

        List<InvestigationResultDto.SimilarIncidentDto> similar = List.of(
                new InvestigationResultDto.SimilarIncidentDto(
                        "INC-101",
                        0.88,
                        "Increased pool capacity and optimized unindexed query on payment_records table."
                )
        );

        return new InvestigationResultDto(
                incId,
                "DEMO",
                observations,
                hypotheses,
                recommendations,
                similar,
                false
        );
    }

    @Override
    public String generateWarRoomAssistantMessage(
            String incidentTopic, String question, List<TelemetryEvent> recentEvents) {
        int eventCount = recentEvents != null ? recentEvents.size() : 0;
        return String.format(
                "[%s] Based on %d real telemetry events in this war room: '%s'. " +
                "Recommended step: verify downstream service health and long-running database queries.",
                DEMO_BANNER, eventCount, question != null ? question : incidentTopic);
    }
}
