package com.jitraj.riskengine.scoring;

import com.jitraj.riskengine.config.ScoringConfig;
import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.util.Collections;

/**
 * Owns the ONNX session lifecycle and executes inference per feature vector.
 * The model is loaded exactly once at startup. If loading fails, the
 * application terminates (fail-fast).
 */
@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final ScoringConfig scoringConfig;
    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private boolean modelLoaded = false;

    public ScoringService(ScoringConfig scoringConfig) {
        this.scoringConfig = scoringConfig;
    }

    @PostConstruct
    public void init() {
        String modelPath = scoringConfig.getModel().getPath();
        String modelVersion = scoringConfig.getModel().getVersion();
        log.info("Loading ONNX model — path: {}, version: {}", modelPath, modelVersion);

        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

            session = env.createSession(modelPath, opts);
            inputName = session.getInputNames().iterator().next();
            modelLoaded = true;

            log.info("ONNX model loaded successfully — input name: {}, version: {}", inputName, modelVersion);
        } catch (OrtException e) {
            log.error("Failed to load ONNX model from path: {} — application will terminate", modelPath, e);
            throw new RuntimeException("ONNX model load failure — cannot start RiskEngine", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
            log.info("ONNX session closed");
        } catch (OrtException e) {
            log.warn("Error closing ONNX session", e);
        }
    }

    /**
     * @return true if the ONNX model was loaded successfully
     */
    public boolean isModelLoaded() {
        return modelLoaded;
    }

    /**
     * @return the configured model version string
     */
    public String getModelVersion() {
        return scoringConfig.getModel().getVersion();
    }

    /**
     * Runs inference on a single feature vector.
     *
     * @param features a float array representing the feature vector
     * @return the raw anomaly score from the model
     * @throws OrtException if inference fails
     */
    public float score(float[] features) throws OrtException {
        long[] shape = new long[]{1, features.length};
        FloatBuffer buffer = FloatBuffer.wrap(features);
        OnnxTensor tensor = OnnxTensor.createTensor(env, buffer, shape);

        try (OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            // Isolation Forest typically outputs a 2D array or a decision function value
            Object output = result.get(0).getValue();
            if (output instanceof float[][] scores) {
                return scores[0][0];
            } else if (output instanceof float[] scores) {
                return scores[0];
            } else if (output instanceof long[][] labels) {
                // Some sklearn models output labels; use the decision function instead
                if (result.size() > 1) {
                    Object decisionOutput = result.get(1).getValue();
                    if (decisionOutput instanceof float[][] decisionScores) {
                        return decisionScores[0][0];
                    }
                }
                return labels[0][0];
            }
            throw new OrtException("Unexpected ONNX output type: " + output.getClass().getName());
        } finally {
            tensor.close();
        }
    }
}
