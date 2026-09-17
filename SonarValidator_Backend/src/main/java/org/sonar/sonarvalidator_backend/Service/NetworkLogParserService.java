package org.sonar.sonarvalidator_backend.Service;

import org.springframework.boot.jackson.autoconfigure.JacksonProperties.Json;

public interface NetworkLogParserService {
    public Json parseInBoundLog();
    public Json parseOutBoundLog();
    
} 