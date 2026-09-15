package org.sonar.sonarvalidator_backend.Controller;

import org.sonar.sonarvalidator_backend.Service.AgentWebSocketService;
import org.springframework.boot.jackson.autoconfigure.JacksonProperties;

import java.util.ArrayList;

public class FirewallController extends AgentController  {
    public FirewallController(AgentWebSocketService agentWebSocketService) {
        super(agentWebSocketService);
    }

    @Override
    public ArrayList<JacksonProperties.Json> FetchPolicy() {
        ArrayList<JacksonProperties.Json> policies = new ArrayList<>();

        return policies;
    }
}
