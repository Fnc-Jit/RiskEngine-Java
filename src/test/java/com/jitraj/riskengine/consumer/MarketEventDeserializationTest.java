package com.jitraj.riskengine.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jitraj.riskengine.dto.MarketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MarketEvent JSON deserialization and validation.
 */
class MarketEventDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should deserialize a valid MarketEvent JSON payload")
    void shouldDeserializeValidEvent() throws Exception {
        String json = """
                {
                    "eventId": "evt-abc123",
                    "timestamp": "2026-05-30T10:15:22.124Z",
                    "ticker": "AAPL",
                    "price": 211.42,
                    "volume": 148200,
                    "volatility": 0.018,
                    "bidAskSpread": 0.07,
                    "timeBucket": "OPEN",
                    "anomalyTag": false
                }
                """;

        MarketEvent event = objectMapper.readValue(json, MarketEvent.class);

        assertEquals("evt-abc123", event.eventId());
        assertEquals("AAPL", event.ticker());
        assertEquals(211.42, event.price(), 0.001);
        assertEquals(148200L, event.volume());
        assertEquals(0.018, event.volatility(), 0.0001);
        assertEquals(0.07, event.bidAskSpread(), 0.001);
        assertEquals("OPEN", event.timeBucket());
        assertFalse(event.anomalyTag());
        assertTrue(event.isValid());
    }

    @Test
    @DisplayName("Should handle missing optional anomalyTag field")
    void shouldHandleMissingAnomalyTag() throws Exception {
        String json = """
                {
                    "eventId": "evt-def456",
                    "timestamp": "2026-05-30T10:15:22.124Z",
                    "ticker": "GOOG",
                    "price": 175.00,
                    "volume": 200000,
                    "volatility": 0.025,
                    "bidAskSpread": 0.03,
                    "timeBucket": "CLOSE"
                }
                """;

        MarketEvent event = objectMapper.readValue(json, MarketEvent.class);

        assertNull(event.anomalyTag());
        assertTrue(event.isValid());
    }

    @Test
    @DisplayName("Should report invalid when required field is missing")
    void shouldReportInvalidOnMissingField() throws Exception {
        String json = """
                {
                    "eventId": "evt-ghi789",
                    "timestamp": "2026-05-30T10:15:22.124Z",
                    "price": 211.42,
                    "volume": 148200,
                    "volatility": 0.018,
                    "bidAskSpread": 0.07,
                    "timeBucket": "OPEN"
                }
                """;
        // Missing "ticker" field

        MarketEvent event = objectMapper.readValue(json, MarketEvent.class);
        assertFalse(event.isValid());
    }

    @Test
    @DisplayName("Should report invalid when price is negative")
    void shouldReportInvalidOnNegativePrice() throws Exception {
        String json = """
                {
                    "eventId": "evt-neg001",
                    "timestamp": "2026-05-30T10:15:22.124Z",
                    "ticker": "TSLA",
                    "price": -10.0,
                    "volume": 148200,
                    "volatility": 0.018,
                    "bidAskSpread": 0.07,
                    "timeBucket": "OPEN"
                }
                """;

        MarketEvent event = objectMapper.readValue(json, MarketEvent.class);
        assertFalse(event.isValid());
    }

    @Test
    @DisplayName("Should throw on completely malformed JSON")
    void shouldThrowOnMalformedJson() {
        String json = "this is not valid json!!!";
        assertThrows(Exception.class, () -> objectMapper.readValue(json, MarketEvent.class));
    }

    @Test
    @DisplayName("Should ignore unknown fields")
    void shouldIgnoreUnknownFields() throws Exception {
        String json = """
                {
                    "eventId": "evt-unk001",
                    "timestamp": "2026-05-30T10:15:22.124Z",
                    "ticker": "MSFT",
                    "price": 420.00,
                    "volume": 100000,
                    "volatility": 0.015,
                    "bidAskSpread": 0.02,
                    "timeBucket": "AFTERNOON",
                    "extraField": "should be ignored",
                    "anotherUnknown": 42
                }
                """;

        MarketEvent event = objectMapper.readValue(json, MarketEvent.class);
        assertTrue(event.isValid());
        assertEquals("MSFT", event.ticker());
    }
}
