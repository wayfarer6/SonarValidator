package org.sonar.sonarvalidator_backend.Client;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration 
public class OPNSenseClientConfig {

    private String baseurl;
    private String apiKey;

    public OPNSenseClientConfig(String baseurl,String apiKey)
    {
        this.baseurl = baseurl;
        this.apiKey = apiKey;
    }


    @Bean  
    public WebClient webClient(WebClient.Builder builder) {
        return builder
        .baseUrl("https://test.local") // 나중에 각 agent를 인자로 받아야
        .defaultHeaders(httpHeaders-> {
            httpHeaders.add(httpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            httpHeaders.add("apiKey","api 값");
        })
        .build();
    }
    
}
