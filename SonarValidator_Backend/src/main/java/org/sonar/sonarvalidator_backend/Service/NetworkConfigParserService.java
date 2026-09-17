package org.sonar.sonarvalidator_backend.Service;

import org.springframework.boot.jackson.autoconfigure.JacksonProperties.Json;

public interface NetworkConfigParserService {
    public Json parseSystemInfo(Json sysinfo);

}