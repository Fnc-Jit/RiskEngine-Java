package com.jitraj.riskengine.metrics;

import com.jitraj.riskengine.config.FeatureConfig;
import com.jitraj.riskengine.config.ScoringConfig;
import com.jitraj.riskengine.dto.MetricsResponse;
import org.HdrHistogram.Histogram;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory metrics aggregation service.
 * Tracks counters and HdrHistogram-backed latency percentiles.
 */
@Service
public class MetricsService {

    // Counters
    private final AtomicLong eventsConsumed = new AtomicLong(0);
    private final AtomicLong eventsScored = new AtomicLong(0);
    private final AtomicLong eventsFailed = new AtomicLong(0);
    private final AtomicLong eventsWarmingUp = new AtomicLong(0);
    private final AtomicLong eventsMalformed = new AtomicLong(0);
    private final AtomicLong eventsInvalid = new AtomicLong(0);
    private final AtomicLong featuresInvalid = new AtomicLong(0);
    private final AtomicLong dbWriteSuccess = new AtomicLong(0);
    private final AtomicLong dbWriteFailures = new AtomicLong(0);
    private final AtomicLong apiRequestCount = new AtomicLong(0);

    // Latency histogram (microseconds internally, reported as milliseconds)
    // Range: 1µs to 60 seconds, 3 significant digits
    private final Histogram latencyHistogram = new Histogram(60_000_000L, 3);

    // Throughput tracking
    private final long startTimeMs = System.currentTimeMillis();

    @Value("${metrics.window-seconds:60}")
    private int windowSeconds;

    private final FeatureConfig featureConfig;
    private final ScoringConfig scoringConfig;

    public MetricsService(FeatureConfig featureConfig, ScoringConfig scoringConfig) {
        this.featureConfig = featureConfig;
        this.scoringConfig = scoringConfig;
    }

    // --- Increment methods ---

    public void incrementConsumed() {
        eventsConsumed.incrementAndGet();
    }

    public void incrementScored() {
        eventsScored.incrementAndGet();
    }

    public void incrementFailed() {
        eventsFailed.incrementAndGet();
    }

    public void incrementWarmingUp() {
        eventsWarmingUp.incrementAndGet();
    }

    public void incrementMalformed() {
        eventsMalformed.incrementAndGet();
    }

    public void incrementInvalid() {
        eventsInvalid.incrementAndGet();
    }

    public void incrementFeaturesInvalid() {
        featuresInvalid.incrementAndGet();
    }

    public void incrementDbWriteSuccess() {
        dbWriteSuccess.incrementAndGet();
    }

    public void incrementDbWriteFailures() {
        dbWriteFailures.incrementAndGet();
    }

    public void incrementApiRequest() {
        apiRequestCount.incrementAndGet();
    }

    /**
     * Records inference latency in milliseconds.
     */
    public void recordLatency(double latencyMs) {
        long latencyUs = (long) (latencyMs * 1000); // convert to microseconds
        if (latencyUs > 0 && latencyUs <= latencyHistogram.getMaxValue()) {
            synchronized (latencyHistogram) {
                latencyHistogram.recordValue(latencyUs);
            }
        }
    }

    /**
     * Returns a snapshot of all current metrics.
     */
    public MetricsResponse snapshot() {
        double elapsedSeconds = (System.currentTimeMillis() - startTimeMs) / 1000.0;
        double throughput = elapsedSeconds > 0 ? eventsScored.get() / elapsedSeconds : 0.0;

        double p50;
        double p95;
        double p99;
        synchronized (latencyHistogram) {
            p50 = latencyHistogram.getValueAtPercentile(50.0) / 1000.0; // µs → ms
            p95 = latencyHistogram.getValueAtPercentile(95.0) / 1000.0;
            p99 = latencyHistogram.getValueAtPercentile(99.0) / 1000.0;
        }

        return new MetricsResponse(
                eventsConsumed.get(),
                eventsScored.get(),
                eventsFailed.get(),
                eventsWarmingUp.get(),
                Math.round(throughput * 100.0) / 100.0,
                Math.round(p50 * 100.0) / 100.0,
                Math.round(p95 * 100.0) / 100.0,
                Math.round(p99 * 100.0) / 100.0,
                dbWriteSuccess.get(),
                dbWriteFailures.get(),
                apiRequestCount.get(),
                featureConfig.getSchema().getVersion(),
                scoringConfig.getModel().getVersion()
        );
    }
}
