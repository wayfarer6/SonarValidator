package org.sonar.sonarvalidator_backend.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;

    public WebMvcConfig(
            @Value("${sonar.cors.allowed-origins:${SONAR_CORS_ALLOWED_ORIGINS:"
                    + "http://localhost:*,http://127.0.0.1:*}}")
                    String allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                // 세션 쿠키(JSESSIONID)를 주고받으려면 필수입니다.
                .allowCredentials(true)
                .maxAge(3600);
    }

    public boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.List.of(allowedOriginPatterns));
        return configuration.checkOrigin(origin) != null;
    }
}
