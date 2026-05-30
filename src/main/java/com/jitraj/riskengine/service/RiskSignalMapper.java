package com.jitraj.riskengine.service;

import com.jitraj.riskengine.config.RiskThresholdsConfig;
import com.jitraj.riskengine.model.ProcessingStatus;
import com.jitraj.riskengine.model.RiskLabel;
import com.jitraj.riskengine.model.RiskSignal;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Converts a raw model output into a RiskSignal containing normalized risk score,
 * label, latency, and status.
 */
@Service
public class RiskSignalMapper {

    private final RiskThresholdsConfig thresholdsConfig;

    public RiskSignalMapper(RiskThresholdsConfig thresholdsConfig) {
        this.thresholdsConfig = thresholdsConfig;
    }

    /**
     * Normalizes a raw anomaly score to the [0, 100] range.
     * Isolation Forest decision_function returns negative for anomalies,
     * positive for normal. We invert and scale:
     *   - Very negative raw score → high risk (close to 100)
     *   - Very positive raw score → low risk (close to 0)
     *
     * Sigmoid-based normalization is used for smooth mapping.
     *
     * @param rawScore the raw model output
     * @return a normalized risk score in [0, 100]
     */
    public double normalizeScore(double rawScore) {
        // Sigmoid-based normalization: risk = 100 * sigmoid(-rawScore * scale)
        // This maps negative raw scores (anomalies) to high risk scores
        double scale = 5.0; // tunable sensitivity
        double sigmoid = 1.0 / (1.0 + Math.exp(rawScore * scale));
        return Math.max(0.0, Math.min(100.0, sigmoid * 100.0));
    }

    /**
     * Assigns a risk label based on the normalized score and configured thresholds.
     *
     * @param riskScore the normalized risk score [0, 100]
     * @return the appropriate RiskLabel
     */
    public RiskLabel assignLabel(double riskScore) {
        if (riskScore >= thresholdsConfig.getCritical()) {
            return RiskLabel.CRITICAL;
        } else if (riskScore >= thresholdsConfig.getElevated()) {
            return RiskLabel.ELEVATED;
        }
        return RiskLabel.NORMAL;
    }

    /**
     * Creates a fully-populated RiskSignal for a successfully scored event.
     *
     * @param eventId   the event ID
     * @param timestamp the event timestamp
     * @param ticker    the ticker symbol
     * @param rawScore  the raw model output
     * @param latencyMs the inference latency in milliseconds
     * @return a RiskSignal with status SCORED
     */
    public RiskSignal mapScored(String eventId, Instant timestamp, String ticker,
                                 double rawScore, double latencyMs) {
        double riskScore = normalizeScore(rawScore);
        RiskLabel label = assignLabel(riskScore);

        return new RiskSignal(
                eventId,
                timestamp,
                ticker,
                rawScore,
                riskScore,
                label.name(),
                latencyMs,
                ProcessingStatus.SCORED.name()
        );
    }

    /**
     * Creates a RiskSignal for a failed event (inference error or invalid features).
     */
    public RiskSignal mapFailed(String eventId, Instant timestamp, String ticker) {
        return new RiskSignal(
                eventId,
                timestamp,
                ticker,
                null,
                null,
                null,
                null,
                ProcessingStatus.FAILED.name()
        );
    }

    /**
     * Creates a RiskSignal for an event that arrived during cold-start.
     */
    public RiskSignal mapWarmingUp(String eventId, Instant timestamp, String ticker) {
        return new RiskSignal(
                eventId,
                timestamp,
                ticker,
                null,
                null,
                null,
                null,
                ProcessingStatus.WARMING_UP.name()
        );
    }
}
