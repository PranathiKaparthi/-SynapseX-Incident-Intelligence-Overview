package com.synapsex.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.auth.model.Workspace;
import com.synapsex.auth.security.JwtService;
import com.synapsex.incident.service.CorrelationEngine;
import com.synapsex.ingestion.repository.IngestionApiKeyRepository;
import com.synapsex.ingestion.repository.ProcessedEventRepository;
import com.synapsex.ingestion.repository.TelemetryEventRepository;
import com.synapsex.ingestion.service.IngestionService;
import com.synapsex.ingestion.service.RateLimiterService;
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
import org.springframework.kafka.core.KafkaTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThroughputBenchmarkTest {

    @Mock private IngestionApiKeyRepository apiKeyRepository;
    @Mock private TelemetryEventRepository telemetryEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private JwtService jwtService;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @Mock private AnomalyRuleRepository ruleRepository;
    @Mock private AnomalyRepository anomalyRepository;
    @Mock private CorrelationEngine correlationEngine;

    private IngestionService ingestionService;
    private RateLimiterService rateLimiterService;
    private TelemetryPipelineService pipelineService;
    private ObjectMapper objectMapper;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // 50,000 req/sec rate limit capacity for load testing
        rateLimiterService = new RateLimiterService(50_000, 100_000);

        ingestionService = new IngestionService(
                apiKeyRepository,
                telemetryEventRepository,
                rateLimiterService,
                jwtService,
                objectMapper,
                kafkaTemplate,
                new SimpleMeterRegistry()
        );
        ingestionService.setMaxBatchSize(1000);

        DetectionEngine detectionEngine = new DetectionEngine(
                ruleRepository,
                anomalyRepository,
                objectMapper,
                kafkaTemplate
        );

        pipelineService = new TelemetryPipelineService(
                processedEventRepository,
                telemetryEventRepository,
                detectionEngine,
                correlationEngine,
                objectMapper,
                new SimpleMeterRegistry()
        );

        workspace = new Workspace(UUID.randomUUID(), "Benchmark Workspace", "bench-ws");

        // Mock repository saves for pipeline
        lenient().when(processedEventRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(telemetryEventRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(ruleRepository.findByWorkspaceIdAndEnabledTrue(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Sustained Throughput Benchmark: Ingestion achieves >= 1,000 events/sec with P95 latency < 1.0s")
    void testSustainedIngestionThroughputAndP95Latency() {
        // Run 10 batches of 200 events = 2,000 events under 4 worker threads
        ThroughputBenchmarkHarness.BenchmarkResult result = ThroughputBenchmarkHarness.runBatchIngestionBenchmark(
                ingestionService,
                workspace,
                200,   // batchSize
                10,    // batches
                4      // concurrency
        );

        System.out.println(result.formatSummary());

        // Verify Target 1: Throughput >= 1,000 events/second
        assertTrue(result.acceptedThroughput() >= 1000.0,
                "Accepted throughput (" + result.acceptedThroughput() + " evt/s) must be >= 1,000 events/sec");

        // Verify Target 2: P95 latency < 1,000 ms (1.0 second)
        assertTrue(result.latencies().p95Ms() < 1000.0,
                "P95 ingestion latency (" + result.latencies().p95Ms() + " ms) must be < 1,000 ms");

        // Verify 0% rejection when within rate limit
        assertEquals(0.0, result.errorRatePercent(), 0.01, "Error rate should be 0% under authorized capacity");
    }

    @Test
    @DisplayName("End-to-End Pipeline Processing: High-throughput micro-batching achieves >= 1,000 events/sec")
    void testEndToEndPipelineThroughput() {
        // Process 2,500 events in batches of 250 across 4 worker threads
        ThroughputBenchmarkHarness.BenchmarkResult result = ThroughputBenchmarkHarness.runPipelineProcessingBenchmark(
                pipelineService,
                workspace,
                2500,  // total events
                250,   // batch size
                4      // concurrency
        );

        System.out.println(result.formatSummary());

        assertTrue(result.processedThroughput() >= 1000.0,
                "Pipeline processed throughput (" + result.processedThroughput() + " evt/s) must be >= 1,000 events/sec");
        assertTrue(result.latencies().p95Ms() < 1000.0,
                "Pipeline P95 latency (" + result.latencies().p95Ms() + " ms) must be < 1,000 ms");
    }

    @Test
    @DisplayName("Saturation Load: Rate limiter shields platform under extreme burst")
    void testRateLimiterUnderSaturationBurst() {
        // Configure strict rate limiter with 500 burst capacity
        RateLimiterService constrainedLimiter = new RateLimiterService(200, 500);
        IngestionService constrainedService = new IngestionService(
                apiKeyRepository,
                telemetryEventRepository,
                constrainedLimiter,
                jwtService,
                objectMapper,
                kafkaTemplate,
                new SimpleMeterRegistry()
        );
        constrainedService.setMaxBatchSize(1000);

        // Attempt to ingest 1,500 events in a burst of 3 batches of 500
        ThroughputBenchmarkHarness.BenchmarkResult result = ThroughputBenchmarkHarness.runBatchIngestionBenchmark(
                constrainedService,
                workspace,
                500,
                3,
                1
        );

        System.out.println(result.formatSummary());

        // Platform should accept up to burst limit and reject excess without crashing
        assertTrue(result.totalAccepted() >= 500, "Should accept initial burst capacity");
        assertTrue(result.totalRejected() > 0, "Should reject requests exceeding burst limit");
        assertTrue(result.latencies().p95Ms() < 500.0, "Rejection latency must remain sub-second");
    }
}
