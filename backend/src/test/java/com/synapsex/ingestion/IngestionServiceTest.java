package com.synapsex.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.auth.model.Workspace;
import com.synapsex.auth.security.JwtService;
import com.synapsex.ingestion.dto.BatchIngestionResponseDto;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.ingestion.model.IngestionApiKey;
import com.synapsex.ingestion.model.ProcessedEvent;
import com.synapsex.ingestion.model.TelemetryEvent;
import com.synapsex.ingestion.repository.IngestionApiKeyRepository;
import com.synapsex.ingestion.repository.ProcessedEventRepository;
import com.synapsex.ingestion.repository.TelemetryEventRepository;
import com.synapsex.ingestion.service.IngestionService;
import com.synapsex.ingestion.service.RateLimiterService;
import com.synapsex.processing.consumer.TelemetryProcessingConsumer;
import com.synapsex.simulation.model.SimulationScenario;
import com.synapsex.simulation.service.TelemetrySimulatorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock private IngestionApiKeyRepository apiKeyRepository;
    @Mock private TelemetryEventRepository telemetryEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private JwtService jwtService;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private Acknowledgment acknowledgment;

    private IngestionService ingestionService;
    private RateLimiterService rateLimiterService;
    private ObjectMapper objectMapper;
    private TelemetryProcessingConsumer consumer;
    private TelemetrySimulatorService simulatorService;

    private Workspace sampleWorkspace;
    private IngestionApiKey sampleApiKey;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        rateLimiterService = new RateLimiterService(5, 5); // 5 req/sec for testing rate limit

        ingestionService = new IngestionService(
                apiKeyRepository,
                telemetryEventRepository,
                rateLimiterService,
                jwtService,
                objectMapper,
                kafkaTemplate,
                new SimpleMeterRegistry()
        );

        consumer = new TelemetryProcessingConsumer(
                processedEventRepository,
                telemetryEventRepository,
                objectMapper,
                kafkaTemplate
        );

        simulatorService = new TelemetrySimulatorService(ingestionService, null);

        sampleWorkspace = new Workspace(UUID.randomUUID(), "Prod Workspace", "prod-ws");
        sampleApiKey = new IngestionApiKey(sampleWorkspace, "Test Key", "syn_live_", "hash123");
    }

    @Test
    @DisplayName("API Key Authentication: valid key resolves workspace; invalid key throws SecurityException")
    void testApiKeyAuthentication() {
        when(jwtService.hashToken("syn_live_valid")).thenReturn("hash123");
        when(apiKeyRepository.findByKeyHash("hash123")).thenReturn(Optional.of(sampleApiKey));

        Workspace resolved = ingestionService.authenticateApiKey("syn_live_valid");
        assertNotNull(resolved);
        assertEquals(sampleWorkspace.getId(), resolved.getId());

        when(jwtService.hashToken("syn_live_invalid")).thenReturn("wronghash");
        when(apiKeyRepository.findByKeyHash("wronghash")).thenReturn(Optional.empty());

        assertThrows(SecurityException.class, () -> ingestionService.authenticateApiKey("syn_live_invalid"));
    }

    @Test
    @DisplayName("Batch Ingestion: enforces max limit and returns per-event results")
    void testBatchIngestion() {
        List<TelemetryEnvelopeDto> batch = List.of(
                new TelemetryEnvelopeDto("evt-1", "payment-service", Instant.now(), "SERVICE_METRIC", "production", "INFO", Map.of("cpu", 50.0), List.of(), Map.of(), false),
                new TelemetryEnvelopeDto("", "order-service", Instant.now(), "SERVICE_METRIC", "production", "INFO", Map.of(), List.of(), Map.of(), false) // Invalid: missing eventId
        );

        BatchIngestionResponseDto response = ingestionService.ingestBatch(batch, sampleWorkspace);

        assertEquals(2, response.total());
        assertEquals(1, response.accepted());
        assertEquals(1, response.rejected());
        assertEquals("ACCEPTED", response.results().get(0).status());
        assertEquals("REJECTED", response.results().get(1).status());
    }

    @Test
    @DisplayName("Rate Limiting: bursts exceeding capacity get rejected")
    void testRateLimiterExceeded() {
        RateLimiterService tightLimiter = new RateLimiterService(1, 2);
        IngestionService tightService = new IngestionService(
                apiKeyRepository,
                telemetryEventRepository,
                tightLimiter,
                jwtService,
                objectMapper,
                kafkaTemplate,
                new SimpleMeterRegistry()
        );
        tightService.setMaxBatchSize(500);

        TelemetryEnvelopeDto dto = new TelemetryEnvelopeDto(
                "evt-rate", "order-service", Instant.now(), "SERVICE_METRIC", "prod", "INFO", Map.of(), List.of(), Map.of(), false);

        // Exhaust capacity of 2
        assertDoesNotThrow(() -> tightService.ingestSingle(dto, sampleWorkspace));
        assertDoesNotThrow(() -> tightService.ingestSingle(dto, sampleWorkspace));

        // 3rd call immediately exceeds capacity
        assertThrows(IllegalArgumentException.class, () -> tightService.ingestSingle(dto, sampleWorkspace));
    }

    @Test
    @DisplayName("Consumer Deduplication: redelivering the same eventId must be idempotent")
    void testConsumerIdempotency() throws Exception {
        UUID wsId = UUID.randomUUID();
        String eventId = "evt-duplicate-100";
        TelemetryEnvelopeDto envelope = new TelemetryEnvelopeDto(
                eventId, "auth-service", Instant.now(), "SERVICE_METRIC", "prod", "INFO", Map.of("cpu", 20.0), List.of(), Map.of(), false);
        String json = objectMapper.writeValueAsString(envelope);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("synapsex.telemetry.raw", 0, 0, wsId + ":auth-service", json);

        // First delivery: not yet processed
        when(processedEventRepository.existsByWorkspaceIdAndEventId(wsId, eventId)).thenReturn(false);

        consumer.consume(record, acknowledgment);

        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
        verify(telemetryEventRepository, times(1)).save(any(TelemetryEvent.class));
        verify(acknowledgment, times(1)).acknowledge();

        // Second delivery: duplicate event!
        when(processedEventRepository.existsByWorkspaceIdAndEventId(wsId, eventId)).thenReturn(true);

        consumer.consume(record, acknowledgment);

        // Deduplication stops second save
        verify(telemetryEventRepository, times(1)).save(any(TelemetryEvent.class));
        verify(acknowledgment, times(2)).acknowledge();
    }

    @Test
    @DisplayName("Simulator Determinism: identical seed produces identical event metrics")
    void testSimulatorDeterminism() {
        List<TelemetryEnvelopeDto> runA = simulatorService.generateTickEvents(SimulationScenario.CPU_SPIKE, 1, new Random(42L));
        List<TelemetryEnvelopeDto> runB = simulatorService.generateTickEvents(SimulationScenario.CPU_SPIKE, 1, new Random(42L));

        assertEquals(runA.size(), runB.size());
        for (int i = 0; i < runA.size(); i++) {
            assertEquals(runA.get(i).serviceName(), runB.get(i).serviceName());
            assertEquals(runA.get(i).metrics(), runB.get(i).metrics());
        }
    }
}
