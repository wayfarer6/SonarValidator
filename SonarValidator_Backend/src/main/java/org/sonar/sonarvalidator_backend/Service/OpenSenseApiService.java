package org.sonar.sonarvalidator_backend.Service;

import org.sonar.sonarvalidator_backend.Client.OPNSenseClientConfig;
import org.springframework.stereotype.Service;

@Service 
public class OpenSenseApiService {

    private OPNSenseClientConfig config;

    public OpenSenseApiService()
    {
        this.config = new OPNSenseClientConfig("http://test.com","apiKey-spxxxxx");
    }

    //1. Get 요청
    public String callGetApi(Long id) {
        // TODO: OPNSense REST GET 구현 예정.
        // 현재는 컴파일 가능한 최소 구현으로, 요청 URL 을 돌려줍니다.
        return config.getBaseUrl() + "/api/" + id;
    }


}
