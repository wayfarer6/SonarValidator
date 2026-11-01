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
    private record ActionReservation(String id, long until) {}
    private final Map<String, ActionReservation> activeActions = new ConcurrentHashMap<>();
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
        log.debug("terminal agent connected: {}", agentId);
        return true;
    }

    public boolean openBrowser(String agentId, WebSocketSession session) {
        final WebSocketSession agent = agents.get(agentId);
        if (agent == null || !agent.isOpen()) {
            if (agent != null) {
                agents.remove(agentId, agent);
            }
            log.warn("terminal browser rejected: no open agent session for {}", agentId);
            return false;
        }
        synchronized (agent) {
        if (hasActiveAction(agentId)) return false;
        final WebSocketSession previous = browsers.putIfAbsent(agentId, session);
        if (previous != null) {
            if (previous.isOpen()) {
                log.warn("terminal browser rejected: browser session already active for {}", agentId);
                return false;
            }
            if (!browsers.replace(agentId, previous, session)) {
                return false;
            }
        }
        send(session, Map.of("type", "terminal-status", "status", "connecting"));
        if (sendAgent(agentId, Map.of("type", "terminal-open"))) {
            log.info("터미널 열기: 노드={}", agentId);
            return true;
        }
        browsers.remove(agentId, session);
        log.warn("terminal browser rejected: could not send terminal-open to agent {}", agentId);
        return false;
        }
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
            log.info("터미널 닫기: 노드={}", agentId);
            sendAgent(agentId, Map.of("type", "terminal-close"));
        }
    }

    public String browserRejectionReason(String agentId) {
        final WebSocketSession agent = agents.get(agentId);
        if (agent == null || !agent.isOpen()) return "Agent 터미널 채널에 연결되지 않았습니다. Prober 상태를 확인하세요.";
        final WebSocketSession browser = browsers.get(agentId);
        if (browser != null && browser.isOpen()) return "이 장비의 터미널이 다른 브라우저 세션에서 사용 중입니다. 기존 연결을 닫고 다시 연결하세요.";
        return "터미널 연결을 열지 못했습니다. 잠시 후 다시 연결하세요.";
    }

    public void rejectBrowser(String agentId, WebSocketSession session) {
        browsers.remove(agentId, session);
    }

    public void agentClosed(String agentId, WebSocketSession session) {
        if (agents.remove(agentId, session)) {
            fromAgent(agentId, Map.of("type", "terminal-error",
                    "message", "Agent terminal connection closed"));
        }
    }

    private boolean hasActiveAction(String agentId) {
        var reservation = activeActions.get(agentId);
        if (reservation != null && reservation.until() < System.currentTimeMillis()) {
            activeActions.remove(agentId, reservation); return false;
        }
        return reservation != null;
    }
    public WebSocketSession actionSession(String agentId) {
        WebSocketSession session = agents.get(agentId);
        return session != null && session.isOpen() ? session : null;
    }
    public boolean isCurrentAgent(String agentId, WebSocketSession session) {
        return actionSession(agentId) == session;
    }
    public boolean supportsAction(String agentId, String operation) {
        WebSocketSession session = actionSession(agentId);
        return session != null && session.getAttributes().get("adviceOperations") instanceof java.util.Set<?> values && values.contains(operation);
    }
    public boolean reserveAction(String agentId, WebSocketSession expected, String executionId) {
        synchronized (expected) {
            WebSocketSession browser = browsers.get(agentId);
            return isCurrentAgent(agentId, expected) && (browser == null || !browser.isOpen())
                    && !hasActiveAction(agentId)
                    && activeActions.putIfAbsent(agentId, new ActionReservation(executionId, System.currentTimeMillis()+120_000)) == null;
        }
    }
    public void releaseAction(String agentId, String executionId) {
        activeActions.computeIfPresent(agentId, (key, value) -> value.id().equals(executionId) ? null : value);
    }
    public boolean sendAction(String agentId, WebSocketSession expected, String executionId, Map<String, Object> message) {
        synchronized (expected) {
            var reservation = activeActions.get(agentId);
            return isCurrentAgent(agentId, expected) && reservation != null && executionId.equals(reservation.id()) && send(expected, message);
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
