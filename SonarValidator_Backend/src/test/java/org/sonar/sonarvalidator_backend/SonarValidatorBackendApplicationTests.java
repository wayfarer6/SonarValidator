package org.sonar.sonarvalidator_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 애플리케이션 컨텍스트가 정상 기동하는지 확인합니다.
 *
 * <p>{@code test} 프로필은 H2 인메모리 DB 를 쓰므로, PostgreSQL 서버가 없는
 * 환경에서도 이 테스트가 통과합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SonarValidatorBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
