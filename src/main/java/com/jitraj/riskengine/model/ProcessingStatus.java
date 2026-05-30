package com.jitraj.riskengine.model;

/**
 * Processing status for a risk signal.
 * SCORED:     Inference completed successfully.
 * FAILED:     Inference or processing failed for this event.
 * WARMING_UP: Per-ticker rolling window has insufficient samples.
 */
public enum ProcessingStatus {
    SCORED,
    FAILED,
    WARMING_UP
}
