package org.sonar.sonarvalidator_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 애플리케이션 컨텍스트가 정상 기동하는지 확인합니다.
 *
 * <p>{@code test} 프로필은 H2 인메모리 DB 를 쓰므로, PostgreSQL 서버가 없는
 * 환경에서도 이 테스트가 통과합니다.
 *
 * <h2>RANDOM_PORT 를 쓰는 이유</h2>
 * <p>{@link org.springframework.boot.test.context.SpringBootTest.WebEnvironment#MOCK}(기본값)은
 * <b>실제 서블릿 컨테이너를 띄우지 않습니다.</b> 그러면
 * {@code Config/WebSocketConfig} 의 {@code ServletServerContainerFactoryBean} 이
 * 요구하는 {@code jakarta.websocket.server.ServerContainer} 속성이
 * ServletContext 에 없어 컨텍스트 기동이 실패합니다.
 * ("Attribute 'jakarta.websocket.server.ServerContainer' not found in ServletContext")
 *
 * <p>WebSocket 버퍼 크기 설정은 실톰컷에만 의미가 있으므로,
 * 이 테스트는 실제 톰컷을 띄워 기동을 검증합니다.
 * (포트는 RANDOM_PORT 라 충돌하지 않습니다.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SonarValidatorBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
