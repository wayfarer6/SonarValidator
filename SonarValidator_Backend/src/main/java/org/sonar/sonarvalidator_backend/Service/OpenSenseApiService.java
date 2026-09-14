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
        
    }


}
