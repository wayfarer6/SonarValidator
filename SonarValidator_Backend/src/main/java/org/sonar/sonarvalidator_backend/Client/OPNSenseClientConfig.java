package org.sonar.sonarvalidator_backend.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration 
public class OPNSenseClientConfig {

    private final String baseurl;
    private final String apiKey;

    /**
     * 설정값을 주입받습니다.
     *
     * <p>이전에는 생성자 파라미터에 빈 문자열이 필요했는데, {@code @Configuration}
     * 클래스는 컨테이너가 직접 생성하므로 String 타입 빈을 찾지 못해 애플리케이션
     * 기동이 실패했습니다. {@code @Value} 로 프로퍼티에서 읽고 기본값을 두어
     * 설정이 없어도 기동되게 합니다.
     *
     * @param baseurl OPNSense 기준 URL (기본 {@code https://test.local})
     * @param apiKey  OPNSense API 키 (기본 빈 문자열)
     */
    public OPNSenseClientConfig(
            @Value("${opnsense.base-url:https://test.local}") String baseurl,
            @Value("${opnsense.api-key:}") String apiKey)
    {
        this.baseurl = baseurl;
        this.apiKey = apiKey;
    }

    /**
     * 설정된 OPNSense 기준 URL 을 반환합니다.
     *
     * @return baseurl (예: {@code http://test.com})
     */
    public String getBaseUrl() {
        return baseurl;
    }

    /**
     * 설정된 API 키를 반환합니다.
     *
     * @return apiKey
     */
    public String getApiKey() {
        return apiKey;
    }


    /**
     * OPNSense 호출용 WebClient 를 만듭니다.
     *
     * <p>Spring Boot 4 에서는 이 애플리케이션이 서블릿(webmvc) 기반이라
     * 리액티브 자동구성이 물러서고 {@code WebClient.Builder} 빈이 만들어지지
     * 않습니다. 그래서 여기서 직접 빌더를 만들어 씁니다.
     *
     * @return 기본 헤더(JSON + apiKey)가 설정된 WebClient
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
        .baseUrl(baseurl) // 나중에 각 agent를 인자로 받아야
        .defaultHeaders(httpHeaders-> {
            httpHeaders.add(httpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            httpHeaders.add("apiKey", apiKey);
        })
        .build();
    }
    
}
