package com.jitraj.riskengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for GET /metrics endpoint.
 * Contains counters, latency percentiles, throughput, and diagnostic info.
 */
public record MetricsResponse(
        @JsonProperty("eventsConsumed") long eventsConsumed,
        @JsonProperty("eventsScored") long eventsScored,
        @JsonProperty("eventsFailed") long eventsFailed,
        @JsonProperty("eventsWarmingUp") long eventsWarmingUp,
        @JsonProperty("throughputEps") double throughputEps,
        @JsonProperty("p50Ms") double p50Ms,
        @JsonProperty("p95Ms") double p95Ms,
        @JsonProperty("p99Ms") double p99Ms,
        @JsonProperty("dbWriteSuccess") long dbWriteSuccess,
        @JsonProperty("dbWriteFailures") long dbWriteFailures,
        @JsonProperty("apiRequestCount") long apiRequestCount,
        @JsonProperty("featureSchemaVersion") String featureSchemaVersion,
        @JsonProperty("modelVersion") String modelVersion
) {
}
