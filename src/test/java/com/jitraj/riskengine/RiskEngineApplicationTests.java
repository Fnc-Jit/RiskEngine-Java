package com.jitraj.riskengine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import com.jitraj.riskengine.scoring.ScoringService;

/**
 * Verifies Spring application context loads successfully.
 * ScoringService is mocked since ONNX model file is not available in test.
 */
@SpringBootTest
@ActiveProfiles("test")
class RiskEngineApplicationTests {

    @MockBean
    private ScoringService scoringService;

    @Test
    void contextLoads() {
        // Verifies Spring application context loads successfully
    }
}
