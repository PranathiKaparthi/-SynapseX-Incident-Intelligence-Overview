package com.synapsex.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.ai.dto.InvestigationResultDto;
import com.synapsex.ai.provider.DemoProvider;
import com.synapsex.ai.repository.AgentExecutionRepository;
import com.synapsex.ai.repository.AiInvestigationRepository;
import com.synapsex.ai.service.AiInvestigationValidator;
import com.synapsex.ai.service.AiOrchestratorService;
import com.synapsex.ai.service.PromptSanitizer;
import com.synapsex.collaboration.model.IncidentRoom;
import com.synapsex.collaboration.repository.IncidentRoomRepository;
import com.synapsex.collaboration.repository.MessageRepository;
import com.synapsex.collaboration.service.WarRoomService;
import com.synapsex.incident.model.*;
import com.synapsex.incident.repository.*;
import com.synapsex.incident.service.CorrelationEngine;
import com.synapsex.incident.service.IncidentService;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.ingestion.model.TelemetryEvent;
import com.synapsex.ingestion.repository.ProcessedEventRepository;
import com.synapsex.ingestion.repository.TelemetryEventRepository;
import com.synapsex.knowledge.dto.CreateKnowledgeDto;
import com.synapsex.knowledge.model.IncidentKnowledge;
import com.synapsex.knowledge.repository.IncidentEmbeddingRepository;
import com.synapsex.knowledge.repository.IncidentKnowledgeRepository;
import com.synapsex.knowledge.service.RagService;
import com.synapsex.processing.model.Anomaly;
import com.synapsex.processing.model.AnomalyRule;
import com.synapsex.processing.repository.AnomalyRepository;
import com.synapsex.processing.repository.AnomalyRuleRepository;
import com.synapsex.processing.service.DetectionEngine;
import com.synapsex.processing.service.TelemetryPipelineService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End-to-End Multi-Stage Pipeline Integration Test.
 * Validates the complete incident lifecycle:
 * Telemetry Ingestion -> Anomaly Detection -> Alert Correlation ->
 * Incident Creation -> War Room Activation -> AI Investigation ->
 * Resolution & RAG Post-Mortem Indexing.
 */
@ExtendWith(MockitoExtension.class)
class EndToEndPipelineIntegrationTest {

    // Ingestion & Processing Mocks
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private TelemetryEventRepository telemetryEventRepository;
    @Mock private AnomalyRuleRepository anomalyRuleRepository;
    @Mock private AnomalyRepository anomalyRepository;

    // Incident & Correlation Mocks
    @Mock private IncidentRepository incidentRepository;
    @Mock private IncidentEventRepository incidentEventRepository;
    @Mock private IncidentAssignmentRepository assignmentRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private ServiceDependencyRepository dependencyRepository;

    // Collaboration Mocks
    @Mock private IncidentRoomRepository roomRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    // AI & Knowledge Mocks
    @Mock private AiInvestigationRepository investigationRepository;
    @Mock private AgentExecutionRepository agentExecutionRepository;
    @Mock private IncidentKnowledgeRepository knowledgeRepository;
    @Mock private IncidentEmbeddingRepository embeddingRepository;

    private ObjectMapper objectMapper;
    private DetectionEngine detectionEngine;
    private CorrelationEngine correlationEngine;
    private TelemetryPipelineService pipelineService;
    private IncidentService incidentService;
    private WarRoomService warRoomService;
    private AiOrchestratorService aiOrchestratorService;
    private RagService ragService;

    private UUID workspaceId;
    private MonitoredService svcDb;
    private MonitoredService svcPayment;
    private AnomalyRule highLatencyRule;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        workspaceId = UUID.randomUUID();

        // 1. Detection Engine
        detectionEngine = new DetectionEngine(
                anomalyRuleRepository,
                anomalyRepository,
                objectMapper,
                null // direct pipeline mode
        );

        // 2. Correlation Engine
        correlationEngine = new CorrelationEngine(
                incidentRepository,
                incidentEventRepository,
                serviceRepository,
                dependencyRepository
        );

