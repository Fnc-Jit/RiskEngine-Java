package com.jitraj.riskengine.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitraj.riskengine.dto.MarketEvent;
import com.jitraj.riskengine.metrics.MetricsService;
import com.jitraj.riskengine.service.EventProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Spring Kafka listener that subscribes to the market-events topic,
 * deserializes each record into a MarketEvent, validates it,
 * and dispatches to the processing pipeline.
 *
 * Malformed or invalid events are counted and skipped without halting consumption.
 */
@Component
public class MarketEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MarketEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final EventProcessingService eventProcessingService;
    private final MetricsService metricsService;

    public MarketEventConsumer(ObjectMapper objectMapper,
                                EventProcessingService eventProcessingService,
                                MetricsService metricsService) {
        this.objectMapper = objectMapper;
        this.eventProcessingService = eventProcessingService;
        this.metricsService = metricsService;
    }

    @KafkaListener(
            topics = "${kafka.topic.market-events:market-events}",
            groupId = "${kafka.consumer.group-id:risk-engine-group}"
    )
    public void consume(String message) {
        MarketEvent event;

        // 1. Deserialize
        try {
            event = objectMapper.readValue(message, MarketEvent.class);
        } catch (Exception e) {
            metricsService.incrementMalformed();
            log.warn("Malformed JSON payload — skipping record: {}", truncate(message, 200), e);
            return;
        }

        // 2. Validate required fields
        if (!event.isValid()) {
            metricsService.incrementInvalid();
            log.warn("Invalid MarketEvent — missing required fields. eventId={}", event.eventId());
            return;
        }

        // 3. Process
        try {
            eventProcessingService.processEvent(event);
        } catch (Exception e) {
            log.error("Unexpected error processing eventId={}", event.eventId(), e);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
