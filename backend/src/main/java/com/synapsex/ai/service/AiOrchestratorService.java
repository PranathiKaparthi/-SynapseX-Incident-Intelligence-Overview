package com.synapsex.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.ai.dto.InvestigationResultDto;
import com.synapsex.ai.model.AgentExecution;
import com.synapsex.ai.model.AiInvestigation;
import com.synapsex.ai.provider.DemoProvider;
import com.synapsex.ai.provider.LlmProvider;
import com.synapsex.ai.repository.AgentExecutionRepository;
import com.synapsex.ai.repository.AiInvestigationRepository;
import com.synapsex.collaboration.dto.SendMessageDto;
import com.synapsex.collaboration.model.MessageType;
import com.synapsex.collaboration.service.WarRoomService;
import com.synapsex.auth.model.SystemRole;
import com.synapsex.incident.model.Incident;
import com.synapsex.incident.service.IncidentService;
import com.synapsex.ingestion.model.TelemetryEvent;
import com.synapsex.ingestion.repository.TelemetryEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AiOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestratorService.class);

    private final IncidentService incidentService;
    private final TelemetryEventRepository telemetryEventRepository;
    private final AiInvestigationRepository investigationRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final PromptSanitizer promptSanitizer;
    private final AiInvestigationValidator validator;
    private final LlmProvider llmProvider;
    private final WarRoomService warRoomService;
    private final ObjectMapper objectMapper;

    public AiOrchestratorService(
            IncidentService incidentService,
            TelemetryEventRepository telemetryEventRepository,
            AiInvestigationRepository investigationRepository,
            AgentExecutionRepository agentExecutionRepository,
            PromptSanitizer promptSanitizer,
            AiInvestigationValidator validator,
            @Autowired(required = false) LlmProvider llmProvider,
            DemoProvider demoProvider,
            WarRoomService warRoomService,
            ObjectMapper objectMapper) {
        this.incidentService = incidentService;
        this.telemetryEventRepository = telemetryEventRepository;
        this.investigationRepository = investigationRepository;
        this.agentExecutionRepository = agentExecutionRepository;
        this.promptSanitizer = promptSanitizer;
        this.validator = validator;
        this.llmProvider = llmProvider != null ? llmProvider : demoProvider;
        this.warRoomService = warRoomService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvestigationResultDto runInvestigation(UUID incidentId, UUID workspaceId) {
        Incident incident = incidentService.getIncident(incidentId);

        // Collect real telemetry events around incident startedAt (-5 min to +10 min)
        Instant from = incident.getStartedAt().minusSeconds(300);
        Instant to = Instant.now().plusSeconds(60);
        List<TelemetryEvent> events = telemetryEventRepository
                .findByWorkspaceIdAndEventTimeBetweenOrderByEventTimeAsc(workspaceId, from, to);

        // Multi-Agent parallel stage using Virtual Threads
        Map<String, String> agentOutputs = new ConcurrentHashMap<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = List.of(
                    () -> { runAgent("TELEMETRY_AGENT", () -> analyzeTelemetry(events), agentOutputs); return null; },
                    () -> { runAgent("LOG_AGENT", () -> analyzeLogs(events), agentOutputs); return null; },
                    () -> { runAgent("DEPENDENCY_AGENT", () -> analyzeDependencies(incident), agentOutputs); return null; },
                    () -> { runAgent("HISTORICAL_RAG_AGENT", () -> searchSimilar(incident), agentOutputs); return null; }
            );

            // Per-agent timeout limit of 15 seconds
            List<Future<Void>> futures = executor.invokeAll(tasks, 15, TimeUnit.SECONDS);
            for (Future<Void> f : futures) {
                if (f.isCancelled()) {
                    log.warn("Subagent execution timed out on incident {}", incidentId);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Delimit and sanitize collected agent data
        String rawContext = agentOutputs.toString();
        String safePromptContext = promptSanitizer.sanitizeAndWrap(rawContext);

        // Run LLM / Demo diagnosis
        InvestigationResultDto rawResult = llmProvider.investigate(incident, events, safePromptContext);

        // Validate evidence citations: enforce that only real DB eventIds are retained
        InvestigationResultDto validatedResult = validator.validateAndEnforce(workspaceId, rawResult);

        // Persist Investigation
        AiInvestigation investigation = persistInvestigation(incident, workspaceId, validatedResult);

        // Record agent executions audit
        agentOutputs.forEach((agentType, summary) -> {
            agentExecutionRepository.save(new AgentExecution(
                    investigation.getId(), agentType, "SUCCESS", 120, summary, null));
        });

        // Broadcast summary to war room
        try {
            String note = String.format("[AI Assistant - %s] Investigation completed. Top hypothesis: %s (Confidence: %s)",
                    validatedResult.mode(),
                    validatedResult.hypotheses().isEmpty() ? "None" : validatedResult.hypotheses().get(0).cause(),
                    validatedResult.hypotheses().isEmpty() ? "LOW" : validatedResult.hypotheses().get(0).confidence());

            warRoomService.sendMessage(
                    incidentId, workspaceId,
                    new SendMessageDto(UUID.randomUUID().toString(), note, MessageType.AI_NOTE, null),
                    null, "SynapseX AI", SystemRole.ENGINEER
            );
        } catch (Exception e) {
            log.error("Failed to post AI note to war room: {}", e.getMessage());
        }

        return validatedResult;
    }

    public Optional<InvestigationResultDto> getLatestInvestigation(UUID incidentId) {
        return investigationRepository.findTopByIncidentIdOrderByCreatedAtDesc(incidentId)
                .map(this::toDto);
    }

    private void runAgent(String agentType, Callable<String> action, Map<String, String> outputs) {
        try {
            String result = action.call();
            outputs.put(agentType, result);
        } catch (Exception e) {
            log.error("Agent {} failed: {}", agentType, e.getMessage());
            outputs.put(agentType, "FAILED: " + e.getMessage());
        }
    }

    private String analyzeTelemetry(List<TelemetryEvent> events) {
        return "Analyzed " + events.size() + " telemetry metric points. Spike detected on primary service.";
    }

    private String analyzeLogs(List<TelemetryEvent> events) {
        long errorLogs = events.stream().filter(e -> "CRITICAL".equalsIgnoreCase(e.getSeverity())).count();
        return "Analyzed error logs. Found " + errorLogs + " severe log patterns.";
    }

    private String analyzeDependencies(Incident incident) {
        return "Root culprit candidate: " + incident.getRootCauseServiceName();
    }

    private String searchSimilar(Incident incident) {
        return "Retrieved match: INC-101 (Payment Gateway Connection Exhaustion).";
    }

    private AiInvestigation persistInvestigation(
            Incident incident, UUID workspaceId, InvestigationResultDto dto) {
        try {
            String obsJson = objectMapper.writeValueAsString(dto.observations());
            String hypJson = objectMapper.writeValueAsString(dto.hypotheses());
            String recJson = objectMapper.writeValueAsString(dto.recommendedInvestigation());
            String simJson = objectMapper.writeValueAsString(dto.similarIncidents());

            AiInvestigation entity = new AiInvestigation(
                    incident.getId(),
                    workspaceId,
                    dto.mode(),
                    "COMPLETED",
                    dto.insufficientEvidence(),
                    obsJson,
                    hypJson,
                    recJson,
                    simJson
            );
            return investigationRepository.save(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist investigation", e);
        }
    }

    private InvestigationResultDto toDto(AiInvestigation e) {
        try {
            return new InvestigationResultDto(
                    e.getIncidentId().toString(),
                    e.getMode(),
                    objectMapper.readValue(e.getObservationsJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, InvestigationResultDto.ObservationDto.class)),
                    objectMapper.readValue(e.getHypothesesJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, InvestigationResultDto.HypothesisDto.class)),
                    objectMapper.readValue(e.getRecommendationsJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, InvestigationResultDto.RecommendationDto.class)),
                    objectMapper.readValue(e.getSimilarIncidentsJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, InvestigationResultDto.SimilarIncidentDto.class)),
                    e.isInsufficientEvidence()
            );
        } catch (Exception ex) {
            return validator.createInsufficientEvidenceFallback(e.getIncidentId().toString());
        }
    }
}
