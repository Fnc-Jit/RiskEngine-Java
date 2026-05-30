package com.jitraj.riskengine.scoring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitraj.riskengine.config.ScoringConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requirement 5: Python ↔ Java parity validation.
 *
 * Loads the golden_vectors.json file (checked into the repository at model/golden_vectors.json),
 * runs each input through the Java ScoringService, and asserts that every Java raw score
 * is within parity.epsilon of the expected Python raw score.
 *
 * The epsilon default is 1e-5 as specified in application.yml.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Python ↔ Java Parity Test (Req 5)")
class ParityTest {

    private static final double PARITY_EPSILON = 1e-5;
    private static final String GOLDEN_VECTORS_PATH = "model/golden_vectors.json";

    private ScoringService scoringService;
    private List<Map<String, Object>> goldenVectors;

    @BeforeAll
    void setUp() throws Exception {
        // Load the ONNX model using the real model path
        ScoringConfig config = new ScoringConfig();
        ScoringConfig.ModelConfig modelConfig = new ScoringConfig.ModelConfig();
        modelConfig.setPath(GOLDEN_VECTORS_PATH.replace("golden_vectors.json", "risk_model.onnx"));
        modelConfig.setVersion("1.0.0");
        config.setModel(modelConfig);

        scoringService = new ScoringService(config);
        scoringService.init(); // loads the ONNX session

        // Load golden vectors
        ObjectMapper mapper = new ObjectMapper();
        goldenVectors = mapper.readValue(
                new File(GOLDEN_VECTORS_PATH),
                new TypeReference<List<Map<String, Object>>>() {}
        );

        assertFalse(goldenVectors.isEmpty(), "Golden vector file must not be empty");
    }

    @Test
    @DisplayName("All Java raw scores must match Python expected scores within epsilon")
    void allScoresMustMatchWithinEpsilon() throws Exception {
        int vectorCount = goldenVectors.size();
        assertTrue(vectorCount > 0, "Must have at least one golden vector");

        for (int i = 0; i < vectorCount; i++) {
            Map<String, Object> entry = goldenVectors.get(i);

            // Parse input feature vector
            @SuppressWarnings("unchecked")
            List<Number> inputList = (List<Number>) entry.get("input");
            float[] features = new float[inputList.size()];
            for (int j = 0; j < inputList.size(); j++) {
                features[j] = inputList.get(j).floatValue();
            }

            // Parse expected score from Python
            double expectedRawScore = ((Number) entry.get("expectedRawScore")).doubleValue();

            // Run Java inference
            float javaRawScore = scoringService.score(features);

            // Assert parity within epsilon
            double delta = Math.abs(javaRawScore - expectedRawScore);
            assertTrue(
                    delta <= PARITY_EPSILON,
                    String.format(
                            "Parity failure at vector %d: Java=%.8f, Python=%.8f, delta=%.2e, epsilon=%.2e",
                            i, javaRawScore, expectedRawScore, delta, PARITY_EPSILON
                    )
            );
        }
    }

    @Test
    @DisplayName("Golden vector file must contain at least 10 vectors")
    void goldenVectorFileMustHaveSufficientCoverage() {
        assertTrue(goldenVectors.size() >= 10,
                "Golden vector file should have at least 10 vectors for meaningful parity coverage, found: "
                        + goldenVectors.size());
    }

    @Test
    @DisplayName("Each golden vector must have 6 features matching the feature schema")
    void eachVectorMustHaveSixFeatures() {
        for (int i = 0; i < goldenVectors.size(); i++) {
            @SuppressWarnings("unchecked")
            List<Number> inputList = (List<Number>) goldenVectors.get(i).get("input");
            assertEquals(6, inputList.size(),
                    "Vector " + i + " must have 6 features (price z-score, volume ratio, volatility, "
                            + "bid-ask spread, time-of-day encoding, direction streak)");
        }
    }
}
