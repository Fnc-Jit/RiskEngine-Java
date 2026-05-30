package com.jitraj.riskengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard error response body for 400/404/500 responses.
 */
public record ErrorResponse(
        @JsonProperty("status") int status,
        @JsonProperty("error") String error,
        @JsonProperty("message") String message,
        @JsonProperty("path") String path
) {
}
