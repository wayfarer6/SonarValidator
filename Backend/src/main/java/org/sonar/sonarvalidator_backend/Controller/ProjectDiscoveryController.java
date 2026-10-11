package org.sonar.sonarvalidator_backend.Controller;

import java.util.Map;
import org.sonar.sonarvalidator_backend.Service.ProjectDiscoveryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectDiscoveryController {
    private final ProjectDiscoveryService discovery;
    public ProjectDiscoveryController(ProjectDiscoveryService discovery) { this.discovery = discovery; }

    @GetMapping("/api/v1/projects/{projectId}/editing")
    public Map<String, Object> editing(@PathVariable String projectId) {
        return discovery.forEditing(projectId);
    }
}
