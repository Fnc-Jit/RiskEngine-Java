package com.jitraj.riskengine.model;

import java.time.Instant;

/**
 * Persistence entity for the risk_signals hypertable.
 * This class carries JPA/JDBC persistence concerns and is NOT used in API responses.
 */
public class RiskSignal {

    private String eventId;
    private Instant time;
    private String ticker;
    private Double rawScore;
    private Double riskScore;
    private String label;
    private Double latencyMs;
    private String status;

    public RiskSignal() {
    }

    public RiskSignal(String eventId, Instant time, String ticker, Double rawScore,
                      Double riskScore, String label, Double latencyMs, String status) {
        this.eventId = eventId;
        this.time = time;
        this.ticker = ticker;
        this.rawScore = rawScore;
        this.riskScore = riskScore;
        this.label = label;
        this.latencyMs = latencyMs;
        this.status = status;
    }

    // --- Getters and Setters ---

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public Double getRawScore() {
        return rawScore;
    }

    public void setRawScore(Double rawScore) {
        this.rawScore = rawScore;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Double getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Double latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "RiskSignal{" +
                "eventId='" + eventId + '\'' +
                ", time=" + time +
                ", ticker='" + ticker + '\'' +
                ", rawScore=" + rawScore +
                ", riskScore=" + riskScore +
                ", label='" + label + '\'' +
                ", latencyMs=" + latencyMs +
                ", status='" + status + '\'' +
                '}';
    }
}
