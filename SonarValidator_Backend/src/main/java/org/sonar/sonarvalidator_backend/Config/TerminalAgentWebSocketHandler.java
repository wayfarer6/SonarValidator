package org.sonar.sonarvalidator_backend.Config;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Service.AgentTerminalBroker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TerminalAgentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalAgentWebSocketHandler.class);
    private final ObjectMapper objectMapper;
    private final ExpectedAgentRepository expectedAgents;
    private final AgentTerminalBroker broker;
    private final String sharedSecret;

    public TerminalAgentWebSocketHandler(ObjectMapper objectMapper,
                                         ExpectedAgentRepository expectedAgents,
                                         AgentTerminalBroker broker,
                                         @Value("${sonar.terminal.shared-secret:}") String sharedSecret) {
        this.objectMapper = objectMapper;
        this.expectedAgents = expectedAgents;
        this.broker = broker;
        this.sharedSecret = sharedSecret;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        final JsonNode json = objectMapper.readTree(message.getPayload());
        final String type = json.path("type").asString("");
        if ("terminal-hello".equals(type)) {
            final String agentId = json.path("agent_id").asString("");
            final String suppliedSecret = json.path("secret").asString("");
            if (sharedSecret.getBytes(StandardCharsets.UTF_8).length < 32 || !MessageDigest.isEqual(
                    sharedSecret.getBytes(StandardCharsets.UTF_8),
                    suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Terminal channel is not authorized"));
                return;
            }
            final boolean registered = expectedAgents.findByAgentId(agentId)
                    .filter(agent -> agent.getProjectKey() != null && !agent.getProjectKey().isBlank())
                    .isPresent();
            if (!registered) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Agent is not registered to a project"));
                return;
            }
            session.getAttributes().put("agentId", agentId);
            broker.registerAgent(agentId, session);
            return;
        }

        final Object agentId = session.getAttributes().get("agentId");
        if (!(agentId instanceof String id)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("terminal-hello required"));
            return;
        }
        if (!"terminal-ready".equals(type) && !"terminal-output".equals(type)
                && !"terminal-error".equals(type) && !"terminal-exit".equals(type)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unsupported terminal message"));
            return;
        }
        final Map<String, Object> forwarded = switch (type) {
            case "terminal-ready" -> Map.of("type", type);
            case "terminal-output" -> Map.of("type", type,
                    "data_base64", json.path("data_base64").asString(""));
            case "terminal-error" -> Map.of("type", type,
                    "message", json.path("message").asString("Terminal agent error"));
            case "terminal-exit" -> Map.of("type", type);
            default -> throw new IllegalStateException("validated terminal message type changed");
        };
        broker.fromAgent(id, forwarded);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        final Object agentId = session.getAttributes().get("agentId");
        if (agentId instanceof String id) {
            broker.agentClosed(id, session);
        }
        log.info("terminal agent socket closed: session={} status={}", session.getId(), status);
    }
}
