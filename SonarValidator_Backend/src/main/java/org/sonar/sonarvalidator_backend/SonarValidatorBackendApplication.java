package org.sonar.sonarvalidator_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 백엔드 애플리케이션 진입점입니다.
 *
 * <p>{@link ConfigurationPropertiesScan} 은 {@code Config} 패키지의
 * {@code @ConfigurationProperties} 빈(예: {@code SiteProperties})을 자동
 * 등록합니다. 이것이 없으면 설정 클래스가 주입되지 않아 기동에 실패합니다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SonarValidatorBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SonarValidatorBackendApplication.class, args);
    }

}
