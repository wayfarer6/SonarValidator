package org.sonar.sonarvalidator_backend.Service.RestApiClient;

public enum OPNSenseEndpoint {
    
    GET_FETCH_ALL_INTERFACE("/api/diagnostics/interface/get"),
    GET_FETCH_ALL_FIREWALL_RULES("/api/firewall/filter/search_rule?rowCount=-1");
    private final String url; 

    // 3. 생성자 추가
    OPNSenseEndpoint(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
} 