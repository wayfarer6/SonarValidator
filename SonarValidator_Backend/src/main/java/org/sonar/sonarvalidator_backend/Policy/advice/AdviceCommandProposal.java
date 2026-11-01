package org.sonar.sonarvalidator_backend.Policy.advice;

import java.util.List;
import java.util.Map;

public record AdviceCommandProposal(String agentId, String title, String reason,
        String operation, Map<String, Object> arguments, List<String> commands) {
    public Map<String, Object> toView() {
        return Map.of("agent_id", agentId, "title", title, "reason", reason,
                "operation", operation, "arguments", arguments, "suggested_commands", commands);
    }
}
