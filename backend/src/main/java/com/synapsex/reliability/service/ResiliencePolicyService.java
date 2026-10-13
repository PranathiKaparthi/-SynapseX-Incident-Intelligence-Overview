package com.synapsex.reliability.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enterprise Resilience Policies: Circuit Breaker and Exponential Backoff with Jitter.
 * Prevents cascading cascading failures and enforces graceful degradation.
 */
@Service
public class ResiliencePolicyService {

    private static final Logger log = LoggerFactory.getLogger(ResiliencePolicyService.class);
    private final Random random = new Random();

    public enum CircuitState {
        CLOSED,   // Normal traffic flow
        OPEN,     // Service tripped; short-circuiting requests
        HALF_OPEN // Probing downstream recovery
    }

    public static class CircuitBreaker {
        private final String name;
        private final int failureThreshold;
        private final long resetTimeoutMs;

        private volatile CircuitState state = CircuitState.CLOSED;
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicLong lastTrippedTime = new AtomicLong(0);

        public CircuitBreaker(String name, int failureThreshold, long resetTimeoutMs) {
            this.name = name;
            this.failureThreshold = failureThreshold;
            this.resetTimeoutMs = resetTimeoutMs;
        }

        public <T> T execute(Callable<T> operation, Callable<T> fallback) throws Exception {
            checkStateTransition();

            if (state == CircuitState.OPEN) {
                log.warn("CircuitBreaker [{}] is OPEN. Executing fallback.", name);
                return fallback.call();
            }

            try {
                T result = operation.call();
                onSuccess();
                return result;
            } catch (Exception e) {
                onFailure(e);
                log.warn("CircuitBreaker [{}] caught failure: {}. Executing fallback.", name, e.getMessage());
                return fallback.call();
            }
        }

        private void onSuccess() {
            consecutiveFailures.set(0);
            if (state == CircuitState.HALF_OPEN) {
                state = CircuitState.CLOSED;
                log.info("CircuitBreaker [{}] fully recovered: Transitioned HALF_OPEN -> CLOSED", name);
            }
        }

        private void onFailure(Exception e) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= failureThreshold || state == CircuitState.HALF_OPEN) {
                state = CircuitState.OPEN;
                lastTrippedTime.set(System.currentTimeMillis());
                log.error("CircuitBreaker [{}] TRIPPED OPEN after {} failures. Error: {}", name, failures, e.getMessage());
            }
        }

        private void checkStateTransition() {
            if (state == CircuitState.OPEN) {
                long elapsed = System.currentTimeMillis() - lastTrippedTime.get();
                if (elapsed >= resetTimeoutMs) {
                    state = CircuitState.HALF_OPEN;
                    log.info("CircuitBreaker [{}] reset timeout elapsed ({} ms). Transitioned OPEN -> HALF_OPEN", name, elapsed);
                }
            }
        }

        public CircuitState getState() {
            checkStateTransition();
            return state;
        }

        public void forceReset() {
            state = CircuitState.CLOSED;
            consecutiveFailures.set(0);
        }
    }

    /**
     * Executes an operation with full exponential backoff and randomized decorrelated jitter.
     */
    public <T> T executeWithExponentialBackoff(
            Callable<T> task,
            int maxAttempts,
            long initialDelayMs,
            long maxDelayMs) throws Exception {

        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                return task.call();
            } catch (Exception e) {
                lastException = e;
                if (attempt >= maxAttempts) {
                    break;
                }

                // Full jitter algorithm: sleep = random_between(0, min(maxDelay, base * 2^attempt))
                long maxExponential = Math.min(maxDelayMs, initialDelayMs * (1L << (attempt - 1)));
                long jitteredDelay = (long) (random.nextDouble() * maxExponential);

                log.debug("Task attempt {} failed with: {}. Retrying in {} ms (backoff+jitter)",
                        attempt, e.getMessage(), jitteredDelay);

                try {
                    Thread.sleep(Math.max(1, jitteredDelay));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
            }
        }

        throw new RuntimeException("Operation failed after " + maxAttempts + " attempts: " + lastException.getMessage(), lastException);
    }

    public CircuitBreaker createCircuitBreaker(String name, int failureThreshold, long resetTimeoutMs) {
        return new CircuitBreaker(name, failureThreshold, resetTimeoutMs);
    }
}
