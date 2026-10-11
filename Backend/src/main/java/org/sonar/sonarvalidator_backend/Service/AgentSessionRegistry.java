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

/**
 * 살아 있는 Agent WebSocket 세션을 보관하고, 서버→Agent 푸시를 담당합니다.
 *
 * <h2>책임</h2>
 * <ul>
 *   <li>{@code agent_id} → {@link WebSocketSession} 매핑 유지/조회</li>
 *   <li>특정 Agent 1명에게 푸시, 또는 연결된 모든 Agent 에게 브로드캐스트</li>
 *   <li>전송 실패 시 세션을 정리(죽은 세션 누수 방지)</li>
 * </ul>
 *
 * <h2>동시성</h2>
 * <p>Spring  {@link WebSocketSession} 은 스레드 안전하지 않습니다. 정책 스케줄러 같은
 * 별도 스레드가 동시에 {@code sendMessage} 를 호출하면 프레임이 깨질 수 있으므로
 * 세션별 락({@link SessionHolder#lock})으로 전송을 직렬화합니다.
 * (Spring 의 {@code ConcurrentWebSocketSessionDecorator}  같은 목적이지만
 * 버퍼 크기/시간 제한이 필요 없는 단순 구조라 직접 구현했습니다.)
 */
@Service
public class AgentSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionRegistry.class);

    /** 세션 1개를 락과 함께 감는 내부 더. */
    private static final class SessionHolder {
        private final WebSocketSession session;
        private final Object lock = new Object();

        private SessionHolder(WebSocketSession session) {
            this.session = session;
        }
    }

    /** agent_id → 세션. 재접속 시 새 세션으로 교체됩니다. */
    private final Map<String, SessionHolder> sessions = new ConcurrentHashMap<>();

    /** 세션 ID → agent_id. hello 없이 끊긴 세션도 정리할 수 있게 하기 위함입니다. */
    private final Map<String, String> sessionToAgent = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public AgentSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Agent 세션을 등록합니다. 같은 {@code agent_id} 로 재접속하면 기존 세션을 대체합니다.
     *
     * @param agentId Agent 식별자 (비어 있으면 세션 ID 를 임시 키로 사용)
     * @param session 연결된 WebSocket 세션
     */
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

    /**
     * 세션이 닫 때 매핑을 제거합니다.
     *
     * @param session 닫힌 세션
     */
    public void unregister(WebSocketSession session) {
        final String key = sessionToAgent.remove(session.getId());
        if (key == null) {
            return;
        }
        // 재접속으로 이미 새 세션으로 교체된 경우에는 제거하지 않습니다.
        sessions.computeIfPresent(key, (k, holder) -> holder.session == session ? null : holder);
        log.info("agent {} unregistered (session={}, total={})", key, session.getId(), sessions.size());
    }

    /**
     * 특정 Agent 에게 봉투를 보냅니다.
     *
     * @param agentId 대상 Agent 식별자
     * @param envelope 보낼 메시지
     * @return 전송 성공 여부 (Agent 가 연결되어 있지 않으면 false)
     */
    public boolean sendTo(String agentId, Envelope envelope) {
        final SessionHolder holder = sessions.get(agentId);
        if (holder == null) {
            log.warn("push dropped: agent {} is not connected", agentId);
            return false;
        }
        return send(holder, envelope);
    }

    /**
     * 연결된 모든 Agent 에게 봉투를 브로드캐스트합니다.
     *
     * @param envelope 보낼 메시지
     * @return 전송에 성공한 세션 수
     */
    public int broadcast(Envelope envelope) {
        int sent = 0;
        for (SessionHolder holder : sessions.values()) {
            if (send(holder, envelope)) {
                sent++;
            }
        }
        return sent;
    }

    /**
     * 연결된 Agent 수를 반환합니다.
     *
     * @return 등록된 세션 수
     */
    public int connectedCount() {
        return sessions.size();
    }

    /**
     * 현재 연결된 Agent 식별자 목록입니다.
     *
     * @return agent_id 스냅샷
     */
    public Collection<String> connectedAgentIds() {
        return java.util.List.copyOf(sessions.keySet());
    }

    /**
     * 실제 전송. 세션별 락으로 직렬화하고 실패 시 세션을 정리합니다.
     *
     * @param holder 대상 홀더
     * @param envelope 보낼 메시지
     * @return 성공 여부
     */
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

    /**
     * 예외를 무시하고 세션을 닫습니다. (정리 경로에서 사용)
     *
     * @param session 을 세션
     */
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