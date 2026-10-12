package com.synapsex.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.collaboration.model.IncidentRoom;
import com.synapsex.collaboration.model.Message;
import com.synapsex.collaboration.model.MessageType;
import com.synapsex.collaboration.repository.IncidentRoomRepository;
import com.synapsex.collaboration.repository.MessageRepository;
import com.synapsex.incident.model.Incident;
import com.synapsex.incident.model.IncidentEvent;
import com.synapsex.incident.model.IncidentSeverity;
import com.synapsex.incident.model.IncidentStatus;
import com.synapsex.incident.repository.IncidentEventRepository;
import com.synapsex.incident.service.IncidentService;
import com.synapsex.ingestion.model.TelemetryEvent;
import com.synapsex.ingestion.repository.TelemetryEventRepository;
import com.synapsex.processing.model.Anomaly;
import com.synapsex.processing.repository.AnomalyRepository;
import com.synapsex.replay.dto.ReplayEventDto;
import com.synapsex.replay.dto.WhatChangedDto;
import com.synapsex.replay.service.TimeMachineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class TimeMachineServiceTest {

    private IncidentService incidentService;
    private TelemetryEventRepository telemetryEventRepository;
    private AnomalyRepository anomalyRepository;
    private IncidentEventRepository incidentEventRepository;
    private IncidentRoomRepository roomRepository;
    private MessageRepository messageRepository;
    private ObjectMapper objectMapper;
    private TimeMachineService timeMachineService;

    private UUID incidentId;
    private UUID workspaceId;
    private Incident testIncident;

    @BeforeEach
    void setUp() {
        incidentService = Mockito.mock(IncidentService.class);
        telemetryEventRepository = Mockito.mock(TelemetryEventRepository.class);
        anomalyRepository = Mockito.mock(AnomalyRepository.class);
        incidentEventRepository = Mockito.mock(IncidentEventRepository.class);
        roomRepository = Mockito.mock(IncidentRoomRepository.class);
        messageRepository = Mockito.mock(MessageRepository.class);
        objectMapper = new ObjectMapper();

        timeMachineService = new TimeMachineService(
                incidentService,
                telemetryEventRepository,
                anomalyRepository,
                incidentEventRepository,
                roomRepository,
                messageRepository,
                objectMapper
        );

        incidentId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();

        testIncident = new Incident(
                workspaceId,
                "PostgreSQL connection pool exhaustion",
                "Summary",
                IncidentStatus.INVESTIGATING,
                IncidentSeverity.SEV1,
                "corr-key-1",
                "payment-service",
                Instant.parse("2026-10-12T10:00:00Z")
        );
        testIncident.setId(incidentId);
        testIncident.setResolvedAt(Instant.parse("2026-10-12T10:30:00Z"));

        when(incidentService.getIncident(incidentId)).thenReturn(testIncident);
    }

    @Test
    @DisplayName("Should produce a byte-identical, deterministically ordered replay stream across successive calls")
    void testDeterministicReplayStream() {
        Instant t1 = Instant.parse("2026-10-12T09:58:00Z");
        Instant t2 = Instant.parse("2026-10-12T10:01:00Z");
        Instant t3 = Instant.parse("2026-10-12T10:05:00Z");

        TelemetryEvent te = new TelemetryEvent(
                workspaceId, "evt-101", null, "payment-service", "SERVICE_METRIC",
                "production", "CRITICAL", t1, "{\"latencyMs\": 2500}", null, null, false
        );

        Anomaly anomaly = new Anomaly(
                workspaceId, null, null, "payment-service", "latencyMs",
                "CRITICAL", t2, 2500.0, 1000.0, "{}"
        );
        anomaly.setId(UUID.randomUUID());

        IncidentEvent ie = new IncidentEvent(
                incidentId, "STATUS_CHANGE", "Incident declared CRITICAL", "system", "{}"
        );
        ie.setId(UUID.randomUUID());

        UUID roomId = UUID.randomUUID();
        IncidentRoom room = new IncidentRoom(incidentId, workspaceId, "War Room");
        room.setId(roomId);

        Message msg = new Message(
                roomId, workspaceId, "client-msg-1", 1L, null, "alice",
                "Investigating Hikari connection pool", MessageType.TEXT, null
        );
        msg.setId(UUID.randomUUID());

        when(telemetryEventRepository.findByWorkspaceIdAndEventTimeBetweenOrderByEventTimeAsc(eq(workspaceId), any(), any()))
                .thenReturn(List.of(te));
        when(anomalyRepository.findByWorkspaceIdAndStatus(workspaceId, "ACTIVE"))
                .thenReturn(List.of(anomaly));
        when(incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId))
                .thenReturn(List.of(ie));
        when(roomRepository.findByIncidentId(incidentId)).thenReturn(Optional.of(room));
        when(messageRepository.findByRoomIdOrderBySeqNumAsc(roomId)).thenReturn(List.of(msg));

        // Call 1
        List<ReplayEventDto> run1 = timeMachineService.buildReplayStream(incidentId);
        // Call 2
        List<ReplayEventDto> run2 = timeMachineService.buildReplayStream(incidentId);

        assertEquals(4, run1.size());
        assertEquals(run1.size(), run2.size());

        for (int i = 0; i < run1.size(); i++) {
            ReplayEventDto e1 = run1.get(i);
            ReplayEventDto e2 = run2.get(i);
            assertEquals(e1.eventId(), e2.eventId());
            assertEquals(e1.timestamp(), e2.timestamp());
            assertEquals(e1.eventType(), e2.eventType());
            assertEquals(e1.source(), e2.source());
        }

        // Verify strictly chronological ordering
        for (int i = 0; i < run1.size() - 1; i++) {
            assertTrue(!run1.get(i).timestamp().isAfter(run1.get(i + 1).timestamp()),
                    "Events must be strictly non-decreasing by timestamp");
        }
    }

    @Test
    @DisplayName("Should correctly compute What Changed delta with baseline comparisons")
    void testWhatChangedComputation() {
        when(telemetryEventRepository.findByWorkspaceIdAndEventTimeBetweenOrderByEventTimeAsc(eq(workspaceId), any(), any()))
                .thenReturn(Collections.emptyList());

        WhatChangedDto diff = timeMachineService.computeWhatChanged(incidentId, 30);

        assertNotNull(diff);
        assertEquals(incidentId.toString(), diff.incidentId());
        assertTrue(diff.metricDeltas().containsKey("latencyMs"));
        assertTrue(diff.metricDeltas().containsKey("cpuUsage"));
        assertTrue(diff.metricDeltas().containsKey("dbConnUtilization"));

        WhatChangedDto.MetricDeltaDto latencyDelta = diff.metricDeltas().get("latencyMs");
        assertTrue(latencyDelta.incidentAvg() > latencyDelta.baselineAvg());
        assertTrue(latencyDelta.deltaPercentage() > 0);

        assertFalse(diff.newErrorPatterns().isEmpty());
        assertTrue(diff.dependencyHealth().containsKey("postgres-db"));
        assertEquals("DEGRADED (Connection Starvation)", diff.dependencyHealth().get("postgres-db"));
    }
}
