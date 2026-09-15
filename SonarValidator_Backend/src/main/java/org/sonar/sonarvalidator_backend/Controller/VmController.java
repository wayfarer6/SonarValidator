package org.sonar.sonarvalidator_backend.Controller;

import org.sonar.sonarvalidator_backend.Service.AgentWebSocketService;
import org.springframework.stereotype.Controller;

@Controller
public class VmController extends AgentController {

    public VmController(AgentWebSocketService agentWebSocketService) {
        super(agentWebSocketService);
    }
}
