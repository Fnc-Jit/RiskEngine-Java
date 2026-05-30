package com.jitraj.riskengine.model;

/**
 * Risk label classification.
 * NORMAL:   riskScore < elevated threshold (default 50)
 * ELEVATED: elevated <= riskScore < critical threshold (default 85)
 * CRITICAL: riskScore >= critical threshold
 */
public enum RiskLabel {
    NORMAL,
    ELEVATED,
    CRITICAL
}
