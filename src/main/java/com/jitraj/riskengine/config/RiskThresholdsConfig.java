package com.jitraj.riskengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized configuration for risk score thresholds.
 */
@Configuration
@ConfigurationProperties(prefix = "risk.thresholds")
public class RiskThresholdsConfig {

    private double elevated = 50.0;
    private double critical = 85.0;

    public double getElevated() {
        return elevated;
    }

    public void setElevated(double elevated) {
        this.elevated = elevated;
    }

    public double getCritical() {
        return critical;
    }

    public void setCritical(double critical) {
        this.critical = critical;
    }
}
