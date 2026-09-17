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

    public OPNSenseClientService(
            @Value("${opnsense.base-url:https://localhost:8080}") String baseUrl,
            OPNSenseEndpoint apiEndpoint,
            OPNSenseClientConfig clientConfig) {
        this.baseUrl = baseUrl;
        this.api_endpoint = apiEndpoint;
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
