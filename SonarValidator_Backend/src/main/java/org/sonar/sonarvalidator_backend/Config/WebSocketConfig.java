package org.sonar.sonarvalidator_backend.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    public static final String MANAGEMENT_PATH = "/api/v1/management";

    public static final String TELEMETRY_PATH = "/api/v1/telemetry";

    private static final int MAX_MESSAGE_BUFFER_SIZE = 1024 * 1024;

    private final AgentWebSocketHandler agentWebSocketHandler;
    private final TerminalAgentWebSocketHandler terminalAgentHandler;
    private final TerminalBrowserWebSocketHandler terminalBrowserHandler;
    private final TerminalBrowserHandshakeInterceptor terminalBrowserHandshakeInterceptor;

    public WebSocketConfig(AgentWebSocketHandler agentWebSocketHandler,
                           TerminalAgentWebSocketHandler terminalAgentHandler,
                           TerminalBrowserWebSocketHandler terminalBrowserHandler,
                           TerminalBrowserHandshakeInterceptor terminalBrowserHandshakeInterceptor) {
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.terminalAgentHandler = terminalAgentHandler;
        this.terminalBrowserHandler = terminalBrowserHandler;
        this.terminalBrowserHandshakeInterceptor = terminalBrowserHandshakeInterceptor;
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        final ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_MESSAGE_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_MESSAGE_BUFFER_SIZE);
        // 유휴 연결이 정책 푸시를 놓치지 않도록 넘념히 둡니다.
        container.setMaxSessionIdleTimeout(0L);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, MANAGEMENT_PATH, TELEMETRY_PATH)
                .setAllowedOriginPatterns("*");
        registry.addHandler(terminalAgentHandler, "/api/v1/terminal/agent")
                .setAllowedOriginPatterns("*");
        registry.addHandler(terminalBrowserHandler, "/api/v1/terminal/browser")
                .addInterceptors(terminalBrowserHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
