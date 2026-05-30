package com.jitraj.riskengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitraj.riskengine.dto.MetricsResponse;
import com.jitraj.riskengine.dto.RiskSignalDto;
import com.jitraj.riskengine.metrics.MetricsService;
import com.jitraj.riskengine.model.RiskSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler that streams live metrics and risk signals to connected dashboard clients.
 */
@Component
public class DashboardWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DashboardWebSocketHandler.class);

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    @Value("${dashboard.metrics.push-interval-ms:1000}")
    private long pushIntervalMs;

    public DashboardWebSocketHandler(ObjectMapper objectMapper, MetricsService metricsService) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("Dashboard client connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("Dashboard client disconnected: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        log.warn("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    /**
     * Pushes a metrics snapshot to all connected clients at configured interval.
     */
    @Scheduled(fixedDelayString = "${dashboard.metrics.push-interval-ms:1000}")
    public void pushMetrics() {
        if (sessions.isEmpty()) return;

        try {
            MetricsResponse metrics = metricsService.snapshot();
            String json = objectMapper.writeValueAsString(Map.of("type", "metrics", "data", metrics));
            broadcast(json);
        } catch (Exception e) {
            log.warn("Failed to push metrics to dashboard clients", e);
        }
    }

    /**
     * Pushes a scored risk signal to all connected clients.
     */
    public void pushSignal(RiskSignal signal) {
        if (sessions.isEmpty()) return;

        try {
            RiskSignalDto dto = new RiskSignalDto(
                    signal.getEventId(), signal.getTime(), signal.getTicker(),
                    signal.getRawScore(), signal.getRiskScore(), signal.getLabel(),
                    signal.getLatencyMs(), signal.getStatus());
            String json = objectMapper.writeValueAsString(Map.of("type", "signal", "data", dto));
            broadcast(json);
        } catch (Exception e) {
            log.warn("Failed to push signal to dashboard clients", e);
        }
    }

    private void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        sessions.forEach((id, session) -> {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to send to session {}, removing", id);
                sessions.remove(id);
            }
        });
    }
}
