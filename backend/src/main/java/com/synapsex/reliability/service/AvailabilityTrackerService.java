package com.synapsex.reliability.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enterprise Availability & Reliability Tracker.
 * Measures uptime, transaction success ratios, sliding-window availability %,
 * Mean Time To Recovery (MTTR), and Mean Time Between Failures (MTBF).
 */
@Service
public class AvailabilityTrackerService implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityTrackerService.class);

    private final Instant systemStartTime = Instant.now();
    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final AtomicLong successfulTransactions = new AtomicLong(0);
    private final AtomicLong failedTransactions = new AtomicLong(0);

    private final List<OutageRecord> outageHistory = Collections.synchronizedList(new ArrayList<>());
    private volatile OutageRecord activeOutage = null;

    public record OutageRecord(
            Instant startTime,
            Instant endTime,
            String reason,
            long durationMs
    ) {}

    public AvailabilityTrackerService(MeterRegistry meterRegistry) {
        Gauge.builder("synapsex_system_availability_percent", this, AvailabilityTrackerService::getAvailabilityPercent)
                .description("Current platform availability percentage")
                .register(meterRegistry);

        Gauge.builder("synapsex_system_uptime_seconds", this, AvailabilityTrackerService::getUptimeSeconds)
                .description("Platform uptime in seconds")
                .register(meterRegistry);
    }

    public void recordSuccess() {
        totalTransactions.incrementAndGet();
        successfulTransactions.incrementAndGet();
    }

    public void recordFailure(String reason) {
        totalTransactions.incrementAndGet();
        failedTransactions.incrementAndGet();

        if (activeOutage == null) {
            startOutage(reason);
        }
    }

    public synchronized void startOutage(String reason) {
        if (activeOutage == null) {
            activeOutage = new OutageRecord(Instant.now(), null, reason, 0);
            log.warn("System OUTAGE declared: {}", reason);
        }
    }

    public synchronized void resolveOutage() {
        if (activeOutage != null) {
            Instant end = Instant.now();
            long durMs = Duration.between(activeOutage.startTime(), end).toMillis();
            OutageRecord resolved = new OutageRecord(activeOutage.startTime(), end, activeOutage.reason(), durMs);
            outageHistory.add(resolved);
            activeOutage = null;
            log.info("System OUTAGE resolved. Duration: {} ms", durMs);
        }
    }

    public double getAvailabilityPercent() {
        long total = totalTransactions.get();
        if (total == 0) return 100.0;
        double ratio = (double) successfulTransactions.get() / (double) total;
        return Math.round(ratio * 10000.0) / 100.0;
    }

    public long getUptimeSeconds() {
        return Duration.between(systemStartTime, Instant.now()).getSeconds();
    }

    public double getMeanTimeToRecoveryMs() {
        if (outageHistory.isEmpty()) return 0.0;
        long sum = outageHistory.stream().mapToLong(OutageRecord::durationMs).sum();
        return (double) sum / outageHistory.size();
    }

    public double getMeanTimeBetweenFailuresMs() {
        if (outageHistory.size() <= 1) {
            return Duration.between(systemStartTime, Instant.now()).toMillis();
        }
        long firstStart = outageHistory.get(0).startTime().toEpochMilli();
        long lastStart = outageHistory.get(outageHistory.size() - 1).startTime().toEpochMilli();
        return (double) (lastStart - firstStart) / (outageHistory.size() - 1);
    }

    public long getTotalTransactions() {
        return totalTransactions.get();
    }

    public long getSuccessfulTransactions() {
        return successfulTransactions.get();
    }

    public long getFailedTransactions() {
        return failedTransactions.get();
    }

    public boolean isCurrentlyDegraded() {
        return activeOutage != null;
    }

    @Override
    public Health health() {
        double availability = getAvailabilityPercent();
        boolean degraded = isCurrentlyDegraded();

        Health.Builder builder = degraded ? Health.down() : Health.up();
        return builder
                .withDetail("availabilityPercent", availability)
                .withDetail("uptimeSeconds", getUptimeSeconds())
                .withDetail("totalTransactions", totalTransactions.get())
                .withDetail("successfulTransactions", successfulTransactions.get())
                .withDetail("failedTransactions", failedTransactions.get())
                .withDetail("mttrMs", getMeanTimeToRecoveryMs())
                .withDetail("activeOutage", degraded ? activeOutage.reason() : "NONE")
                .build();
    }
}
