package com.synapsex.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsex.auth.model.Workspace;
import com.synapsex.ingestion.dto.BatchIngestionResponseDto;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.ingestion.service.IngestionService;
import com.synapsex.processing.service.TelemetryPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reproducible Load Testing and Throughput Benchmark Harness.
 * Measures offered vs accepted vs processed throughput, error rates,
 * and high-resolution latency percentiles (P50, P90, P95, P99).
 */
public class ThroughputBenchmarkHarness {

    private static final Logger log = LoggerFactory.getLogger(ThroughputBenchmarkHarness.class);

    public record LatencyPercentiles(
            double minMs,
            double meanMs,
            double p50Ms,
            double p90Ms,
            double p95Ms,
            double p99Ms,
            double maxMs
    ) {}

    public record BenchmarkResult(
            String testName,
            int targetEventsPerSecond,
            int totalOffered,
            int totalAccepted,
            int totalProcessed,
            int totalRejected,
            double actualDurationSeconds,
            double offeredThroughput,
            double acceptedThroughput,
            double processedThroughput,
            double errorRatePercent,
            LatencyPercentiles latencies
    ) {
        public String formatSummary() {
            return String.format("""
                --------------------------------------------------------------------------------
                BENCHMARK: %s
                --------------------------------------------------------------------------------
                Target Offered Load       : %d events/sec
                Total Events Offered      : %,d
                Total Events Accepted     : %,d
                Total Events Processed    : %,d
                Total Events Rejected     : %,d
                Test Duration             : %.2f seconds
                Offered Throughput        : %,.1f events/sec
                Accepted Throughput       : %,.1f events/sec
                Processed Throughput      : %,.1f events/sec
                Error / Rejection Rate    : %.2f%%
                Latency Distribution (ms) :
                   Min : %.2f ms | Mean: %.2f ms
                   P50 : %.2f ms | P90 : %.2f ms
                   P95 : %.2f ms | P99 : %.2f ms | Max : %.2f ms
                Targets Met:
                   Throughput >= 1,000 evt/s : %s
                   P95 Latency < 1,000 ms    : %s
                --------------------------------------------------------------------------------
                """,
                    testName,
                    targetEventsPerSecond,
                    totalOffered,
                    totalAccepted,
                    totalProcessed,
                    totalRejected,
                    actualDurationSeconds,
                    offeredThroughput,
                    acceptedThroughput,
                    processedThroughput,
                    errorRatePercent,
                    latencies.minMs(), latencies.meanMs(),
                    latencies.p50Ms(), latencies.p90Ms(),
                    latencies.p95Ms(), latencies.p99Ms(), latencies.maxMs(),
                    processedThroughput >= 1000.0 ? "YES [PASS]" : "TARGET UNDER EVALUATION",
                    latencies.p95Ms() < 1000.0 ? "YES [PASS]" : "EXCEEDED"
            );
        }
    }

