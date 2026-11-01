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

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    private static final int SEND_BUFFER_LIMIT = 512 * 1024;

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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put("wrapped",
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT));
        // hello 를 보내기 전까지 agent_id 를 모르므로 여기서는 등록하지 않습니다.
        log.debug("agent socket opened: session={} remote={}",
                session.getId(), session.getRemoteAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        final String raw = message.getPayload();
        Envelope request;
        try {
            request = objectMapper.readValue(raw, Envelope.class);
        } catch (RuntimeException ex) {
            log.warn("invalid envelope from session {}", session.getId());
            registry.send(session, Envelope.error(null, "invalid JSON envelope"));
            return;
        }

        if (request == null) {
            registry.send(session, Envelope.error(null, "invalid JSON envelope"));
            return;
        }

        if ("unknown".equals(request.getType())) {
            registry.send(session, Envelope.error(request.getCorrelation_id(),
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
            registry.send(session, response);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.debug("agent socket closed: session={} status={}", session.getId(), status);
        registry.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("transport error on session {}: {}", session.getId(), exception.getMessage());
    }

}
