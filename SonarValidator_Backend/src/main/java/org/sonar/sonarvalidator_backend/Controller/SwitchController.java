package org.sonar.sonarvalidator_backend.Controller;

import org.sonar.sonarvalidator_backend.Service.AgentWebSocketService;
import org.springframework.stereotype.Controller;

@Controller
public class SwitchController extends AgentController {
    public SwitchController(AgentWebSocketService agentWebSocketService) {
        super(agentWebSocketService);
    }
}
