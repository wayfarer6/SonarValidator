package org.sonar.sonarvalidator_backend.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Service.AgentMessageRouterService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.ExpectedAgentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/agents")
public class ExpectedAgentController {

    private final ExpectedAgentService service;
    private final AgentSessionRegistry registry;
    private final AgentMessageRouterService router;

    public ExpectedAgentController(ExpectedAgentService service,
                                   AgentSessionRegistry registry,
                                   AgentMessageRouterService router) {
        this.service = service;
        this.registry = registry;
        this.router = router;
    }

    @PostMapping("/expected")
    public Map<String, Object> register(@RequestBody JsonNode body) {
        final String agentId = text(body, "agent_id", text(body, "agentId", null));
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agent_id is required");
        }

        final ExpectedAgent saved = service.register(
                agentId,
                text(body, "project_id", text(body, "projectKey", null)),
                text(body, "device_type", text(body, "deviceType", null)),
                text(body, "node_type", text(body, "nodeType", null)),
                text(body, "expected_ip", text(body, "expectedIp", null)),
                text(body, "note", null));

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_id", saved.getAgentId());
        result.put("project_id", saved.getProjectKey());
        result.put("device_type", saved.getDeviceType());
        result.put("node_type", saved.getNodeType());
        result.put("status", saved.getStatus());
        result.put("registered", true);
        return result;
    }

    @GetMapping("/expected")
    public Map<String, Object> list(@RequestParam(name = "project_id", required = false) String projectId) {
        final List<ExpectedAgent> expected = service.list(projectId);
        return ExpectedAgentService.toResponse(expected, registry.connectedAgentIds(), observedAgentIds());
    }

    @DeleteMapping("/expected/{agentId}")
    public Map<String, Object> delete(
            @PathVariable String agentId,
            @RequestParam(name = "project_id", required = false) String projectId) {
        if (projectId == null || projectId.isBlank()) {
            service.delete(agentId);
        } else {
            service.deleteFromProject(agentId, projectId);
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_id", agentId);
        result.put("removed", true);
        if (projectId != null && !projectId.isBlank()) {
            result.put("project_id", projectId);
        }
        return result;
    }

    private Set<String> observedAgentIds() {
        final Set<String> ids = new TreeSet<>();
        for (final String agentId : router.allConfigs().keySet()) {
            if (agentId != null) {
                ids.add(agentId);
            }
        }
        for (final String agentId : registry.connectedAgentIds()) {
            if (agentId != null && router.lastTelemetryOf(agentId) != null) {
                ids.add(agentId);
            }
        }
        return ids;
    }

    private static String text(JsonNode body, String key, String fallback) {
        if (body == null) {
            return fallback;
        }
        final JsonNode node = body.path(key);
        if (node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        final String value = node.isString() ? node.asString() : node.toString();
        return (value == null || value.isBlank()) ? fallback : value;
    }

    public static Map<String, Object> buildResponse(List<ExpectedAgent> expected, Set<String> connectedIds) {
        return ExpectedAgentService.toResponse(expected, new ArrayList<>(connectedIds), List.of());
    }
}
