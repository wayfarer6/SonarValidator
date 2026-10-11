package org.sonar.sonarvalidator_backend.Config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Service.AgentTerminalBroker;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TerminalBrowserWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalBrowserWebSocketHandler.class);
    private static final int MAX_INPUT_CHARS = 8_192;

    private final ObjectMapper objectMapper;
    private final AgentTerminalBroker broker;

    public TerminalBrowserWebSocketHandler(ObjectMapper objectMapper, AgentTerminalBroker broker) {
        this.objectMapper = objectMapper;
        this.broker = broker;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        final String agentId = (String) session.getAttributes().get("agentId");
        if (!broker.openBrowser(agentId, session)) {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        Map.of("type", "terminal-error",
                                "message", "Agent terminal is offline or not configured with the shared secret"))));
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason(
                        "Agent terminal is offline or already in use"));
            } catch (java.io.IOException | RuntimeException ex) {
                log.warn("could not reject browser terminal session {}: {}", session.getId(), ex.getMessage());
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        final JsonNode json = objectMapper.readTree(message.getPayload());
        final String type = json.path("type").asString("");
        final String agentId = (String) session.getAttributes().get("agentId");
        if ("input".equals(type)) {
            final String data = json.path("data").asString("");
            if (data.length() > MAX_INPUT_CHARS) {
                session.close(CloseStatus.TOO_BIG_TO_PROCESS.withReason("terminal input too large"));
                return;
            }
            broker.fromBrowser(agentId, Map.of("type", "terminal-input", "data", data));
        } else if ("resize".equals(type)) {
            final int cols = Math.max(20, Math.min(300, json.path("cols").asInt(80)));
            final int rows = Math.max(5, Math.min(100, json.path("rows").asInt(24)));
            broker.fromBrowser(agentId, Map.of("type", "terminal-resize", "cols", cols, "rows", rows));
        } else {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unsupported terminal input"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        final String agentId = (String) session.getAttributes().get("agentId");
        if (agentId != null) {
            broker.browserClosed(agentId, session);
        }
    }
}
