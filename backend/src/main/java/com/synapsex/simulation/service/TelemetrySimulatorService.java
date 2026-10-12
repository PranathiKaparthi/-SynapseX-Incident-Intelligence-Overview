package com.synapsex.simulation.service;

import com.synapsex.auth.model.Workspace;
import com.synapsex.auth.repository.WorkspaceRepository;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.ingestion.service.IngestionService;
import com.synapsex.simulation.model.SimulationScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TelemetrySimulatorService {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySimulatorService.class);

    private final IngestionService ingestionService;
    private final WorkspaceRepository workspaceRepository;

    private final Map<String, AtomicBoolean> activeSimulations = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> simulationStats = new ConcurrentHashMap<>();

    public TelemetrySimulatorService(
            IngestionService ingestionService,
            WorkspaceRepository workspaceRepository) {
        this.ingestionService = ingestionService;
        this.workspaceRepository = workspaceRepository;
    }

    public Map<String, Object> startSimulation(
            UUID workspaceId, SimulationScenario scenario, int durationSeconds, long seed) {

        String key = workspaceId.toString();
        AtomicBoolean running = activeSimulations.computeIfAbsent(key, k -> new AtomicBoolean(false));

        if (running.get()) {
            return Map.of(
                    "status", "ALREADY_RUNNING",
                    "scenario", scenario,
                    "workspaceId", workspaceId
            );
        }

        running.set(true);
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("status", "RUNNING");
        stats.put("scenario", scenario);
        stats.put("startedAt", Instant.now().toString());
        stats.put("eventsGenerated", 0);
        stats.put("seed", seed);
        simulationStats.put(key, stats);

        executeSimulationAsync(workspaceId, scenario, durationSeconds, seed, running, stats);

        return stats;
    }

    public Map<String, Object> stopSimulation(UUID workspaceId) {
        String key = workspaceId.toString();
        AtomicBoolean running = activeSimulations.get(key);
        if (running != null) {
            running.set(false);
        }
        Map<String, Object> stats = simulationStats.getOrDefault(key, Map.of("status", "STOPPED"));
        return stats;
    }

    public Map<String, Object> getStatus(UUID workspaceId) {
        return simulationStats.getOrDefault(workspaceId.toString(), Map.of("status", "IDLE"));
    }

    @Async
    protected void executeSimulationAsync(
            UUID workspaceId, SimulationScenario scenario, int durationSeconds, long seed,
            AtomicBoolean running, Map<String, Object> stats) {

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseGet(() -> new Workspace(workspaceId, "Simulation Workspace", "sim-ws"));

        Random random = new Random(seed);
        int generated = 0;
        int intervalMs = 1000;
        int totalTicks = Math.max(1, durationSeconds);

        log.info("Starting simulation scenario {} for workspace {} (seed={})", scenario, workspaceId, seed);

        try {
            for (int tick = 0; tick < totalTicks && running.get(); tick++) {
                List<TelemetryEnvelopeDto> events = generateTickEvents(scenario, tick, random);
                for (TelemetryEnvelopeDto evt : events) {
                    try {
                        ingestionService.ingestSingle(evt, workspace);
                        generated++;
                    } catch (Exception e) {
                        log.debug("Simulation event ingestion error: {}", e.getMessage());
                    }
                }
                stats.put("eventsGenerated", generated);
                Thread.sleep(intervalMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
            stats.put("status", "COMPLETED");
            stats.put("completedAt", Instant.now().toString());
            log.info("Simulation completed: {} events generated for workspace {}", generated, workspaceId);
        }
    }

    public List<TelemetryEnvelopeDto> generateTickEvents(SimulationScenario scenario, int tick, Random random) {
        List<TelemetryEnvelopeDto> list = new ArrayList<>();
        Instant now = Instant.now().minusSeconds(Math.max(0, 60 - tick));

        // Normal base traffic
        list.add(createEnvelope("api-gateway", now, 25 + random.nextDouble() * 10, 45, 0.2, 120 + random.nextDouble() * 30, 20, 10, random));
        list.add(createEnvelope("auth-service", now, 30 + random.nextDouble() * 15, 50, 0.1, 80 + random.nextDouble() * 20, 25, 5, random));
        list.add(createEnvelope("order-service", now, 40 + random.nextDouble() * 20, 60, 0.5, 200 + random.nextDouble() * 50, 35, 20, random));
        list.add(createEnvelope("payment-service", now, 35 + random.nextDouble() * 10, 55, 0.3, 250 + random.nextDouble() * 40, 40, 15, random));
        list.add(createEnvelope("inventory-service", now, 20 + random.nextDouble() * 10, 40, 0.0, 90 + random.nextDouble() * 15, 15, 10, random));
        list.add(createEnvelope("notification-service", now, 15 + random.nextDouble() * 10, 35, 0.0, 60 + random.nextDouble() * 10, 10, 50, random));

        // Apply scenario-specific anomalies
        switch (scenario) {
            case DATABASE_CONNECTION_POOL_EXHAUSTION -> {
                // Payment DB pool reaches 98-100%, causing cascading latency into order-service
                list.add(createExtremeEnvelope(
                        "payment-service", now, 88.0, 85.0, 18.5, 3400.0, 99.0, 500,
                        List.of(new TelemetryEnvelopeDto.LogEntryDto("ERROR", "HikariPool-1 - Connection is not available, request timed out after 30000ms.", "tr-db-" + tick)),
                        random
                ));
                list.add(createExtremeEnvelope(
                        "order-service", now, 75.0, 70.0, 12.0, 3600.0, 70.0, 800,
                        List.of(new TelemetryEnvelopeDto.LogEntryDto("ERROR", "Downstream payment-service timed out: HTTP 504 Gateway Timeout", "tr-order-" + tick)),
                        random
                ));
            }
            case CPU_SPIKE -> {
                list.add(createExtremeEnvelope(
                        "order-service", now, 96.5, 78.0, 8.0, 1800.0, 50.0, 300,
                        List.of(new TelemetryEnvelopeDto.LogEntryDto("WARN", "High thread contention detected on order checkout processor", "tr-cpu-" + tick)),
                        random
                ));
            }
            case MEMORY_EXHAUSTION -> {
                list.add(createExtremeEnvelope(
                        "inventory-service", now, 45.0, 98.2, 5.0, 950.0, 30.0, 100,
                        List.of(new TelemetryEnvelopeDto.LogEntryDto("ERROR", "java.lang.OutOfMemoryError: Java heap space during cache reload", "tr-oom-" + tick)),
                        random
                ));
            }
            case API_LATENCY_INCREASE -> {
                list.add(createExtremeEnvelope(
                        "api-gateway", now, 55.0, 60.0, 2.5, 2800.0, 40.0, 50,
                        List.of(new TelemetryEnvelopeDto.LogEntryDto("WARN", "Gateway downstream routing latency exceeded P95 threshold", "tr-gw-" + tick)),
                        random
                ));
            }
            case KAFKA_CONSUMER_LAG -> {
                list.add(createExtremeEnvelope(
                        "notification-service", now, 40.0, 50.0, 0.5, 300.0, 20.0, 2800,
                        List.of(new TelemetryEnvelopeDto.LogEntryDto("WARN", "Consumer partition lag growing steadily: 2800 uncommitted offsets", "tr-lag-" + tick)),
                        random
                ));
            }
            case PAYMENT_FAILURE_CASCADE -> {
                list.add(createExtremeEnvelope(
                        "payment-service", now, 65.0, 70.0, 32.0, 2200.0, 85.0, 400,
                        List.of(new TelemetryEnvelopeDto.LogEntryDto("ERROR", "Payment provider webhook rejected transaction with 500 Internal Error", "tr-pay-" + tick)),
                        random
                ));
            }
            case NETWORK_PARTITION -> {
                list.add(createExtremeEnvelope(
                        "auth-service", now, 35.0, 45.0, 45.0, 5000.0, 20.0, 100,
                        List.of(new TelemetryEnvelopeDto.LogEntryDto("FATAL", "Connection refused: unable to reach identity verification cluster", "tr-net-" + tick)),
                        random
                ));
            }
            case NORMAL_TRAFFIC -> {
                // Already baseline
            }
        }

        return list;
    }

    private TelemetryEnvelopeDto createEnvelope(
            String service, Instant time, double cpu, double mem, double errorRate, double latency,
            double dbConn, double lag, Random random) {
        String eventId = "evt-sim-" + UUID.randomUUID().toString().substring(0, 8);
        return new TelemetryEnvelopeDto(
                eventId,
                service,
                time,
                "SERVICE_METRIC",
                "production",
                errorRate > 5 ? "WARNING" : "INFO",
                Map.of(
                        "cpuUsage", Math.round(cpu * 10.0) / 10.0,
                        "memoryUsage", Math.round(mem * 10.0) / 10.0,
                        "errorRate", Math.round(errorRate * 10.0) / 10.0,
                        "latencyMs", Math.round(latency * 10.0) / 10.0,
                        "dbConnUtilization", Math.round(dbConn * 10.0) / 10.0,
                        "consumerLag", Math.round(lag * 10.0) / 10.0
                ),
                List.of(),
                Map.of("version", "1.0.0", "host", service + "-node-1"),
                true
        );
    }

    private TelemetryEnvelopeDto createExtremeEnvelope(
            String service, Instant time, double cpu, double mem, double errorRate, double latency,
            double dbConn, double lag, List<TelemetryEnvelopeDto.LogEntryDto> logs, Random random) {
        String eventId = "evt-sim-" + UUID.randomUUID().toString().substring(0, 8);
        return new TelemetryEnvelopeDto(
                eventId,
                service,
                time,
                "SERVICE_METRIC",
                "production",
                "CRITICAL",
                Map.of(
                        "cpuUsage", cpu,
                        "memoryUsage", mem,
                        "errorRate", errorRate,
                        "latencyMs", latency,
                        "dbConnUtilization", dbConn,
                        "consumerLag", lag
                ),
                logs,
                Map.of("version", "1.0.0", "host", service + "-node-primary"),
                true
        );
    }
}