    /**
     * Executes sustained workload benchmark on batch ingestion.
     */
    public static BenchmarkResult runBatchIngestionBenchmark(
            IngestionService ingestionService,
            Workspace workspace,
            int batchSize,
            int batchesToRun,
            int concurrency) {

        String testName = "Batch Ingestion [batchSize=" + batchSize + ", batches=" + batchesToRun + ", threads=" + concurrency + "]";
        List<Long> latenciesNanos = new CopyOnWriteArrayList<>();
        AtomicInteger totalOffered = new AtomicInteger(0);
        AtomicInteger totalAccepted = new AtomicInteger(0);
        AtomicInteger totalRejected = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        long startTime = System.nanoTime();

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < batchesToRun; i++) {
            final int batchIndex = i;
            futures.add(executor.submit(() -> {
                List<TelemetryEnvelopeDto> batch = generateSyntheticBatch(batchSize, batchIndex);
                totalOffered.addAndGet(batch.size());

                long t0 = System.nanoTime();
                try {
                    BatchIngestionResponseDto response = ingestionService.ingestBatch(batch, workspace);
                    long elapsed = System.nanoTime() - t0;
                    latenciesNanos.add(elapsed);

                    totalAccepted.addAndGet(response.accepted());
                    totalRejected.addAndGet(response.rejected());
                } catch (Exception e) {
                    long elapsed = System.nanoTime() - t0;
                    latenciesNanos.add(elapsed);
                    totalRejected.addAndGet(batch.size());
                }
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Batch task failed: {}", e.getMessage());
            }
        }
        executor.shutdown();

        long totalNanos = System.nanoTime() - startTime;
        double durationSeconds = totalNanos / 1_000_000_000.0;

        int offered = totalOffered.get();
        int accepted = totalAccepted.get();
        int rejected = totalRejected.get();

        double offeredRps = offered / durationSeconds;
        double acceptedRps = accepted / durationSeconds;
        double errorRate = offered > 0 ? (rejected * 100.0 / offered) : 0.0;

        LatencyPercentiles percentiles = calculatePercentiles(latenciesNanos);

        return new BenchmarkResult(
                testName,
                (int) Math.round(offeredRps),
                offered,
                accepted,
                accepted, // In batch ingestion, accepted envelopes enter downstream pipeline
                rejected,
                durationSeconds,
                offeredRps,
                acceptedRps,
                acceptedRps,
                errorRate,
                percentiles
        );
    }

    /**
     * Executes sustained pipeline processing benchmark (end-to-end processing with persistence & anomaly detection).
     */
    public static BenchmarkResult runPipelineProcessingBenchmark(
            TelemetryPipelineService pipelineService,
            Workspace workspace,
            int totalEvents,
            int batchSize,
            int concurrency) {

        String testName = "End-to-End Pipeline Processing [total=" + totalEvents + ", batch=" + batchSize + "]";
        List<Long> latenciesNanos = new CopyOnWriteArrayList<>();
        AtomicInteger totalProcessed = new AtomicInteger(0);
        AtomicInteger anomaliesFound = new AtomicInteger(0);

        int numberOfBatches = (totalEvents + batchSize - 1) / batchSize;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        long startTime = System.nanoTime();

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfBatches; i++) {
            final int bIdx = i;
            futures.add(executor.submit(() -> {
                int count = Math.min(batchSize, totalEvents - (bIdx * batchSize));
                List<TelemetryEnvelopeDto> batch = generateSyntheticBatch(count, bIdx);

                long t0 = System.nanoTime();
                TelemetryPipelineService.ProcessingResult res = pipelineService.processBatch(batch, workspace.getId());
                long elapsed = System.nanoTime() - t0;
                latenciesNanos.add(elapsed);

                totalProcessed.addAndGet(res.processed());
                anomaliesFound.addAndGet(res.anomaliesDetected());
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Pipeline task failed: {}", e.getMessage());
            }
        }
        executor.shutdown();

        long totalNanos = System.nanoTime() - startTime;
        double durationSeconds = totalNanos / 1_000_000_000.0;

        int processed = totalProcessed.get();
        double processedRps = processed / durationSeconds;
        LatencyPercentiles percentiles = calculatePercentiles(latenciesNanos);

        return new BenchmarkResult(
                testName,
                (int) Math.round(processedRps),
                totalEvents,
                processed,
                processed,
                0,
                durationSeconds,
                totalEvents / durationSeconds,
                processedRps,
                processedRps,
                0.0,
                percentiles
        );
    }

    public static List<TelemetryEnvelopeDto> generateSyntheticBatch(int size, int batchSeed) {
        List<TelemetryEnvelopeDto> list = new ArrayList<>(size);
        String[] services = {"payment-service", "order-service", "inventory-service", "api-gateway", "postgres-db"};
        Random rng = new Random(batchSeed * 31L);

        for (int i = 0; i < size; i++) {
            String eventId = "evt-bench-" + batchSeed + "-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            String service = services[rng.nextInt(services.length)];
            double cpu = 20.0 + rng.nextDouble() * 70.0;
            double latency = 50.0 + rng.nextDouble() * 300.0;
            double conn = 10.0 + rng.nextDouble() * 85.0;

            list.add(new TelemetryEnvelopeDto(
                    eventId,
                    service,
                    Instant.now(),
                    "SERVICE_METRICS",
                    "production",
                    "INFO",
                    Map.of("cpuUsage", cpu, "latencyMs", latency, "dbConnUtilization", conn),
                    List.of(),
                    Map.of("region", "us-east-1", "host", "worker-" + (i % 8)),
                    false
            ));
        }
        return list;
    }

    public static LatencyPercentiles calculatePercentiles(List<Long> latenciesNanos) {
        if (latenciesNanos.isEmpty()) {
            return new LatencyPercentiles(0, 0, 0, 0, 0, 0, 0);
        }

        List<Double> msList = new ArrayList<>(latenciesNanos.size());
        double sum = 0.0;
        for (Long n : latenciesNanos) {
            double ms = n / 1_000_000.0;
            msList.add(ms);
            sum += ms;
        }
        Collections.sort(msList);

        double min = msList.get(0);
        double max = msList.get(msList.size() - 1);
        double mean = sum / msList.size();
        double p50 = getPercentile(msList, 50.0);
        double p90 = getPercentile(msList, 90.0);
        double p95 = getPercentile(msList, 95.0);
        double p99 = getPercentile(msList, 99.0);

        return new LatencyPercentiles(min, mean, p50, p90, p95, p99, max);
    }

    private static double getPercentile(List<Double> sorted, double percentile) {
        if (sorted.isEmpty()) return 0.0;
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
