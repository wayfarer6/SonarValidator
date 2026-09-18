package org.sonar.sonarvalidator_backend.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Agent 통신용 plain WebSocket 설정입니다. (STOMP/SockJS 완전 제거)
 *
 * <h2>변경 이력</h2>
 * <p>기존에는 {@code @EnableWebSocketMessageBroker} + SimpleBroker + SockJS 였으나,
 * C++ Prober(Boost.Beast)가 STOMP 프레임을 만들 수 없고 SimpleBroker 가
 * RECEIPT/ACK 을 지원하지 않아 요청-응답 상관관계를 맞출 수 없었습니다.
 * 그래서 서브프로토콜 없이 <b>평문 WebSocket + JSON 봉투</b>로 전환했습니다.
 *
 * <h2>엔드포인트를 2개 두는 이유</h2>
 * <p>{@code /api/v1/management} 와 {@code /api/v1/telemetry} 는
 * <b>의미 구분용</b>이며 처리 로직은 동일합니다. 실제 분기는 봉투의 {@code type}
 * 필드가 담당합니다. Agent 코드가 이미 이 두 경로를 쓰고 있어(설정 변경 최소화)
 * 그대로 유지합니다. (mock 서버와 경로 계약도 동일해집니다.)
 *
 * <h2>SockJS 를 쓰지 않는 이유</h2>
 * <p>SockJS 는 브라우저 폴백(XHR 스트리밍 등)을 위한 계층입니다. Agent 는
 * 네이티브 WebSocket 만 사용하므로 불필요하고, 오히려 핸드셰이크 경로가
 * {@code /info}, {@code /xhr_streaming} 등으로 갈라져 디버깅이 어려워집니다.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /** 관리(정책/명령) 채널 경로. */
    public static final String MANAGEMENT_PATH = "/api/v1/management";

    /** 텔레메트리 채널 경로. */
    public static final String TELEMETRY_PATH = "/api/v1/telemetry";

    /**
     * 텔레메트리 한 건의 최대 크기(바이트).
     *
     * <h2>1MB 로 올려야 하는 이유</h2>
     * <p>Tomcat 의 WebSocket 기본 텍스트 버퍼는 <b>8192 바이트</b>입니다. 이 값보다
     * 크 프레임이 오면 컨테이너가 <b>close code 1009</b>
     * ("The decoded text message was too big for the output buffer")
     * 로 소켓을 끊고, 이후 텔레메트리가 통째로 유실됩니다.
     *
     * <p>실측(2026-09-18, PoC 랩): 장치 유형별 텔레메트리 JSON 크기
     * <ul>
     *   <li>VM: 약 1.7 KB</li>
     *   <li>OpenVSwitch 스위치: 약 6.8 KB</li>
     *   <li>nftables 방화벽: 약 7.1 KB</li>
     *   <li><b>FRR 라우터: 약 11.8 KB</b> <- 기본 8 KB 초과</li>
     * </ul>
     * 라우터는 경로 테이블과 ARP 테이블이 커서 8 KB 를 넘습니다. 그래서 기본값으로는
     * 라우터만 조용히 실패합니다. 여유를 크게 두어 장치가 늘어나도 견디게 합니다.
     *
     * <p>주의: {@code spring.websocket.max-text-message-buffer-size} 같은
     * 프로퍼티는 <b>Spring Boot 에 존재하지 않습니다.</b> 컨테이너에 직접 설정해야
     * 반영됩니다.
     */
    private static final int MAX_MESSAGE_BUFFER_SIZE = 1024 * 1024;

    private final AgentWebSocketHandler agentWebSocketHandler;

    public WebSocketConfig(AgentWebSocketHandler agentWebSocketHandler) {
        this.agentWebSocketHandler = agentWebSocketHandler;
    }

    /**
     * Tomcat WebSocket 컨테이너의 버퍼 크기를 올립니다.
     *
     * <p>이 빈이 없으면 Tomcat 기본값(8 KB)이 적용되어 큰 텔레메트리 프레임이
     * close 1009 로 거부됩니다.
     *
     * @return 버퍼 크기가 설정된 컨테이너 팩토리
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        final ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_MESSAGE_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_MESSAGE_BUFFER_SIZE);
        // 유휴 연결이 정책 푸시를 놓치지 않도록 넘념히 둡니다.
        container.setMaxSessionIdleTimeout(0L);
        return container;
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
