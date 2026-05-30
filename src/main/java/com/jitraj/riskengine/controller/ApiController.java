package com.jitraj.riskengine.controller;

import com.jitraj.riskengine.dto.*;
import com.jitraj.riskengine.metrics.MetricsService;
import com.jitraj.riskengine.model.RiskSignal;
import com.jitraj.riskengine.repository.SignalRepository;
import com.jitraj.riskengine.scoring.ScoringService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing health, signal retrieval, and metrics endpoints.
 * Delegates all business logic to service/repository layers.
 */
@RestController
public class ApiController {

    private final SignalRepository signalRepository;
    private final MetricsService metricsService;
    private final ScoringService scoringService;

    public ApiController(SignalRepository signalRepository,
                          MetricsService metricsService,
                          ScoringService scoringService) {
        this.signalRepository = signalRepository;
        this.metricsService = metricsService;
        this.scoringService = scoringService;
    }

    /**
     * GET /health — application status, database connectivity, ONNX model status.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        metricsService.incrementApiRequest();

        String dbStatus = signalRepository.isHealthy() ? "UP" : "DOWN";
        String onnxStatus = scoringService.isModelLoaded() ? "LOADED" : "NOT_LOADED";

        HealthResponse response = new HealthResponse("UP", dbStatus, onnxStatus, "CONNECTED");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /signals/latest — most recent N signals, default 100.
     */
    @GetMapping("/signals/latest")
    public ResponseEntity<List<RiskSignalDto>> latestSignals(
            @RequestParam(defaultValue = "100") int limit) {
        metricsService.incrementApiRequest();

        List<RiskSignal> signals = signalRepository.findLatest(limit);
        List<RiskSignalDto> dtos = signals.stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /signals — filter by ticker and time range.
     */
    @GetMapping("/signals")
    public ResponseEntity<?> signals(
            @RequestParam String ticker,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "100") int limit) {
        metricsService.incrementApiRequest();

        Instant fromInstant;
        Instant toInstant;
        try {
            fromInstant = Instant.parse(from);
            toInstant = Instant.parse(to);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(400, "Bad Request",
                            "Invalid timestamp format. Use ISO-8601 (e.g., 2026-01-01T00:00:00Z)",
                            "/signals"));
        }

        List<RiskSignal> signals = signalRepository.findByTickerAndTimeRange(ticker, fromInstant, toInstant, limit);
        List<RiskSignalDto> dtos = signals.stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /signals/{eventId} — look up a specific signal.
     */
    @GetMapping("/signals/{eventId}")
    public ResponseEntity<?> signalById(@PathVariable String eventId) {
        metricsService.incrementApiRequest();

        Optional<RiskSignal> signal = signalRepository.findByEventId(eventId);
        if (signal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ErrorResponse(404, "Not Found",
                            "No signal found for eventId=" + eventId,
                            "/signals/" + eventId));
        }
        return ResponseEntity.ok(toDto(signal.get()));
    }

    /**
     * GET /metrics — current metrics snapshot.
     */
    @GetMapping("/metrics")
    public ResponseEntity<MetricsResponse> metrics() {
        metricsService.incrementApiRequest();
        return ResponseEntity.ok(metricsService.snapshot());
    }

    /**
     * Converts a persistence entity to an API DTO.
     */
    private RiskSignalDto toDto(RiskSignal signal) {
        return new RiskSignalDto(
                signal.getEventId(),
                signal.getTime(),
                signal.getTicker(),
                signal.getRawScore(),
                signal.getRiskScore(),
                signal.getLabel(),
                signal.getLatencyMs(),
                signal.getStatus()
        );
    }
}
