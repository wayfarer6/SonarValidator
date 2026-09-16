package org.sonar.sonarvalidator_backend.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Agent 통신용 plain WebSocket 설정입니다. (STOMP/SockJS 완전 제거)
 *
 * <h2>변경 이력</h2>
 * <p>기존에는 {@code @EnableWebSocketMessageBroker} + SimpleBroker + SockJS 였으나,
 * C++ Prober(Boost.Beast)가 STOMP 프레임을 만들 수 없고 SimpleBroker 가
 * RECEIPT/ACK 을 지원하지 않아 요청-응답 상관관계를 맞출 수 없었습니다.
 * 그래서 서브프로토콜 없이 <b>평문 WebSocket + JSON 투</b>로 전환했습니다.
 *
 * <h2>엔드포인트를 2개 두는 이유</h2>
 * <p>{@code /api/v1/management} 와 {@code /api/v1/telemetry} 
 * <b>의미 구분용</b>이며 처리 로직은 동일합니다. 실제 분기는 봉투의 {@code type}
 * 필드가 담당합니다. Agent 코드가 이미 이 두 경로를 쓰고 있어(설정 변경 최소화)
 * 그대로 유지합니다. (mock 서버와 경로 계약도 동일해집니다.)
 *
 * <h2>SockJS 를 쓰지 않는 이유</h2>
 * <p>SockJS 는 브라우저 폴백(XHR 스트리밍 등)을 위한 계층입니다. Agent 는
 * 네이티브 WebSocket 만 사용하므로 불필요하고, 오히려 핸드이크 경로가
 * {@code /info}, {@code /xhr_streaming} 등으로 갈라져 디버깅이 어려워집니다.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /** 관리(정책/명령) 채널 경로. */
    public static final String MANAGEMENT_PATH = "/api/v1/management";

    /** 텔레메트리 채널 경로. */
    public static final String TELEMETRY_PATH = "/api/v1/telemetry";

    private final AgentWebSocketHandler agentWebSocketHandler;

    public WebSocketConfig(AgentWebSocketHandler agentWebSocketHandler) {
        this.agentWebSocketHandler = agentWebSocketHandler;
    }

    /**
     * Agent 가 접속할 두 경로를 같은 핸들러에 연결합니다.
     *
     * @param registry 핸들러 등록기
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, MANAGEMENT_PATH, TELEMETRY_PATH)
                .setAllowedOriginPatterns("*");
    }
}
