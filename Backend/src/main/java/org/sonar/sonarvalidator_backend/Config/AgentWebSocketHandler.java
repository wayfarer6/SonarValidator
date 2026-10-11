package org.sonar.sonarvalidator_backend.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.dto.Envelope;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * Agent 전용 plain WebSocket 핸들러입니다. (STOMP 대체)
 *
 * <h2>수신 흐름</h2>
 * <ol>
 *   <li>텍스트 프레임을 {@link Envelope} 로 역직렬화</li>
 *   <li>{@link AgentMessageRouterService#handle} 로 분기</li>
 *   <li>응답 투가 있으면 같은 소켓으로 1건 전송</li>
 * </ol>
 *
 * <h2>파싱 실패 처리</h2>
 * <p>JSON 이 아니면 {@code error} 봉투로 응답합니다. 세션을 닫지 않는 이유는
 * Agent 가 재접속 비용을 치르지 않고도 형식 오류를 인지할 수 있게 하기 위함입니다.
 *
 * <h2>동시성</h2>
 * <p>세션을 {@link ConcurrentWebSocketSessionDecorator} 로 감싸 정책 푸시(서버
 * 발신)와 응답(수신 처리 중 발신)이 쳐도 프레임이 이지 않게 합니다.
 */
@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    /** 신 버퍼 상한(바이트). 넘으면 전송이 실패합니다. */
    private static final int SEND_BUFFER_LIMIT = 512 * 1024;

    /** 송신 1건의 최대 허용 시간(ms). 느린 Agent 가 서버 스레드를 붙잡지 않게 합니다. */
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    private final ObjectMapper objectMapper;
    private final AgentMessageRouterService router;
    private final AgentSessionRegistry registry;

    public AgentWebSocketHandler(ObjectMapper objectMapper,
                                 AgentMessageRouterService router,
                                 AgentSessionRegistry registry) {
        this.objectMapper = objectMapper;
        this.router = router;
        this.registry = registry;
    }

    /**
     * Agent 가 연결되면 데코레이터로 감싼 세션을 속성에 심어 둡니다.
     * 이후 모든 수신/발신이 같은 래퍼를 쓰도록 하기 위함입니다.
     *
     * @param session 원본 세션
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put("wrapped", toWrapped(session));
        // hello 를 보내기 전까지 agent_id 를 모르므로 여기서는 등록하지 않습니다.
        log.info("agent socket opened: session={} remote={}",
                session.getId(), session.getRemoteAddress());
    }

    /**
     * 스트 프레임 1건을 처리합니다.
     *
     * @param session 수신 세션
     * @param message 텍스트 프레임 (JSON 봉투 1건)
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        final String raw = message.getPayload();
        Envelope request;
        try {
            request = objectMapper.readValue(raw, Envelope.class);
        } catch (RuntimeException ex) {
            log.warn("invalid envelope from session {}: {}", session.getId(), ex.getMessage());
            send(session, Envelope.error(null, "invalid JSON envelope: " + ex.getMessage()));
            return;
        }

        if ("unknown".equals(request.getType())) {
            send(session, Envelope.error(request.getCorrelation_id(),
                    "missing or empty 'type' field"));
            return;
        }

        Envelope response;
        try {
            response = router.handle(session, request);
        } catch (RuntimeException ex) {
            // 단일 메시지 처리 실패가 소켓 전체를 죽이지 않도록 여기서 흡수합니다.
            log.error("handler failure for type={} session={}", request.getType(),
                    session.getId(), ex);
            response = Envelope.error(request.getCorrelation_id(),
                    "server error: " + ex.getClass().getSimpleName());
        }

        if (response != null) {
            send(session, response);
        }
    }

    /**
     * 소켓이 닫히면 세션 레지스트리에서 제거합니다.
     *
     * @param session 힌 세션
     * @param status 종료 상태
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("agent socket closed: session={} status={}", session.getId(), status);
        registry.unregister(session);
    }

    /**
     * 전송 계층 오류를 로그로 남깁니다. 소켓은 곧 힙니다.
     *
     * @param session 세션
     * @param exception 발생한 예외
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("transport error on session {}: {}", session.getId(), exception.getMessage());
    }

    /**
     * 투를 JSON 로 직렬화해 전송합니다.
     *
     * @param session 대상 세션
     * @param envelope 보낼 메시지
     */
    private void send(WebSocketSession session, Envelope envelope) {
        try {
            final String json = objectMapper.writeValueAsString(envelope);
            final WebSocketSession target = wrapped(session);
            synchronized (session) {
                if (target.isOpen()) {
                    target.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception ex) {
            log.warn("failed to send {} to session {}: {}",
                    envelope.getType(), session.getId(), ex.getMessage());
        }
    }

    /**
     * 데코레이터가 심어져 있으면 그 세션을, 없으면 새로 만들어 반환합니다.
     *
     * @param session 원본 세션
     * @return 감싼 세션
     */
    private WebSocketSession wrapped(WebSocketSession session) {
        final Object existing = session.getAttributes().get("wrapped");
        if (existing instanceof WebSocketSession wrappedSession) {
            return wrappedSession;
        }
        return toWrapped(session);
    }

    /**
     * 새 데코레이터를 만듭니다.
     *
     * @param session 원본 세션
     * @return 감싼 세션
     */
    private WebSocketSession toWrapped(WebSocketSession session) {
        return new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT);
    }
}