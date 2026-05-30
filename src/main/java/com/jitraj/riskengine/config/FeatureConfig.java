package com.jitraj.riskengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized configuration for feature extraction.
 */
@Configuration
@ConfigurationProperties(prefix = "feature")
public class FeatureConfig {

    private WindowConfig window = new WindowConfig();
    private SchemaConfig schema = new SchemaConfig();

    public WindowConfig getWindow() {
        return window;
    }

    public void setWindow(WindowConfig window) {
        this.window = window;
    }

    public SchemaConfig getSchema() {
        return schema;
    }

    public void setSchema(SchemaConfig schema) {
        this.schema = schema;
    }

    public static class WindowConfig {
        private int minSamples = 10;
        private int size = 50;

        public int getMinSamples() {
            return minSamples;
        }

        public void setMinSamples(int minSamples) {
            this.minSamples = minSamples;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }

    public static class SchemaConfig {
        private String version = "1.0.0";

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
