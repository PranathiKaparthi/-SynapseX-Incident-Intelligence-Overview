package com.synapsex.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.processing.service.TelemetryPipelineService;
import com.synapsex.reliability.service.AvailabilityTrackerService;
import com.synapsex.reliability.service.DeadLetterQueueService;
import com.synapsex.reliability.service.ResiliencePolicyService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReliabilityAndRecoveryTest {

    private AvailabilityTrackerService availabilityTracker;
    private ResiliencePolicyService resiliencePolicyService;
    private DeadLetterQueueService dlqService;
    private ObjectMapper objectMapper;

    @Mock
    private TelemetryPipelineService pipelineService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        availabilityTracker = new AvailabilityTrackerService(new SimpleMeterRegistry());
        resiliencePolicyService = new ResiliencePolicyService();
        dlqService = new DeadLetterQueueService(objectMapper, pipelineService);
    }

    @Test
    @DisplayName("Availability Measurement: Sustains >= 99.9% availability and calculates MTTR")
    void testAvailabilityMeasurementAndMttr() throws InterruptedException {
        // Simulate 9,995 successful requests and 5 transient failed requests (99.95% availability)
        for (int i = 0; i < 9995; i++) {
            availabilityTracker.recordSuccess();
        }

        // Trigger transient outage
        availabilityTracker.startOutage("Kafka connection timeout");
        Thread.sleep(50); // 50ms outage duration
        for (int i = 0; i < 5; i++) {
            availabilityTracker.recordFailure("Kafka timeout");
        }
        availabilityTracker.resolveOutage();

        double avail = availabilityTracker.getAvailabilityPercent();
        assertTrue(avail >= 99.9, "Measured availability (" + avail + "%) must meet 99.9% design target");

        double mttr = availabilityTracker.getMeanTimeToRecoveryMs();
        assertTrue(mttr >= 40.0, "MTTR (" + mttr + " ms) should accurately record outage duration");

        Health health = availabilityTracker.health();
        assertEquals(Status.UP, health.getStatus(), "Actuator health indicator should report UP after recovery");
        assertEquals(avail, health.getDetails().get("availabilityPercent"));
    }

    @Test
    @DisplayName("Circuit Breaker: Trips to OPEN on failures and automatically recovers after reset timeout")
    void testCircuitBreakerLifecycleAndRecovery() throws Exception {
        // Threshold: 3 consecutive failures, reset timeout: 100ms
        ResiliencePolicyService.CircuitBreaker cb =
                resiliencePolicyService.createCircuitBreaker("ai-provider", 3, 100);

        assertEquals(ResiliencePolicyService.CircuitState.CLOSED, cb.getState());

        // Trip the circuit with 3 consecutive failures
        for (int i = 0; i < 3; i++) {
            String res = cb.execute(
                    () -> { throw new RuntimeException("External 503 Provider Unavailable"); },
                    () -> "FALLBACK_DEMO_RESULT"
            );
            assertEquals("FALLBACK_DEMO_RESULT", res);
        }

        // Circuit must now be OPEN
        assertEquals(ResiliencePolicyService.CircuitState.OPEN, cb.getState());

        // While OPEN, operation is short-circuited directly to fallback
        String shortCircuited = cb.execute(
                () -> "SHOULD_NOT_EXECUTE",
                () -> "FALLBACK_CACHED"
        );
        assertEquals("FALLBACK_CACHED", shortCircuited);

        // Wait for reset timeout to elapse
        Thread.sleep(120);

        // Circuit transitions to HALF_OPEN to probe downstream recovery
        assertEquals(ResiliencePolicyService.CircuitState.HALF_OPEN, cb.getState());

        // Probe succeeds
        String recovered = cb.execute(
                () -> "SUCCESSFUL_PROBE_RESULT",
                () -> "FALLBACK"
        );
        assertEquals("SUCCESSFUL_PROBE_RESULT", recovered);

        // Circuit is fully restored to CLOSED
        assertEquals(ResiliencePolicyService.CircuitState.CLOSED, cb.getState());
    }

    @Test
    @DisplayName("Exponential Backoff with Jitter: Retries recover from transient failures")
    void testExponentialBackoffWithJitter() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = resiliencePolicyService.executeWithExponentialBackoff(
                () -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new RuntimeException("Transient socket timeout");
                    }
                    return "TRANSIENT_RECOVERY_SUCCESS";
                },
                5,   // max attempts
                10,  // initial delay 10ms
                50   // max delay 50ms
        );

        assertEquals("TRANSIENT_RECOVERY_SUCCESS", result);
        assertEquals(3, attempts.get(), "Should succeed on 3rd attempt");
    }

    @Test
    @DisplayName("Dead Letter Queue: Routes malformed event and verifies safe replay upon resolution")
    void testDeadLetterQueueRoutingAndReplay() {
        UUID wsId = UUID.randomUUID();
        String payload = """
                {
                  "eventId": "evt-dlq-001",
                  "serviceName": "payment-service",
                  "timestamp": "2026-10-13T12:00:00Z",
                  "eventType": "METRIC",
                  "severity": "CRITICAL",
                  "metrics": {"latencyMs": 3500.0}
                }
                """;

        // Route to DLQ
        DeadLetterQueueService.DeadLetterEntry entry = dlqService.routeToDeadLetter(
                wsId, "payment-service", payload, "Schema validation failure", new IllegalArgumentException("Corrupted envelope")
        );

        assertNotNull(entry);
        assertEquals("PENDING", entry.status());
        assertEquals(1, dlqService.getQueueDepth());

        // Mock pipeline returning successful replay
        when(pipelineService.processSingle(any(TelemetryEnvelopeDto.class), eq(wsId)))
                .thenReturn(new TelemetryPipelineService.ProcessingResult(1, 1, 0, 1, 1, 500_000));

        // Replay entry
        boolean replayed = dlqService.replayEntry(entry.id());
        assertTrue(replayed, "Replaying valid dead letter should succeed");
        assertEquals(0, dlqService.getQueueDepth(), "Pending queue depth should be 0 after replay");
    }
}
