package org.sonar.sonarvalidator_backend.Controller;

import java.util.Map;
import org.sonar.sonarvalidator_backend.Service.AgentMonitorSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/agents/{agentId}/monitor-settings")
public class AgentMonitorSettingsController {
    private final AgentMonitorSettingsService service;
    public AgentMonitorSettingsController(AgentMonitorSettingsService service) { this.service = service; }

    @GetMapping
    public Map<String, Object> get(@PathVariable String agentId, @RequestParam("project_id") String projectId) {
        return service.status(agentId, projectId);
    }

    @PutMapping
    public Map<String, Object> update(@PathVariable String agentId,
                                      @RequestParam("project_id") String projectId,
                                      @RequestBody JsonNode body) {
        final JsonNode seconds = body.path("monitor_interval_seconds");
        final JsonNode revision = body.path("revision");
        if (!seconds.isIntegralNumber() || !seconds.canConvertToInt() || seconds.asLong() < 1 || seconds.asLong() > AgentMonitorSettingsService.MAX_SECONDS ||
                !revision.isIntegralNumber() || !revision.canConvertToLong() || revision.asLong() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 수집 주기와 설정 버전이 필요합니다.");
        service.save(agentId, projectId, seconds.asInt(), revision.asLong());
        final boolean delivered = service.deliver(agentId);
        final Map<String, Object> result = service.status(agentId, projectId);
        result.put("delivered", delivered);
        return result;
    }
}
