package com.jitraj.riskengine.service;

import com.jitraj.riskengine.config.FeatureConfig;
import com.jitraj.riskengine.dto.MarketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the FeatureExtractor.
 */
class FeatureExtractorTest {

    private FeatureExtractor featureExtractor;

    @BeforeEach
    void setUp() {
        FeatureConfig config = new FeatureConfig();
        FeatureConfig.WindowConfig windowConfig = new FeatureConfig.WindowConfig();
        windowConfig.setMinSamples(3); // Lower for testing
        windowConfig.setSize(10);
        config.setWindow(windowConfig);

        FeatureConfig.SchemaConfig schemaConfig = new FeatureConfig.SchemaConfig();
        schemaConfig.setVersion("1.0.0");
        config.setSchema(schemaConfig);

        featureExtractor = new FeatureExtractor(config);
        featureExtractor.init();
    }

    @Test
    @DisplayName("Should report cold start when ticker has no history")
    void shouldReportColdStart() {
        assertTrue(featureExtractor.isColdStart("AAPL"));
    }

    @Test
    @DisplayName("Should exit cold start after min-samples events")
    void shouldExitColdStartAfterMinSamples() {
        // Feed 3 events (minSamples = 3)
        for (int i = 0; i < 3; i++) {
            MarketEvent event = createEvent("AAPL", 200.0 + i, 100000L + i * 1000);
            featureExtractor.extract(event);
        }

        assertFalse(featureExtractor.isColdStart("AAPL"));
    }

    @Test
    @DisplayName("Should produce a 6-element feature vector")
    void shouldProduceSixElementVector() {
        // Feed enough events to pass cold start
        for (int i = 0; i < 4; i++) {
            featureExtractor.extract(createEvent("GOOG", 170.0 + i, 200000L));
        }

        float[] features = featureExtractor.extract(createEvent("GOOG", 175.0, 220000L));
        assertNotNull(features);
        assertEquals(6, features.length);
    }

    @Test
    @DisplayName("Feature extraction should be deterministic for same sequence")
    void shouldBeDeterministic() {
        // Run 1
        FeatureConfig config = new FeatureConfig();
        FeatureConfig.WindowConfig windowConfig = new FeatureConfig.WindowConfig();
        windowConfig.setMinSamples(2);
        windowConfig.setSize(10);
        config.setWindow(windowConfig);
        FeatureConfig.SchemaConfig schemaConfig = new FeatureConfig.SchemaConfig();
        schemaConfig.setVersion("1.0.0");
        config.setSchema(schemaConfig);

        FeatureExtractor extractor1 = new FeatureExtractor(config);
        extractor1.init();

        FeatureExtractor extractor2 = new FeatureExtractor(config);
        extractor2.init();

        // Same sequence of events
        MarketEvent e1 = createEvent("MSFT", 420.0, 150000L);
        MarketEvent e2 = createEvent("MSFT", 421.0, 155000L);
        MarketEvent e3 = createEvent("MSFT", 422.5, 160000L);

        extractor1.extract(e1);
        extractor1.extract(e2);
        float[] f1 = extractor1.extract(e3);

        extractor2.extract(e1);
        extractor2.extract(e2);
        float[] f2 = extractor2.extract(e3);

        assertNotNull(f1);
        assertNotNull(f2);
        assertArrayEquals(f1, f2, 1e-6f);
    }

    @Test
    @DisplayName("Should expose schema version")
    void shouldExposeSchemaVersion() {
        assertEquals("1.0.0", featureExtractor.getSchemaVersion());
    }

    private MarketEvent createEvent(String ticker, double price, long volume) {
        return new MarketEvent(
                "evt-test-" + System.nanoTime(),
                Instant.now(),
                ticker,
                price,
                volume,
                0.02,
                0.05,
                "OPEN",
                false
        );
    }
}
