package org.sonar.sonarvalidator_backend.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 MVC 설정입니다.
 *
 * <h2>왜 CORS 가 필요한가</h2>
 * <p>프론트엔드는 Vite 개발 서버(기본 5173)에서 뜨고, 백엔드는 3000 에서
 * 뜹니다. 브라우저 입장에서 <b>다른 출처</b> 이므로, 이 설정이 없으면
 * 프론트엔드의 {@code fetch} 가 차단됩니다. (WebSocket 은 CORS 영향을 받지
 * 않아 에이전트 통신에는 문제가 없었습니다 — 그래서 이 설정이 늦게 드러납니다.)
 *
 * <h2>허용 출처를 어떻게 정하는가</h2>
 * <p>{@code sonar.cors.allowed-origins} 프로퍼티로 주입합니다. 기본값은 개발
 * 편의를 위해 localhost 계열만 열어 둡니다. 운영에서는 실제 프론트엔드
 * 도메인만 넣으세요.
 *
 * <p><b>주의</b>: {@code allowedOrigins("*")} 를 {@code allowCredentials(true)}
 * 와 함께 쓰면 Spring 이 런타임에 거부합니다. 그래서 패턴 방식
 * ({@code allowedOriginPatterns}) 을 씁니다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 허용할 출처 패턴 (쉼표 구분). */
    private final String[] allowedOriginPatterns;

    /**
     * @param allowedOriginPatterns 허용 출처 패턴 (쉼표 구분 문자열)
     */
    public WebMvcConfig(
            @Value("${sonar.cors.allowed-origins:http://localhost:5173,http://localhost:4173,http://127.0.0.1:5173}")
            String allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns.split("\\s*,\\s*");
    }

    /**
     * REST API 경로에 CORS 를 적용합니다.
     *
     * @param registry CORS 등록기
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
