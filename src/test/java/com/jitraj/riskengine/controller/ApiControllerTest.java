package com.jitraj.riskengine.controller;

import com.jitraj.riskengine.metrics.MetricsService;
import com.jitraj.riskengine.model.RiskSignal;
import com.jitraj.riskengine.repository.SignalRepository;
import com.jitraj.riskengine.scoring.ScoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API integration tests for the REST controller.
 * Uses @WebMvcTest to test only the web layer with mocked dependencies.
 */
@WebMvcTest(ApiController.class)
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SignalRepository signalRepository;

    @MockBean
    private MetricsService metricsService;

    @MockBean
    private ScoringService scoringService;

    @Test
    @DisplayName("GET /health should return 200 with status fields")
    void healthShouldReturn200() throws Exception {
        when(signalRepository.isHealthy()).thenReturn(true);
        when(scoringService.isModelLoaded()).thenReturn(true);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.onnxModel").value("LOADED"))
                .andExpect(jsonPath("$.kafka").value("CONNECTED"));
    }

    @Test
    @DisplayName("GET /health should report DB DOWN when unavailable")
    void healthShouldReportDbDown() throws Exception {
        when(signalRepository.isHealthy()).thenReturn(false);
        when(scoringService.isModelLoaded()).thenReturn(true);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("DOWN"));
    }

    @Test
    @DisplayName("GET /signals/latest should return latest signals")
    void latestShouldReturnSignals() throws Exception {
        RiskSignal signal = new RiskSignal("evt-001", Instant.parse("2026-05-30T10:00:00Z"),
                "AAPL", -0.5, 72.5, "ELEVATED", 12.3, "SCORED");

        when(signalRepository.findLatest(anyInt())).thenReturn(List.of(signal));

        mockMvc.perform(get("/signals/latest").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-001"))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].riskScore").value(72.5))
                .andExpect(jsonPath("$[0].label").value("ELEVATED"))
                .andExpect(jsonPath("$[0].status").value("SCORED"));
    }

    @Test
    @DisplayName("GET /signals/latest should default to limit 100")
    void latestShouldDefaultLimit() throws Exception {
        when(signalRepository.findLatest(100)).thenReturn(List.of());

        mockMvc.perform(get("/signals/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /signals/{eventId} should return 200 when found")
    void signalByIdShouldReturn200WhenFound() throws Exception {
        RiskSignal signal = new RiskSignal("evt-123", Instant.parse("2026-05-30T10:00:00Z"),
                "TSLA", -0.3, 65.0, "ELEVATED", 8.5, "SCORED");

        when(signalRepository.findByEventId("evt-123")).thenReturn(Optional.of(signal));

        mockMvc.perform(get("/signals/evt-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-123"))
                .andExpect(jsonPath("$.ticker").value("TSLA"));
    }

    @Test
    @DisplayName("GET /signals/{eventId} should return 404 when not found")
    void signalByIdShouldReturn404WhenNotFound() throws Exception {
        when(signalRepository.findByEventId("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/signals/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("GET /signals should return filtered results")
    void signalsShouldReturnFilteredResults() throws Exception {
        RiskSignal signal = new RiskSignal("evt-f1", Instant.parse("2026-05-30T10:30:00Z"),
                "GOOG", -0.1, 45.0, "NORMAL", 5.0, "SCORED");

        when(signalRepository.findByTickerAndTimeRange(eq("GOOG"), any(), any(), anyInt()))
                .thenReturn(List.of(signal));

        mockMvc.perform(get("/signals")
                        .param("ticker", "GOOG")
                        .param("from", "2026-05-30T10:00:00Z")
                        .param("to", "2026-05-30T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("GOOG"));
    }

    @Test
    @DisplayName("GET /signals should return 400 on invalid timestamp")
    void signalsShouldReturn400OnInvalidTimestamp() throws Exception {
        mockMvc.perform(get("/signals")
                        .param("ticker", "AAPL")
                        .param("from", "not-a-timestamp")
                        .param("to", "2026-05-30T11:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /metrics should return metrics snapshot")
    void metricsShouldReturnSnapshot() throws Exception {
        var metrics = new com.jitraj.riskengine.dto.MetricsResponse(
                1000, 950, 10, 40, 158.33, 2.1, 5.3, 12.7,
                940, 5, 200, "1.0.0", "1.0.0");

        when(metricsService.snapshot()).thenReturn(metrics);

        mockMvc.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsConsumed").value(1000))
                .andExpect(jsonPath("$.eventsScored").value(950))
                .andExpect(jsonPath("$.eventsFailed").value(10))
                .andExpect(jsonPath("$.p50Ms").value(2.1))
                .andExpect(jsonPath("$.p95Ms").value(5.3))
                .andExpect(jsonPath("$.p99Ms").value(12.7))
                .andExpect(jsonPath("$.featureSchemaVersion").value("1.0.0"))
                .andExpect(jsonPath("$.modelVersion").value("1.0.0"));
    }
}
