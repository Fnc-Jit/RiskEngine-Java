package com.jitraj.riskengine.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RollingWindow utility.
 */
class RollingWindowTest {

    @Test
    @DisplayName("Should maintain correct size within bounds")
    void shouldMaintainSize() {
        RollingWindow window = new RollingWindow(5);

        for (int i = 0; i < 10; i++) {
            window.add(i);
        }

        assertEquals(5, window.size());
        assertTrue(window.isFull());
    }

    @Test
    @DisplayName("Should compute correct mean")
    void shouldComputeMean() {
        RollingWindow window = new RollingWindow(5);
        window.add(10);
        window.add(20);
        window.add(30);

        assertEquals(20.0, window.mean(), 0.001);
    }

    @Test
    @DisplayName("Should compute correct z-score")
    void shouldComputeZScore() {
        RollingWindow window = new RollingWindow(100);
        // Add values centered around 100
        for (int i = 0; i < 50; i++) {
            window.add(100.0);
        }

        // Value equal to mean should have z-score near 0
        assertEquals(0.0, window.zScore(100.0), 0.001);
    }

    @Test
    @DisplayName("Should compute correct ratio")
    void shouldComputeRatio() {
        RollingWindow window = new RollingWindow(5);
        window.add(100);
        window.add(100);
        window.add(100);

        assertEquals(2.0, window.ratio(200.0), 0.001);
        assertEquals(0.5, window.ratio(50.0), 0.001);
    }

    @Test
    @DisplayName("Should handle empty window gracefully")
    void shouldHandleEmptyWindow() {
        RollingWindow window = new RollingWindow(5);

        assertEquals(0.0, window.mean());
        assertEquals(0.0, window.stddev());
        assertEquals(0.0, window.zScore(42.0));
        assertEquals(0.0, window.latest());
    }
}
