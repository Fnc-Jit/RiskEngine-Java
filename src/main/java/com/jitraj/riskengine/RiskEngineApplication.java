package com.jitraj.riskengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RiskEngine-Java — Real-time financial risk scoring pipeline.
 *
 * Ingests synthetic market events from Apache Kafka, transforms each event
 * into a numeric feature vector, runs in-process anomaly inference using
 * ONNX Runtime, persists scored risk signals to TimescaleDB, and exposes
 * results through Spring Boot REST endpoints and a live web dashboard.
 */
@SpringBootApplication
@EnableScheduling
public class RiskEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskEngineApplication.class, args);
    }
}
