package com.jitraj.riskengine.service;

import com.jitraj.riskengine.dto.MarketEvent;
import com.jitraj.riskengine.metrics.MetricsService;
import com.jitraj.riskengine.model.RiskSignal;
import com.jitraj.riskengine.repository.SignalRepository;
import com.jitraj.riskengine.scoring.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full event processing pipeline:
 * 1. Feature extraction
 * 2. Cold-start detection
 * 3. ONNX inference
 * 4. Risk signal mapping
 * 5. Persistence
 * 6. Metrics update
 *
 * Delegates to FeatureExtractor, ScoringService, RiskSignalMapper, and SignalRepository.
 * No business logic resides in controllers.
 */
@Service
public class EventProcessingService {

    private static final Logger log = LoggerFactory.getLogger(EventProcessingService.class);

    private final FeatureExtractor featureExtractor;
    private final ScoringService scoringService;
    private final RiskSignalMapper riskSignalMapper;
    private final SignalRepository signalRepository;
    private final MetricsService metricsService;

    public EventProcessingService(FeatureExtractor featureExtractor,
                                   ScoringService scoringService,
                                   RiskSignalMapper riskSignalMapper,
                                   SignalRepository signalRepository,
                                   MetricsService metricsService) {
        this.featureExtractor = featureExtractor;
        this.scoringService = scoringService;
        this.riskSignalMapper = riskSignalMapper;
        this.signalRepository = signalRepository;
        this.metricsService = metricsService;
    }

    /**
     * Processes a single market event through the full pipeline.
     *
     * @param event the validated market event
     * @return the resulting RiskSignal (never null)
     */
    public RiskSignal processEvent(MarketEvent event) {
        metricsService.incrementConsumed();

        // 1. Check cold-start
        if (featureExtractor.isColdStart(event.ticker())) {
            // Still need to update the rolling windows
            featureExtractor.extract(event);
            metricsService.incrementWarmingUp();

            RiskSignal signal = riskSignalMapper.mapWarmingUp(
                    event.eventId(), event.timestamp(), event.ticker());
            signalRepository.save(signal);
            return signal;
        }

        // 2. Extract features
        float[] features = featureExtractor.extract(event);
        if (features == null) {
            metricsService.incrementFailed();
            metricsService.incrementFeaturesInvalid();
            log.warn("Invalid feature values for eventId={}", event.eventId());

            RiskSignal signal = riskSignalMapper.mapFailed(
                    event.eventId(), event.timestamp(), event.ticker());
            signalRepository.save(signal);
            return signal;
        }

        // 3. Run ONNX inference
        try {
            long startNanos = System.nanoTime();
            float rawScore = scoringService.score(features);
            double latencyMs = (System.nanoTime() - startNanos) / 1_000_000.0;

            metricsService.recordLatency(latencyMs);
            metricsService.incrementScored();

            // 4. Map to risk signal
            RiskSignal signal = riskSignalMapper.mapScored(
                    event.eventId(), event.timestamp(), event.ticker(),
                    rawScore, latencyMs);

            // 5. Persist
            signalRepository.save(signal);
            return signal;

        } catch (Exception e) {
            log.error("Inference failed for eventId={}", event.eventId(), e);
            metricsService.incrementFailed();

            RiskSignal signal = riskSignalMapper.mapFailed(
                    event.eventId(), event.timestamp(), event.ticker());
            signalRepository.save(signal);
            return signal;
        }
    }
}
