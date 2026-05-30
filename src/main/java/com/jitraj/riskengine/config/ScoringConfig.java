package com.jitraj.riskengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized configuration for ONNX model scoring.
 */
@Configuration
@ConfigurationProperties(prefix = "scoring")
public class ScoringConfig {

    private ModelConfig model = new ModelConfig();
    private ThreadPoolConfig threadPool = new ThreadPoolConfig();

    public ModelConfig getModel() {
        return model;
    }

    public void setModel(ModelConfig model) {
        this.model = model;
    }

    public ThreadPoolConfig getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ThreadPoolConfig threadPool) {
        this.threadPool = threadPool;
    }

    public static class ModelConfig {
        private String path = "model/risk_model.onnx";
        private String version = "1.0.0";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    public static class ThreadPoolConfig {
        private int size = 4;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }
}
