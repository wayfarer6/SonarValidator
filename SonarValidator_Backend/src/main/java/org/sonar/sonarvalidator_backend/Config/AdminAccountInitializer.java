package org.sonar.sonarvalidator_backend.Config;

import org.sonar.sonarvalidator_backend.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 기본 관리자 계정을 준비합니다.
 *
 * <h2>왜 필요한가</h2>
 * <p>사용자 테이블이 비어 있으면 <b>아무도 로그인할 수 없습니다.</b>
 * 스프링 시큐리티는 사용자를 자동으로 만들지 않으므로, 첫 진입 경로가
 * 없으면 시스템에 접근할 방법이 사라집니다.
 *
 * <h2>비밀번호 주입</h2>
 * <p>기본값은 {@code admin} 이지만, 환경변수
 * {@code SONAR_ADMIN_PASSWORD} 로 덮어쓸 수 있습니다. 기본값을 쓴 경우
 * {@link UserService#ensureDefaultAdmin} 가 <b>경고 로그</b>를 남깁니다.
 *
 * <p>운영 배포에서는 반드시 환경변수를 주입하세요.
 * <pre>
 *   SONAR_ADMIN_PASSWORD='...' java -jar app.jar
 * </pre>
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserService userService;

    /** 기본 관리자 비밀번호 (환경변수로 덮어쓰기 가능). */
    private final String defaultAdminPassword;

    /**
     * @param userService          사용자 서비스
     * @param defaultAdminPassword 기본 비밀번호
     */
    public AdminAccountInitializer(
            UserService userService,
            @Value("${sonar.admin.password:admin}") String defaultAdminPassword) {
        this.userService = userService;
        this.defaultAdminPassword = defaultAdminPassword;
    }

    /**
     * 사용자가 없을 때만 기본 관리자를 만듭니다.
     *
     * <p>실패해도 애플리케이션 기동은 계속합니다. 계정 생성 실패로 서버가
     * 뜨지 않으면 원인을 확인할 방법이 없어지기 때문입니다.
     *
     * @param args 실행 인자 (사용하지 않음)
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            final boolean created = userService.ensureDefaultAdmin(defaultAdminPassword);
            if (!created) {
                log.debug("admin initializer: users already exist, nothing to do");
            }
        } catch (RuntimeException ex) {
            log.error("failed to create default admin account: {}", ex.getMessage(), ex);
        }
    }
}
