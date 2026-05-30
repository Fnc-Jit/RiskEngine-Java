package com.jitraj.riskengine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.jitraj.riskengine.service.DashboardWebSocketHandler;

/**
 * WebSocket configuration for the live dashboard.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Value("${dashboard.websocket.path:/ws/dashboard}")
    private String wsPath;

    private final DashboardWebSocketHandler dashboardHandler;

    public WebSocketConfig(DashboardWebSocketHandler dashboardHandler) {
        this.dashboardHandler = dashboardHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(dashboardHandler, wsPath)
                .setAllowedOrigins("*");
    }
}
