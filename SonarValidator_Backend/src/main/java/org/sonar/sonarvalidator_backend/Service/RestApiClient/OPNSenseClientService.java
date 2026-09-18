package org.sonar.sonarvalidator_backend.Service.RestApiClient;


import org.sonar.sonarvalidator_backend.Client.OPNSenseClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;


@Service
public class OPNSenseClientService {

    private final String baseUrl; // https://localhost:8080 이런식으로
    private final OPNSenseEndpoint api_endpoint;
    private final OPNSenseClientConfig clientConfig;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * OPNSense REST 클라이언트를 만듭니다.
     *
     * <p>{@link OPNSenseEndpoint} 는 <b>열거형</b>이라 Spring 빈이 될 수 없습니다.
     * 생성자 파라미터로 두면 컨테이너가 주입할 대상을 찾지 못해
     * 기동이 실패하므로, 여기서는 기본 엔드포인트를 직접 지정합니다.
     * 호출별 엔드포인트는 {@link #buildRequest(String, String)} 에 경로로 전달됩니다.
     *
     * @param baseUrl      OPNSense 기준 URL (프로퍼티 {@code opnsense.base-url})
     * @param clientConfig OPNSense 접속 설정(기본 URL/API 키)
     */
    public OPNSenseClientService(
            @Value("${opnsense.base-url:}") String baseUrl,
            OPNSenseClientConfig clientConfig) {
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? clientConfig.getBaseUrl() : baseUrl;
        this.api_endpoint = OPNSenseEndpoint.GET_FETCH_ALL_INTERFACE;
        this.clientConfig = clientConfig;
    }

    public OPNSenseEndpoint getApiEndpoint() {
        return api_endpoint;
    }

    public OPNSenseClientConfig getClientConfig() {
        return clientConfig;
    }

    private HttpRequest buildRequest(String httpMethod, String path) {
        return HttpRequest.newBuilder()
                .method(httpMethod, HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(baseUrl + path))
                .build();
    }
}
