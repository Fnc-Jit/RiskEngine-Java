package com.jitraj.riskengine.service;

import com.jitraj.riskengine.config.FeatureConfig;
import com.jitraj.riskengine.dto.MarketEvent;
import com.jitraj.riskengine.util.RollingWindow;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts a validated MarketEvent into a deterministic numeric feature vector
 * using rolling per-ticker state.
 *
 * Feature vector layout (6 features):
 *   [0] priceZScore     — z-score of price relative to rolling window
 *   [1] volumeRatio     — ratio of current volume to rolling average
 *   [2] rollingVolatility — rolling stddev of price
 *   [3] bidAskSpread    — direct from event
 *   [4] timeOfDayEncoding — numeric encoding of time bucket
 *   [5] directionStreak — consecutive same-direction price moves
 */
@Service
public class FeatureExtractor {

    private static final Logger log = LoggerFactory.getLogger(FeatureExtractor.class);

    private final FeatureConfig featureConfig;

    // Per-ticker rolling state
    private final ConcurrentHashMap<String, TickerState> tickerStates = new ConcurrentHashMap<>();

    public FeatureExtractor(FeatureConfig featureConfig) {
        this.featureConfig = featureConfig;
    }

    @PostConstruct
    public void init() {
        log.info("FeatureExtractor initialized — schema version: {}, window size: {}, min samples: {}",
                featureConfig.getSchema().getVersion(),
                featureConfig.getWindow().getSize(),
                featureConfig.getWindow().getMinSamples());
    }

    /**
     * @return the active feature schema version
     */
    public String getSchemaVersion() {
        return featureConfig.getSchema().getVersion();
    }

    /**
     * Checks whether the given ticker has accumulated enough samples.
     *
     * @param ticker the ticker symbol
     * @return true if the ticker is still in cold-start mode
     */
    public boolean isColdStart(String ticker) {
        TickerState state = tickerStates.get(ticker);
        return state == null || state.priceWindow.size() < featureConfig.getWindow().getMinSamples();
    }

    /**
     * Extracts a numeric feature vector from a market event.
     * Also updates the internal rolling state for the ticker.
     *
     * @param event the incoming market event
     * @return a float array of length 6, or null if a feature value is invalid
     */
    public float[] extract(MarketEvent event) {
        int windowSize = featureConfig.getWindow().getSize();
        TickerState state = tickerStates.computeIfAbsent(event.ticker(),
                k -> new TickerState(windowSize));

        // Compute features BEFORE updating windows (use current state)
        double priceZScore = state.priceWindow.zScore(event.price());
        double volumeRatio = state.volumeWindow.ratio(event.volume().doubleValue());
        double rollingVolatility = state.priceWindow.stddev();
        double bidAskSpread = event.bidAskSpread();
        double timeOfDayEncoding = encodeTimeBucket(event.timeBucket());
        double directionStreak = computeDirectionStreak(state, event.price());

        // Now update windows with current event data
        state.priceWindow.add(event.price());
        state.volumeWindow.add(event.volume().doubleValue());

        // Validate features
        float[] vector = new float[]{
                (float) priceZScore,
                (float) volumeRatio,
                (float) rollingVolatility,
                (float) bidAskSpread,
                (float) timeOfDayEncoding,
                (float) directionStreak
        };

        for (float v : vector) {
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                return null; // Signals invalid feature
            }
        }

        return vector;
    }

    /**
     * Encodes the time bucket string into a numeric value.
     */
    private double encodeTimeBucket(String timeBucket) {
        return switch (timeBucket.toUpperCase()) {
            case "PRE_MARKET" -> 0.0;
            case "OPEN" -> 0.2;
            case "MID_MORNING" -> 0.4;
            case "LUNCH" -> 0.5;
            case "AFTERNOON" -> 0.7;
            case "CLOSE" -> 0.9;
            case "AFTER_HOURS" -> 1.0;
            default -> 0.5;
        };
    }

    /**
     * Computes a direction streak: counts consecutive same-direction price moves.
     * Positive for consecutive ups, negative for consecutive downs.
     */
    private double computeDirectionStreak(TickerState state, double currentPrice) {
        double lastPrice = state.priceWindow.latest();
        if (lastPrice == 0.0) {
            state.streak = 0;
            return 0.0;
        }
        if (currentPrice > lastPrice) {
            state.streak = Math.max(1, state.streak + 1);
        } else if (currentPrice < lastPrice) {
            state.streak = Math.min(-1, state.streak - 1);
        }
        // If equal, streak stays the same
        // Normalize streak to roughly [-1, 1] range
        return Math.tanh(state.streak / 5.0);
    }

    /**
     * Internal per-ticker rolling state container.
     */
    private static class TickerState {
        final RollingWindow priceWindow;
        final RollingWindow volumeWindow;
        int streak;

        TickerState(int windowSize) {
            this.priceWindow = new RollingWindow(windowSize);
            this.volumeWindow = new RollingWindow(windowSize);
            this.streak = 0;
        }
    }
}