        // 3. Pipeline Service
        pipelineService = new TelemetryPipelineService(
                processedEventRepository,
                telemetryEventRepository,
                detectionEngine,
                correlationEngine,
                objectMapper,
                new SimpleMeterRegistry()
        );

        // 4. Incident Service
        incidentService = new IncidentService(
                incidentRepository,
                incidentEventRepository,
                assignmentRepository,
                serviceRepository
        );

        // 5. War Room Service
        warRoomService = new WarRoomService(
                roomRepository,
                messageRepository,
                messagingTemplate
        );

        // 6. AI Orchestrator
        DemoProvider demoProvider = new DemoProvider();
        PromptSanitizer promptSanitizer = new PromptSanitizer();
        AiInvestigationValidator validator = new AiInvestigationValidator(telemetryEventRepository);

        aiOrchestratorService = new AiOrchestratorService(
                incidentService,
                telemetryEventRepository,
                investigationRepository,
                agentExecutionRepository,
                promptSanitizer,
                validator,
                demoProvider,
                demoProvider,
                warRoomService,
                objectMapper
        );

        // 7. RAG Service
        ragService = new RagService(
                knowledgeRepository,
                embeddingRepository,
                demoProvider
        );

        // Domain Setup
        svcDb = new MonitoredService(UUID.randomUUID(), workspaceId, "postgres-db", "prod", "TIER_1", "HEALTHY");
        svcPayment = new MonitoredService(UUID.randomUUID(), workspaceId, "payment-service", "prod", "TIER_1", "HEALTHY");

        highLatencyRule = new AnomalyRule(workspaceId, "High Latency Rule", "latencyMs", "GREATER_THAN", 2000.0, 60, 300, "CRITICAL");
        highLatencyRule.setId(UUID.randomUUID());

