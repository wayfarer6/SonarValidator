package org.sonar.sonarvalidator_backend.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;

@Service
public class AgentTerminalBroker {

    private static final Logger log = LoggerFactory.getLogger(AgentTerminalBroker.class);

    private final Map<String, WebSocketSession> agents = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> browsers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public AgentTerminalBroker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean registerAgent(String agentId, WebSocketSession session) {
        final WebSocketSession previous = agents.put(agentId, session);
        if (previous != null && previous != session && previous.isOpen()) {
            close(previous);
        }
        final WebSocketSession browser = browsers.get(agentId);
        if (browser != null) {
            sendAgent(agentId, Map.of("type", "terminal-open"));
        }
        log.info("terminal agent connected: {}", agentId);
        return true;
    }

    public boolean openBrowser(String agentId, WebSocketSession session) {
        if (!agents.containsKey(agentId) || browsers.putIfAbsent(agentId, session) != null) {
            return false;
        }
        send(session, Map.of("type", "terminal-status", "status", "connecting"));
        if (sendAgent(agentId, Map.of("type", "terminal-open"))) {
            return true;
        }
        browsers.remove(agentId, session);
        return false;
    }

    public void fromBrowser(String agentId, Map<String, Object> message) {
        sendAgent(agentId, message);
    }

    public void fromAgent(String agentId, Map<String, Object> message) {
        final WebSocketSession browser = browsers.get(agentId);
        if (browser != null) {
            send(browser, message);
        }
    }

    public void browserClosed(String agentId, WebSocketSession session) {
        if (browsers.remove(agentId, session)) {
            sendAgent(agentId, Map.of("type", "terminal-close"));
        }
    }

    public void agentClosed(String agentId, WebSocketSession session) {
        if (agents.remove(agentId, session)) {
            fromAgent(agentId, Map.of("type", "terminal-error",
                    "message", "Agent terminal connection closed"));
        }
    }

    private boolean sendAgent(String agentId, Map<String, Object> message) {
        final WebSocketSession session = agents.get(agentId);
        return session != null && send(session, message);
    }

    private boolean send(WebSocketSession session, Map<String, Object> message) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            log.warn("terminal message send failed for session {}: {}",
                    session.getId(), ex.getMessage());
            close(session);
            return false;
        }
    }

    private void close(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException ex) {
            log.warn("failed to close terminal session {}: {}", session.getId(), ex.getMessage());
        }
    }
}
