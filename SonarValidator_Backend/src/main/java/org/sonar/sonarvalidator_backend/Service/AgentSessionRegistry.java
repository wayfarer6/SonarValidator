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

    private static final class SessionHolder {
        private final WebSocketSession session;
        private final Object lock = new Object();

        private SessionHolder(WebSocketSession session) {
            this.session = session;
        }
    }

    private final Map<String, SessionHolder> sessions = new ConcurrentHashMap<>();

    private final Map<String, String> sessionToAgent = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public AgentSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(String agentId, WebSocketSession session) {
        final String key = (agentId == null || agentId.isBlank())
                ? "session:" + session.getId()
                : agentId;

        final SessionHolder previous = sessions.put(key, new SessionHolder(session));
        sessionToAgent.put(session.getId(), key);

        if (previous != null && previous.session != session) {
            log.info("agent {} reconnected; replacing old session {}", key, previous.session.getId());
            closeQuietly(previous.session);
        }
        log.info("agent {} registered (session={}, total={})", key, session.getId(), sessions.size());
    }

    public void unregister(WebSocketSession session) {
        final String key = sessionToAgent.remove(session.getId());
        if (key == null) {
            return;
        }
        // 재접속으로 이미 새 세션으로 교체된 경우에는 제거하지 않습니다.
        sessions.computeIfPresent(key, (k, holder) -> holder.session == session ? null : holder);
        log.info("agent {} unregistered (session={}, total={})", key, session.getId(), sessions.size());
    }

    public boolean sendTo(String agentId, Envelope envelope) {
        final SessionHolder holder = sessions.get(agentId);
        if (holder == null) {
            log.warn("push dropped: agent {} is not connected", agentId);
            return false;
        }
        return send(holder, envelope);
    }

    public int broadcast(Envelope envelope) {
        int sent = 0;
        for (SessionHolder holder : sessions.values()) {
            if (send(holder, envelope)) {
                sent++;
            }
        }
        return sent;
    }

    public int connectedCount() {
        return sessions.size();
    }

    public Collection<String> connectedAgentIds() {
        return java.util.List.copyOf(sessions.keySet());
    }

    private boolean send(SessionHolder holder, Envelope envelope) {
        if (!holder.session.isOpen()) {
            log.warn("push dropped: session {} is closed", holder.session.getId());
            return false;
        }
        try {
            final String json = objectMapper.writeValueAsString(envelope);
            synchronized (holder.lock) {
                holder.session.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            log.warn("push failed for session {}: {}", holder.session.getId(), ex.getMessage());
            closeQuietly(holder.session);
            unregister(holder.session);
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
