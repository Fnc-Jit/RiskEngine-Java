package com.jitraj.riskengine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * Incoming market event payload consumed from Kafka.
 * This is a pure DTO — no persistence annotations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketEvent(
        @JsonProperty("eventId") @NotBlank String eventId,
        @JsonProperty("timestamp") @NotNull Instant timestamp,
        @JsonProperty("ticker") @NotBlank String ticker,
        @JsonProperty("price") @NotNull @Positive Double price,
        @JsonProperty("volume") @NotNull @Positive Long volume,
        @JsonProperty("volatility") @NotNull Double volatility,
        @JsonProperty("bidAskSpread") @NotNull Double bidAskSpread,
        @JsonProperty("timeBucket") @NotBlank String timeBucket,
        @JsonProperty("anomalyTag") Boolean anomalyTag
) {
    /**
     * Validates that all required fields are present and within acceptable ranges.
     *
     * @return true if the event passes basic validation
     */
    public boolean isValid() {
        return eventId != null && !eventId.isBlank()
                && timestamp != null
                && ticker != null && !ticker.isBlank()
                && price != null && price > 0
                && volume != null && volume > 0
                && volatility != null
                && bidAskSpread != null
                && timeBucket != null && !timeBucket.isBlank();
    }
}