        lenient().when(anomalyRuleRepository.findByWorkspaceIdAndEnabledTrue(workspaceId))
                .thenReturn(List.of(highLatencyRule));
        lenient().when(serviceRepository.findByWorkspaceIdAndName(workspaceId, "postgres-db"))
                .thenReturn(Optional.of(svcDb));
        lenient().when(serviceRepository.findByWorkspaceIdAndName(workspaceId, "payment-service"))
                .thenReturn(Optional.of(svcPayment));
        lenient().when(dependencyRepository.findByWorkspaceId(workspaceId))
                .thenReturn(List.of(new ServiceDependency(workspaceId, svcPayment.getId(), svcDb.getId(), "CRITICAL")));
    }

    @Test
    @DisplayName("End-to-End Pipeline: Ingestion -> Anomaly -> Correlation -> Incident -> War Room -> AI -> RAG Resolution")
    void testFullIncidentIntelligenceLifecycle() {
        // Step 1: Telemetry with latency exceeding 2000ms arrives
        Instant now = Instant.now();
        String breachEventId = "evt-breach-9001";
        TelemetryEnvelopeDto breachEvent = new TelemetryEnvelopeDto(
                breachEventId,
                "payment-service",
                now,
                "METRIC",
                "production",
                "CRITICAL",
                Map.of("latencyMs", 3450.0),
                List.of(new TelemetryEnvelopeDto.LogEntryDto("ERROR", "Connection timed out waiting for postgres pool", "trace-101")),
                Map.of("cluster", "prod-us-east"),
                false
        );

        // Mock DB behavior
        when(processedEventRepository.existsByWorkspaceIdAndEventId(workspaceId, breachEventId)).thenReturn(false);
        when(anomalyRepository.findFirstByWorkspaceIdAndServiceNameAndMetricNameAndStatus(
                eq(workspaceId), eq("payment-service"), eq("latencyMs"), eq("ACTIVE")))
                .thenReturn(Optional.empty());

        UUID generatedIncidentId = UUID.randomUUID();
        when(incidentRepository.findByWorkspaceIdAndStatus(workspaceId, IncidentStatus.DETECTED))
                .thenReturn(new ArrayList<>());
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident inc = inv.getArgument(0);
            if (inc.getId() == null) inc.setId(generatedIncidentId);
            return inc;
        });
        when(incidentEventRepository.save(any(IncidentEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Step 2 & 3: Process Telemetry through Pipeline
        TelemetryPipelineService.ProcessingResult result =
                pipelineService.processSingle(breachEvent, workspaceId);

        assertEquals(1, result.processed());
        assertEquals(1, result.anomaliesDetected());
        assertEquals(1, result.incidentsCreatedOrUpdated());

        // Verify Telemetry and deduplication persistence
        verify(telemetryEventRepository, times(1)).saveAll(any());
        verify(anomalyRepository, times(1)).save(any(Anomaly.class));

        // Step 4: Verify Incident Declaration
        verify(incidentRepository, atLeastOnce()).save(argThat(inc ->
                "payment-service".equals(inc.getRootCauseServiceName()) &&
                inc.getSeverity() == IncidentSeverity.SEV2
        ));

        // Step 5: War Room activation
        Incident activeIncident = new Incident(
                workspaceId, "Incident on payment-service", "Latency breach",
                IncidentStatus.DETECTED, IncidentSeverity.SEV2, "corr-1", "payment-service", now
        );
        activeIncident.setId(generatedIncidentId);

        when(incidentRepository.findById(generatedIncidentId)).thenReturn(Optional.of(activeIncident));
        when(roomRepository.findByIncidentId(generatedIncidentId)).thenReturn(Optional.empty());
        when(roomRepository.save(any(IncidentRoom.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any(com.synapsex.collaboration.model.Message.class))).thenAnswer(inv -> {
            var m = (com.synapsex.collaboration.model.Message) inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        IncidentRoom room = warRoomService.getOrCreateRoom(generatedIncidentId, workspaceId, "War Room " + generatedIncidentId);
        assertNotNull(room);
        assertEquals(generatedIncidentId, room.getIncidentId());

        // Step 6: Multi-Agent AI Investigation
        when(telemetryEventRepository.findByWorkspaceIdAndEventTimeBetweenOrderByEventTimeAsc(any(), any(), any()))
                .thenReturn(List.of(
                        new TelemetryEvent(workspaceId, breachEventId, null, "payment-service", "METRIC", "prod", "CRITICAL", now, "{\"latencyMs\": 3450.0}", null, null, false)
                ));
        when(telemetryEventRepository.existsByWorkspaceIdAndEventId(workspaceId, breachEventId)).thenReturn(true);
        when(investigationRepository.save(any())).thenAnswer(inv -> {
            var entity = (com.synapsex.ai.model.AiInvestigation) inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        InvestigationResultDto aiResult = aiOrchestratorService.runInvestigation(generatedIncidentId, workspaceId);
        assertNotNull(aiResult);
        assertFalse(aiResult.insufficientEvidence());
        assertFalse(aiResult.hypotheses().isEmpty());
        // Verify evidence citations match real event ID
        assertEquals(breachEventId, aiResult.observations().get(0).evidenceRefs().get(0));

        // Step 7: Incident Resolution & Post-Mortem Indexing to RAG Memory
        // State transition: DETECTED -> INVESTIGATING -> RESOLVED
        incidentService.updateStatus(generatedIncidentId, IncidentStatus.INVESTIGATING, "Alice Engineer", "Investigating root cause");
        Incident resolvedIncident = incidentService.updateStatus(
                generatedIncidentId, IncidentStatus.RESOLVED, "Alice Engineer", "Service stabilized after pool scaling"
        );
        assertEquals(IncidentStatus.RESOLVED, resolvedIncident.getStatus());

        when(knowledgeRepository.save(any())).thenAnswer(inv -> {
            var k = (IncidentKnowledge) inv.getArgument(0);
            k.setId(UUID.randomUUID());
            return k;
        });

        IncidentKnowledge indexedKnowledge = ragService.indexResolvedIncident(workspaceId, new CreateKnowledgeDto(
                generatedIncidentId,
                "Payment Latency Spike during flash sale",
                "HikariCP connection starvation caused 3.4s latency",
                "Unindexed query held locks",
                "Scale connection pool and add index",
                "[\"" + breachEventId + "\"]"
        ));

        assertNotNull(indexedKnowledge);
        verify(embeddingRepository, times(1)).save(any());
    }
}
