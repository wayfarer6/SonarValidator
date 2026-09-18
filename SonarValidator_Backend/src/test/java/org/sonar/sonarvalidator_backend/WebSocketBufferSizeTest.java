package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 큰 텔레메트리 프레임이 서버에서 거부되지 않는지 확인합니다.
 *
 * <h2>왜 이 테스트가 필요한가</h2>
 * <p>Tomcat 의 WebSocket 기본 텍스트 버퍼는 <b>8192 바이트</b>입니다. 이보다 큰
 * 프레임이 오면 컨테이너가 <b>close code 1009</b>
 * ("The decoded text message was too big for the output buffer") 로 소켓을 끊습니다.
 *
 * <p>실측(2026-09-18, PoC 랩) 텔레메트리 JSON 크기:
 * <ul>
 *   <li>VM 약 1.7 KB, OpenVSwitch 약 6.8 KB, nftables 약 7.1 KB → 기본값으로 통과</li>
 *   <li><b>FRR 라우터 약 11.8 KB → 기본값에서 전부 거부</b></li>
 * </ul>
 * 라우터만 조용히 텔레메트리가 사라지는 증상의 원인이었습니다. 정책 요청/응답은
 * 수백 바이트라 영향이 없어서 "연결은 되는데 텔레메트리만 없다" 로 보입니다.
 *
 * <p>이 테스트는 실제 서버가 떠 있지 않아도 동작하도록, <b>설정 클래스가 만든
 * 컨테이너 팩토리의 값</b>을 직접 검증합니다. 실서버 통합 검증은 배포 가이드의
 * 프레임 크기 프로브로 수행합니다.
 */
class WebSocketBufferSizeTest {

    /** 기본값이면 실패하는 크기. 실제 라우터 페이로드(약 11.8KB)보다 크게 잡습니다. */
    private static final int LARGE_PAYLOAD_BYTES = 12 * 1024;

    /** Tomcat 의 WebSocket 기본 버퍼 크기. 이 값이면 큰 프레임이 거부됩니다. */
    private static final int TOMCAT_DEFAULT_BUFFER = 8192;

    /**
     * 설정된 버퍼 크기가 기본값보다 충분히 커야 합니다.
     *
     * <p>{@code WebSocketConfig.createWebSocketContainer()} 가 반환하는 팩토리를
     * 직접 만들어 값을 확인합니다. (컨텍스트를 띄우지 않아 빠릅니다.)
     */
    @Test
    @DisplayName("WebSocket 컨테이너 버퍼가 Tomcat 기본값(8KB)보다 크다")
    void containerBufferExceedsTomcatDefault() {
        final org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean
                container = new org.sonar.sonarvalidator_backend.Config.WebSocketConfig(null)
                        .createWebSocketContainer();

        assertNotNull(container, "container bean must exist");
        assertEquals(1024 * 1024, container.getMaxTextMessageBufferSize(),
                "text buffer must be 1MB so router telemetry (~11.8KB) is accepted");
        assertEquals(1024 * 1024, container.getMaxBinaryMessageBufferSize());

        assertTrue(container.getMaxTextMessageBufferSize() > TOMCAT_DEFAULT_BUFFER,
                "buffer must exceed Tomcat default, otherwise close 1009 drops telemetry");
        assertTrue(container.getMaxTextMessageBufferSize() > LARGE_PAYLOAD_BYTES,
                "buffer must exceed the largest real payload (" + LARGE_PAYLOAD_BYTES + " bytes)");
    }

}
