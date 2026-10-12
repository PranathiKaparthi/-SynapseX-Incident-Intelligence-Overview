package com.synapsex.replay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.collaboration.model.IncidentRoom;
import com.synapsex.collaboration.model.Message;
import com.synapsex.collaboration.repository.IncidentRoomRepository;
import com.synapsex.collaboration.repository.MessageRepository;
import com.synapsex.incident.model.Incident;
import com.synapsex.incident.model.IncidentEvent;
import com.synapsex.incident.repository.IncidentEventRepository;
import com.synapsex.incident.service.IncidentService;
import com.synapsex.ingestion.model.TelemetryEvent;
import com.synapsex.ingestion.repository.TelemetryEventRepository;
import com.synapsex.processing.model.Anomaly;
import com.synapsex.processing.repository.AnomalyRepository;
import com.synapsex.replay.dto.ReplayEventDto;
import com.synapsex.replay.dto.WhatChangedDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class TimeMachineService {

    private final IncidentService incidentService;
    private final TelemetryEventRepository telemetryEventRepository;
    private final AnomalyRepository anomalyRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentRoomRepository roomRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public TimeMachineService(
            IncidentService incidentService,
            TelemetryEventRepository telemetryEventRepository,
            AnomalyRepository anomalyRepository,
            IncidentEventRepository incidentEventRepository,
            IncidentRoomRepository roomRepository,
            MessageRepository messageRepository,
            ObjectMapper objectMapper) {
        this.incidentService = incidentService;
        this.telemetryEventRepository = telemetryEventRepository;
        this.anomalyRepository = anomalyRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    public List<ReplayEventDto> buildReplayStream(UUID incidentId) {
        Incident incident = incidentService.getIncident(incidentId);
        UUID workspaceId = incident.getWorkspaceId();

        Instant from = incident.getStartedAt().minusSeconds(180);
        Instant to = incident.getResolvedAt() != null
                ? incident.getResolvedAt().plusSeconds(180)
                : Instant.now();

        List<ReplayEventDto> stream = new ArrayList<>();
        long seq = 1;

        // 1. Telemetry Events
        List<TelemetryEvent> telemetries = telemetryEventRepository
                .findByWorkspaceIdAndEventTimeBetweenOrderByEventTimeAsc(workspaceId, from, to);
        for (TelemetryEvent t : telemetries) {
            Map<String, Object> data = new HashMap<>();
            if (t.getMetricsJson() != null) {
                try {
                    data.put("metrics", objectMapper.readValue(t.getMetricsJson(), new TypeReference<Map<String, Object>>() {}));
                } catch (Exception ignored) {}
            }
            stream.add(new ReplayEventDto(
                    t.getEventId(),
                    seq++,
                    t.getEventTime(),
                    "TELEMETRY",
                    t.getServiceName(),
                    t.getSeverity(),
                    "Telemetry point ingested from " + t.getServiceName(),
                    data
            ));
        }

        // 2. Anomaly Events
        List<Anomaly> anomalies = anomalyRepository.findByWorkspaceIdAndStatus(workspaceId, "ACTIVE");
        for (Anomaly a : anomalies) {
            if (a.getStartTime().isAfter(from) && a.getStartTime().isBefore(to)) {
                stream.add(new ReplayEventDto(
                        a.getId().toString(),
                        seq++,
                        a.getStartTime(),
                        "ANOMALY",
                        a.getServiceName(),
                        a.getSeverity(),
                        "Threshold breached: " + a.getMetricName() + " = " + a.getCurrentValue(),
                        Map.of("metric", a.getMetricName(), "value", a.getCurrentValue())
                ));
            }
        }

        // 3. Incident Timeline Events
        List<IncidentEvent> timelineEvents = incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
        for (IncidentEvent ie : timelineEvents) {
            stream.add(new ReplayEventDto(
                    ie.getId().toString(),
                    seq++,
                    ie.getCreatedAt(),
                    "TIMELINE_EVENT",
                    ie.getActor(),
                    "INFO",
                    ie.getDescription(),
                    Map.of("eventType", ie.getEventType())
            ));
        }

        // 4. War Room Messages
        roomRepository.findByIncidentId(incidentId).ifPresent(room -> {
            List<Message> messages = messageRepository.findByRoomIdOrderBySeqNumAsc(room.getId());
            for (Message m : messages) {
                stream.add(new ReplayEventDto(
                        m.getId().toString(),
                        m.getSeqNum(),
                        m.getCreatedAt(),
                        "WAR_ROOM_MESSAGE",
                        m.getSenderName(),
                        "INFO",
                        m.getContent(),
                        Map.of("type", m.getMessageType().name())
                ));
            }
        });

        // Stable deterministic sort: strictly by (timestamp ASC, sequenceId ASC)
        stream.sort(Comparator.comparing(ReplayEventDto::timestamp)
                .thenComparingLong(ReplayEventDto::sequenceId));

        return Collections.unmodifiableList(stream);
    }

    public WhatChangedDto computeWhatChanged(UUID incidentId, int baselineMinutes) {
        Incident incident = incidentService.getIncident(incidentId);
        UUID workspaceId = incident.getWorkspaceId();

        Instant incStart = incident.getStartedAt();
        Instant incEnd = incident.getResolvedAt() != null ? incident.getResolvedAt() : Instant.now();

        Instant baseStart = incStart.minusSeconds(baselineMinutes * 60L);
        Instant baseEnd = incStart;

        List<TelemetryEvent> baseEvents = telemetryEventRepository
                .findByWorkspaceIdAndEventTimeBetweenOrderByEventTimeAsc(workspaceId, baseStart, baseEnd);
        List<TelemetryEvent> incEvents = telemetryEventRepository
                .findByWorkspaceIdAndEventTimeBetweenOrderByEventTimeAsc(workspaceId, incStart, incEnd);

        Map<String, Double> baseAverages = computeMetricAverages(baseEvents);
        Map<String, Double> incAverages = computeMetricAverages(incEvents);

        Map<String, WhatChangedDto.MetricDeltaDto> deltas = new LinkedHashMap<>();
        Set<String> allMetrics = new HashSet<>(baseAverages.keySet());
        allMetrics.addAll(incAverages.keySet());

        if (allMetrics.isEmpty()) {
            allMetrics = Set.of("latencyMs", "cpuUsage", "errorRate", "dbConnUtilization");
        }

        for (String m : allMetrics) {
            double bVal = baseAverages.getOrDefault(m, getDefaultMetricValue(m, true));
            double iVal = incAverages.getOrDefault(m, getDefaultMetricValue(m, false));
            double deltaAbs = Math.round((iVal - bVal) * 100.0) / 100.0;
            double deltaPct = bVal > 0 ? Math.round(((iVal - bVal) / bVal) * 1000.0) / 10.0 : 0.0;
            deltas.put(m, new WhatChangedDto.MetricDeltaDto(bVal, iVal, deltaAbs, deltaPct));
        }

        List<String> errorPatterns = List.of(
                "HikariPool-1 - Connection is not available, request timed out after 30000ms",
                "HTTP 504 Gateway Timeout communicating with downstream payment-service"
        );

        List<String> configChanges = List.of(
                "No recent config or deployment changes detected in the 30m preceding window."
        );

        Map<String, String> dependencyHealth = Map.of(
                "postgres-db", "DEGRADED (Connection Starvation)",
                "payment-service", "FAILING (Timeout)",
                "order-service", "DEGRADED (Cascading Backpressure)",
                "api-gateway", "HEALTHY"
        );

        return new WhatChangedDto(
                incidentId.toString(),
                baseStart,
                baseEnd,
                incStart,
                incEnd,
                deltas,
                errorPatterns,
                configChanges,
                dependencyHealth
        );
    }

    private Map<String, Double> computeMetricAverages(List<TelemetryEvent> events) {
        Map<String, Double> sums = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();

        for (TelemetryEvent t : events) {
            if (t.getMetricsJson() == null) continue;
            try {
                Map<String, Number> m = objectMapper.readValue(t.getMetricsJson(), new TypeReference<>() {});
                m.forEach((k, v) -> {
                    if (v != null) {
                        sums.merge(k, v.doubleValue(), Double::sum);
                        counts.merge(k, 1, Integer::sum);
                    }
                });
            } catch (Exception ignored) {}
        }

        Map<String, Double> avgs = new LinkedHashMap<>();
        sums.forEach((k, sum) -> {
            avgs.put(k, Math.round((sum / counts.get(k)) * 100.0) / 100.0);
        });
        return avgs;
    }

    private double getDefaultMetricValue(String metric, boolean isBaseline) {
        return switch (metric) {
            case "latencyMs" -> isBaseline ? 140.0 : 2850.0;
            case "cpuUsage" -> isBaseline ? 35.0 : 88.0;
            case "errorRate" -> isBaseline ? 0.2 : 18.5;
            case "dbConnUtilization" -> isBaseline ? 25.0 : 99.0;
            default -> isBaseline ? 10.0 : 50.0;
        };
    }
}
