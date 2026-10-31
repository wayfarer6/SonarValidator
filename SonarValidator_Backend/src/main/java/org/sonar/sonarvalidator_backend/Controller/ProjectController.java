package org.sonar.sonarvalidator_backend.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.dto.ProjectDto;
import org.sonar.sonarvalidator_backend.Model.dto.ProjectMapper;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine;
import org.sonar.sonarvalidator_backend.Service.ProjectService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Map<String, Object> list() {
        final List<Map<String, Object>> projects = new ArrayList<>();
        for (final Project project : projectService.listAll()) {
            projects.add(ProjectMapper.toSummary(project));
        }
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", projects.size());
        body.put("projects", projects);
        return body;
    }

    @GetMapping("/{projectId}")
    public Map<String, Object> get(@PathVariable String projectId) {
        return ProjectMapper.toResponse(projectService.getByKey(projectId));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody(required = false) ProjectDto.CreateRequest body) {
        final ProjectDto.CreateRequest request = body == null
                ? new ProjectDto.CreateRequest(null, null, null, null, null, null)
                : body;
        return ProjectMapper.toResponse(projectService.create(request));
    }

    @PutMapping("/{projectId}")
    public Map<String, Object> update(@PathVariable String projectId,
                                      @RequestBody ProjectDto.UpdateRequest body) {
        final ProjectDto.UpdateRequest request = body == null
                ? new ProjectDto.UpdateRequest(null, null, null, null, null, null, null)
                : body;
        return ProjectMapper.toResponse(projectService.update(projectId, request));
    }

    @DeleteMapping("/{projectId}")
    public Map<String, Object> delete(@PathVariable String projectId) {
        projectService.delete(projectId);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", true);
        body.put("project_id", projectId);
        return body;
    }

    @GetMapping("/{projectId}/validation")
    public Map<String, Object> validateStored(@PathVariable String projectId) {
        return ProjectMapper.toValidationResponse(projectService.validateStored(projectId));
    }

    @PostMapping("/{projectId}/validation")
    public Map<String, Object> validate(@PathVariable String projectId,
                                        @RequestBody(required = false) ProjectDto.ValidateRequest body) {
        final SegmentationBddEngine.Report report = (body == null
                || (body.subnets() == null && body.rules() == null))
                ? projectService.validateStored(projectId)
                : projectService.validate(body);
        return ProjectMapper.toValidationResponse(report);
    }

    @GetMapping("/{projectId}/forbidden-pairs")
    public Map<String, Object> forbiddenPairs(@PathVariable String projectId) {
        final var project = projectService.getByKey(projectId);
        final List<Map<String, Object>> pairs = new ArrayList<>();
        final var forbidden = projectService.forbiddenPairs(project.toPolicySubnets());
        for (final String[] pair : forbidden) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("src", pair[0]);
            entry.put("dst", pair[1]);
            pairs.add(entry);
        }
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("total", pairs.size());
        body.put("pairs", pairs);
        return body;
    }

    @PostMapping("/draft/validation")
    public Map<String, Object> validateDraft(@RequestBody ProjectDto.ValidateRequest body) {
        final ProjectDto.ValidateRequest request = body == null
                ? new ProjectDto.ValidateRequest(List.of(), List.of())
                : body;
        return ProjectMapper.toValidationResponse(projectService.validate(request));
    }
}
