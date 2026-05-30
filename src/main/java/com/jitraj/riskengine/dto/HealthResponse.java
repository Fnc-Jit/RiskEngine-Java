package com.jitraj.riskengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Health check response DTO for GET /health endpoint.
 */
public record HealthResponse(
        @JsonProperty("status") String status,
        @JsonProperty("database") String database,
        @JsonProperty("onnxModel") String onnxModel,
        @JsonProperty("kafka") String kafka
) {
}
