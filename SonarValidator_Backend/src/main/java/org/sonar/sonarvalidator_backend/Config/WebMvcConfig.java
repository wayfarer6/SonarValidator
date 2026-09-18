package org.sonar.sonarvalidator_backend.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 MVC 설정입니다. <b>CORS 의 단일 정의 지점</b>입니다.
 *
 * <h2>왜 CORS 가 필요한가</h2>
 * <p>프론트엔드는 Vite 개발 서버(기본 5173)에서 뜨고, 백엔드는 3000 에서
 * 뜹니다. 브라우저 입장에서 <b>다른 출처</b> 이므로, 이 설정이 없으면
 * 프론트엔드의 {@code fetch} 가 차단됩니다. (WebSocket 은 CORS 영향을 받지
 * 않아 에이전트 통신에는 문제가 없었습니다 — 그래서 이 설정이 늦게 드러납니다.)
 *
 * <h2>⚠️ 여기가 유일한 CORS 정의여야 합니다</h2>
 * <p>스프링 시큐리티도 CORS 설정을 요구합니다. 만약 {@code SecurityConfig} 에
 * {@code CorsConfigurationSource} 빈을 하나 더 만들면, 컨테이너에 같은 타입
 * 빈이 <b>둘</b>이 되어 기동이 실패합니다.
 *
 * <pre>
 *   No qualifying bean of type 'CorsConfigurationSource' available:
 *   expected single matching bean but found 2:
 *   corsConfigurationSource, mvcHandlerMappingIntrospector
 * </pre>
 *
 * <p>{@code mvcHandlerMappingIntrospector} 는 이 클래스의
 * {@code addCorsMappings} 를 스프링이 자동으로 노출한 것입니다. 그래서
 * 시큐리티 쪽에서는 {@code cors(Customizer.withDefaults())} 로 그것을 그대로
 * 사용하고, 정의는 이 파일에만 둡니다.
 *
 * <h2>세션 쿠키와 allowCredentials</h2>
 * <p>로그인이 세션 기반이라 브라우저가 {@code JSESSIONID} 쿠키를 주고받아야
 * 합니다. 그러려면 {@code allowCredentials(true)} 가 필요하고, 그러면
 * {@code allowedOrigins("*")} 를 쓸 수 없습니다. (스프링이 런타임에 거부)
 * 그래서 패턴 방식({@code allowedOriginPatterns}) 을 씁니다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 허용할 출처 패턴 (쉼표 구분). */
    private final String[] allowedOriginPatterns;

    /**
     * @param allowedOriginPatterns 허용 출처 패턴 (쉼표 구분 문자열)
     */
    public WebMvcConfig(
            @Value("${sonar.cors.allowed-origins:http://localhost:5173,http://localhost:4173}"
                    + ",http://127.0.0.1:5173}") String allowedOriginPatterns) {
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
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 세션 쿠키(JSESSIONID)를 주고받으려면 필수입니다.
                .allowCredentials(true)
                .maxAge(3600);
    }
}
