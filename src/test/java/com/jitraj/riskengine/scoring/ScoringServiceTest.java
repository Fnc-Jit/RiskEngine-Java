package com.jitraj.riskengine.scoring;

import ai.onnxruntime.OrtException;
import com.jitraj.riskengine.config.ScoringConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mockito-based unit tests for the ScoringService.
 * Tests behavior on success, failure, and model status.
 */
@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Test
    @DisplayName("Should report model not loaded before initialization")
    void shouldReportNotLoadedBeforeInit() {
        ScoringConfig config = new ScoringConfig();
        ScoringConfig.ModelConfig modelConfig = new ScoringConfig.ModelConfig();
        modelConfig.setPath("nonexistent/model.onnx");
        config.setModel(modelConfig);

        ScoringService service = new ScoringService(config);

        assertFalse(service.isModelLoaded());
    }

    @Test
    @DisplayName("Should fail fast if model path is invalid")
    void shouldFailFastOnInvalidModel() {
        ScoringConfig config = new ScoringConfig();
        ScoringConfig.ModelConfig modelConfig = new ScoringConfig.ModelConfig();
        modelConfig.setPath("/invalid/path/to/model.onnx");
        config.setModel(modelConfig);

        ScoringService service = new ScoringService(config);

        assertThrows(RuntimeException.class, service::init);
    }

    @Test
    @DisplayName("Should expose model version from config")
    void shouldExposeModelVersion() {
        ScoringConfig config = new ScoringConfig();
        ScoringConfig.ModelConfig modelConfig = new ScoringConfig.ModelConfig();
        modelConfig.setVersion("2.0.0");
        config.setModel(modelConfig);

        ScoringService service = new ScoringService(config);

        assertEquals("2.0.0", service.getModelVersion());
    }
}
