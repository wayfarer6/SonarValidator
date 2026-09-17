package org.sonar.sonarvalidator_backend.Service;

import org.springframework.boot.jackson.autoconfigure.JacksonProperties.Json;
import org.springframework.stereotype.Service;

@Service 
public class CiscoRouterLogParserService implements NetworkLogParserService {
    
    public Json parseInBoundLog()
    {
        return new Json();
    }
    
    public Json parseOutBoundLog() {
        return new Json();
    }
}
