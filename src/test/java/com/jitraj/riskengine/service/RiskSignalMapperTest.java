package com.jitraj.riskengine.service;

import com.jitraj.riskengine.config.RiskThresholdsConfig;
import com.jitraj.riskengine.model.RiskLabel;
import com.jitraj.riskengine.model.RiskSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for risk score normalization and label assignment.
 */
class RiskSignalMapperTest {

    private RiskSignalMapper mapper;

    @BeforeEach
    void setUp() {
        RiskThresholdsConfig config = new RiskThresholdsConfig();
        config.setElevated(50.0);
        config.setCritical(85.0);
        mapper = new RiskSignalMapper(config);
    }

    @Test
    @DisplayName("Normalized score should be in [0, 100]")
    void normalizedScoreShouldBeInRange() {
        for (double raw = -5.0; raw <= 5.0; raw += 0.5) {
            double score = mapper.normalizeScore(raw);
            assertTrue(score >= 0 && score <= 100, "Score " + score + " for raw " + raw);
        }
    }

    @Test
    @DisplayName("Negative raw scores should yield high risk")
    void negativeRawShouldBeHighRisk() {
        double score = mapper.normalizeScore(-1.0);
        assertTrue(score > 50, "Expected high risk for negative raw, got: " + score);
    }

    @Test
    @DisplayName("Positive raw scores should yield low risk")
    void positiveRawShouldBeLowRisk() {
        double score = mapper.normalizeScore(1.0);
        assertTrue(score < 50, "Expected low risk for positive raw, got: " + score);
    }

    @Test
    @DisplayName("Should assign NORMAL label for score < 50")
    void shouldAssignNormal() {
        assertEquals(RiskLabel.NORMAL, mapper.assignLabel(30.0));
        assertEquals(RiskLabel.NORMAL, mapper.assignLabel(0.0));
        assertEquals(RiskLabel.NORMAL, mapper.assignLabel(49.9));
    }

    @Test
    @DisplayName("Should assign ELEVATED label for 50 <= score < 85")
    void shouldAssignElevated() {
        assertEquals(RiskLabel.ELEVATED, mapper.assignLabel(50.0));
        assertEquals(RiskLabel.ELEVATED, mapper.assignLabel(70.0));
        assertEquals(RiskLabel.ELEVATED, mapper.assignLabel(84.9));
    }

    @Test
    @DisplayName("Should assign CRITICAL label for score >= 85")
    void shouldAssignCritical() {
        assertEquals(RiskLabel.CRITICAL, mapper.assignLabel(85.0));
        assertEquals(RiskLabel.CRITICAL, mapper.assignLabel(95.0));
        assertEquals(RiskLabel.CRITICAL, mapper.assignLabel(100.0));
    }

    @Test
    @DisplayName("mapScored should produce a SCORED signal with all fields")
    void mapScoredShouldProduceCompleteSignal() {
        RiskSignal signal = mapper.mapScored("evt-001", Instant.now(), "AAPL", -0.5, 12.3);

        assertEquals("evt-001", signal.getEventId());
        assertEquals("AAPL", signal.getTicker());
        assertNotNull(signal.getRawScore());
        assertNotNull(signal.getRiskScore());
        assertNotNull(signal.getLabel());
        assertEquals(12.3, signal.getLatencyMs());
        assertEquals("SCORED", signal.getStatus());
    }

    @Test
    @DisplayName("mapFailed should produce a FAILED signal with null scores")
    void mapFailedShouldHaveNullScores() {
        RiskSignal signal = mapper.mapFailed("evt-002", Instant.now(), "TSLA");

        assertEquals("FAILED", signal.getStatus());
        assertNull(signal.getRawScore());
        assertNull(signal.getRiskScore());
        assertNull(signal.getLabel());
    }

    @Test
    @DisplayName("mapWarmingUp should produce a WARMING_UP signal")
    void mapWarmingUpShouldSetCorrectStatus() {
        RiskSignal signal = mapper.mapWarmingUp("evt-003", Instant.now(), "GOOG");

        assertEquals("WARMING_UP", signal.getStatus());
        assertNull(signal.getRawScore());
    }
}
