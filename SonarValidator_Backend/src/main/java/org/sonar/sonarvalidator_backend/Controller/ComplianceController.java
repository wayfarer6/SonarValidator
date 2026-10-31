package org.sonar.sonarvalidator_backend.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.entity.ComplianceChange;
import org.sonar.sonarvalidator_backend.Service.ComplianceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/compliance")
public class ComplianceController {

    private final ComplianceService complianceService;

    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    @GetMapping("/changes")
    public Map<String, Object> changes(
            @RequestParam(value = "project_id", required = false) String projectId,
            @RequestParam(value = "agent_id", required = false) String agentId) {

        final List<ComplianceChange> changes;
        if (agentId != null && !agentId.isBlank()) {
            changes = complianceService.byAgent(agentId);
        } else if (projectId != null && !projectId.isBlank()) {
            changes = complianceService.byProject(projectId);
        } else {
            changes = complianceService.listAll();
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("agent_id", agentId);
        body.put("total", changes.size());
        body.put("changes", ComplianceService.toResponse(changes));
        return body;
    }

    @GetMapping("/changes/project/{projectId}")
    public Map<String, Object> byProject(@PathVariable String projectId) {
        final List<ComplianceChange> changes = complianceService.byProject(projectId);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("total", changes.size());
        body.put("changes", ComplianceService.toResponse(changes));
        return body;
    }

    @GetMapping("/changes/agent/{agentId}")
    public Map<String, Object> byAgent(@PathVariable String agentId) {
        final List<ComplianceChange> changes = complianceService.byAgent(agentId);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", agentId);
        body.put("total", changes.size());
        body.put("changes", ComplianceService.toResponse(changes));
        return body;
    }
}
