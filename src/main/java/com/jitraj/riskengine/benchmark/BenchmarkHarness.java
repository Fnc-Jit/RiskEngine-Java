package com.jitraj.riskengine.benchmark;

import com.jitraj.riskengine.dto.MetricsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Requirement 14: Benchmark harness implementation.
 *
 * Drives the EventGenerator at a configured target rate, waits for the warm-up
 * period, then collects latency and throughput artifacts from the RiskEngine
 * MetricsService over the measurement window.
 *
 * Usage (invoked by the Gradle 'benchmark' task):
 *   ./gradlew benchmark -PtargetRate=1000 -Pseed=42
 *   ./gradlew benchmark -PtargetRate=5000 -Pseed=42
 *
 * The harness:
 *   1. Starts the EventGenerator subprocess at the given rate and seed.
 *   2. Waits for the warm-up period (default 60 seconds).
 *   3. Polls /metrics every second for the measurement window (default 180 seconds).
 *   4. Prints a benchmark result table to stdout.
 *   5. Stops the EventGenerator subprocess.
 */
public class BenchmarkHarness {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkHarness.class);

    private static final int WARMUP_SECONDS = 60;
    private static final int MEASUREMENT_SECONDS = 180;
    private static final int POLL_INTERVAL_MS = 1_000;
    private static final String DEFAULT_API_BASE = "http://localhost:8080";
    private static final String DEFAULT_GENERATOR_SCRIPT = "scripts/event_generator.py";

    private final int targetRate;
    private final long seed;
    private final String apiBase;
    private final String generatorScript;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BenchmarkHarness(int targetRate, long seed, String apiBase, String generatorScript) {
        this.targetRate = targetRate;
        this.seed = seed;
        this.apiBase = apiBase;
        this.generatorScript = generatorScript;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Runs the full benchmark: warm-up → measurement → report.
     */
    public BenchmarkResult run() throws Exception {
        log.info("=== RiskEngine Benchmark Harness ===");
        log.info("Target rate: {} events/sec | Seed: {} | Warm-up: {}s | Measurement: {}s",
                targetRate, seed, WARMUP_SECONDS, MEASUREMENT_SECONDS);

        // 1. Start the event generator
        Process generatorProcess = startGenerator();
        log.info("EventGenerator started (PID: {})", generatorProcess.pid());

        try {
            // 2. Warm-up phase
            log.info("Warm-up phase: {} seconds...", WARMUP_SECONDS);
            Thread.sleep(WARMUP_SECONDS * 1_000L);
            log.info("Warm-up complete. Starting measurement window.");

            // 3. Snapshot metrics at start of measurement window
            MetricsResponse startSnapshot = fetchMetrics();
            Instant measurementStart = Instant.now();

            // 4. Measurement phase — poll every second
            MetricsResponse latestSnapshot = startSnapshot;
            int polls = 0;
            double minP99 = Double.MAX_VALUE;
            double maxP99 = 0;
            double sumP99 = 0;

            while (Duration.between(measurementStart, Instant.now()).getSeconds() < MEASUREMENT_SECONDS) {
                Thread.sleep(POLL_INTERVAL_MS);
                try {
                    latestSnapshot = fetchMetrics();
                    double p99 = latestSnapshot.p99Ms();
                    if (p99 > 0) {
                        minP99 = Math.min(minP99, p99);
                        maxP99 = Math.max(maxP99, p99);
                        sumP99 += p99;
                        polls++;
                    }
                } catch (Exception e) {
                    log.warn("Metrics poll failed: {}", e.getMessage());
                }
            }

            // 5. Compute results
            long eventsInWindow = latestSnapshot.eventsScored() - startSnapshot.eventsScored();
            double avgP99 = polls > 0 ? sumP99 / polls : 0;
            double measuredThroughput = eventsInWindow / (double) MEASUREMENT_SECONDS;

            BenchmarkResult result = new BenchmarkResult(
                    targetRate,
                    seed,
                    WARMUP_SECONDS,
                    MEASUREMENT_SECONDS,
                    measuredThroughput,
                    latestSnapshot.p50Ms(),
                    latestSnapshot.p95Ms(),
                    avgP99,
                    latestSnapshot.eventsFailed(),
                    latestSnapshot.dbWriteFailures()
            );

            printResultTable(result);
            return result;

        } finally {
            // 6. Stop the generator
            if (generatorProcess.isAlive()) {
                generatorProcess.destroy();
                log.info("EventGenerator stopped.");
            }
        }
    }

    /**
     * Starts the Python event generator as a subprocess.
     */
    private Process startGenerator() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "python3", generatorScript,
                "--rate", String.valueOf(targetRate),
                "--seed", String.valueOf(seed)
        );
        pb.redirectErrorStream(true);
        pb.inheritIO();
        return pb.start();
    }

    /**
     * Fetches the current MetricsResponse from the running RiskEngine.
     */
    private MetricsResponse fetchMetrics() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/metrics"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), MetricsResponse.class);
    }

    /**
     * Prints a formatted benchmark result table to stdout.
     */
    private void printResultTable(BenchmarkResult result) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              RiskEngine Benchmark Results                    ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Target rate:       %6d events/sec                         ║%n", result.targetRate());
        System.out.printf("║  Measured throughput: %6.1f events/sec                      ║%n", result.measuredThroughputEps());
        System.out.printf("║  Seed:              %6d                                    ║%n", result.seed());
        System.out.printf("║  Warm-up:           %6d seconds                            ║%n", result.warmupSeconds());
        System.out.printf("║  Measurement:       %6d seconds                            ║%n", result.measurementSeconds());
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  p50 latency:       %8.2f ms                               ║%n", result.p50Ms());
        System.out.printf("║  p95 latency:       %8.2f ms                               ║%n", result.p95Ms());
        System.out.printf("║  p99 latency (avg): %8.2f ms                               ║%n", result.p99AvgMs());
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Events failed:     %6d                                    ║%n", result.eventsFailed());
        System.out.printf("║  DB write failures: %6d                                    ║%n", result.dbWriteFailures());
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Warn if p99 target missed
        if (result.targetRate() <= 1000 && result.p99AvgMs() > 50.0) {
            System.out.printf("⚠️  WARNING: p99 latency %.2f ms exceeds 50 ms target at 1K eps%n", result.p99AvgMs());
        }
    }

    /**
     * Immutable benchmark result record.
     */
    public record BenchmarkResult(
            int targetRate,
            long seed,
            int warmupSeconds,
            int measurementSeconds,
            double measuredThroughputEps,
            double p50Ms,
            double p95Ms,
            double p99AvgMs,
            long eventsFailed,
            long dbWriteFailures
    ) {}

    /**
     * Entry point for the Gradle benchmark task.
     * Reads targetRate and seed from system properties set by the Gradle task.
     */
    public static void main(String[] args) throws Exception {
        int targetRate = Integer.parseInt(System.getProperty("benchmark.targetRate", "1000"));
        long seed = Long.parseLong(System.getProperty("benchmark.seed", "42"));
        String apiBase = System.getProperty("benchmark.apiBase", DEFAULT_API_BASE);
        String generatorScript = System.getProperty("benchmark.generatorScript", DEFAULT_GENERATOR_SCRIPT);

        BenchmarkHarness harness = new BenchmarkHarness(targetRate, seed, apiBase, generatorScript);
        harness.run();
    }
}
