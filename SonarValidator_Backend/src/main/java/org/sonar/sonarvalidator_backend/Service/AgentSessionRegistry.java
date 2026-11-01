package org.sonar.sonarvalidator_backend.Service;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;

@Service
public class AgentSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionRegistry.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final Map<String, String> sessionToAgent = new ConcurrentHashMap<>();

    private final Map<String, WebSocketSession> telemetrySessions = new ConcurrentHashMap<>();
    private final Map<String, String> telemetrySessionToAgent = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final java.time.Clock clock;
    private final Map<String, Long> lastManagementActivity = new ConcurrentHashMap<>();
    private static final long MANAGEMENT_IDLE_MILLIS = 30_000;


    @org.springframework.beans.factory.annotation.Autowired
    public AgentSessionRegistry(ObjectMapper objectMapper) {
        this(objectMapper, java.time.Clock.systemUTC());
    }

    public AgentSessionRegistry(ObjectMapper objectMapper, java.time.Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void register(String agentId, WebSocketSession session) {
        final String key = (agentId == null || agentId.isBlank())
                ? "session:" + session.getId()
                : agentId;

        lastManagementActivity.put(session.getId(), clock.millis());
        final WebSocketSession previous = sessions.put(key, session);
        sessionToAgent.put(session.getId(), key);

        if (previous != null && previous != session) {
            log.info("장비 관리 연결 복구: 노드={}", key);
            lastManagementActivity.remove(previous.getId());
            closeQuietly(previous);
        }
        if (previous == null) log.info("장비 관리 연결: 노드={}", key);
    }

    public void registerTelemetry(String agentId, WebSocketSession session) {
        final WebSocketSession previous = telemetrySessions.put(agentId, session);
        telemetrySessionToAgent.put(session.getId(), agentId);
        if (previous != null && previous != session) closeQuietly(previous);
    }

    public WebSocketSession telemetrySession(String agentId) {
        final WebSocketSession session = telemetrySessions.get(agentId);
        return session != null && session.isOpen() ? session : null;
    }

    public boolean sendToTelemetry(String agentId, Envelope envelope) {
        final WebSocketSession session = telemetrySession(agentId);
        return session != null && send(session, envelope);
    }

    public void unregister(WebSocketSession session) {
        lastManagementActivity.remove(session.getId());
        final String telemetryAgent = telemetrySessionToAgent.remove(session.getId());
        if (telemetryAgent != null) telemetrySessions.remove(telemetryAgent, session);
        final String key = sessionToAgent.remove(session.getId());
        if (key == null) {
            return;
        }
        // 재접속으로 이미 새 세션으로 교체된 경우에는 제거하지 않습니다.
        if (sessions.remove(key, session)) log.info("장비 관리 연결 종료: 노드={}", key);
    }

    public boolean sendTo(String agentId, Envelope envelope) {
        final WebSocketSession session = sessions.get(agentId);
        if (session == null || !recent(session)) {
            log.warn("push dropped: agent {} is not connected", agentId);
            return false;
        }
        return send(session, envelope);
    }

    public int broadcast(Envelope envelope) {
        int sent = 0;
        for (WebSocketSession session : sessions.values()) {
            if (send(session, envelope)) {
                sent++;
            }
        }
        return sent;
    }

    public int connectedCount() {
        return connectedAgentIds().size();
    }

    public Collection<String> connectedAgentIds() {
        return sessions.entrySet().stream().filter(entry -> recent(entry.getValue()))
                .map(Map.Entry::getKey).toList();
    }

    private boolean recent(WebSocketSession session) {
        Long seen = lastManagementActivity.get(session.getId());
        return session.isOpen() && seen != null && clock.millis() - seen <= MANAGEMENT_IDLE_MILLIS;
    }

    public boolean send(WebSocketSession session, Envelope envelope) {
        try {
            final String json = objectMapper.writeValueAsString(envelope);
            // Replies and pushes must use the same session lock.
            synchronized (session) {
                final Object wrapped = session.getAttributes().get("wrapped");
                final WebSocketSession target = wrapped instanceof WebSocketSession decorated ? decorated : session;
                if (!target.isOpen()) return false;
                target.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            log.warn("push failed for session {}: {}", session.getId(), ex.getMessage());
            closeQuietly(session);
            unregister(session);
            return false;
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException ex) {
            log.debug("ignoring close failure for {}: {}", session.getId(), ex.getMessage());
        }
    }
}
