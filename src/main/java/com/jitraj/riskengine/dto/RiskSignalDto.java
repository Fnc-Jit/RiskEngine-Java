package com.jitraj.riskengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Outgoing risk signal DTO returned by API endpoints.
 * This is a pure DTO — no persistence annotations.
 */
public record RiskSignalDto(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("ticker") String ticker,
        @JsonProperty("rawScore") Double rawScore,
        @JsonProperty("riskScore") Double riskScore,
        @JsonProperty("label") String label,
        @JsonProperty("latencyMs") Double latencyMs,
        @JsonProperty("status") String status
) {
}
