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

/**
 * 변경 이력(Compliance) 조회 API 입니다.
 *
 * <h2>프론트엔드 흐름과의 대응</h2>
 * <p>{@code Compliance.tsx} 는 프로젝트를 고르면 그 프로젝트의 변경 내역을,
 * 그 안에서 장치를 고르면 장치 단위 내역을 보여줍니다. 그래서 조회 축을
 * {@code project_id} 와 {@code agent_id} 두 개로 제공합니다.
 *
 * <pre>
 *   GET /api/v1/compliance/changes                       전체
 *   GET /api/v1/compliance/changes?project_id=PRJ-...    프로젝트 단위
 *   GET /api/v1/compliance/changes?agent_id=AGT-0001     장치 단위
 * </pre>
 *
 * <p>둘 다 주면 장치 축이 우선입니다. (더 좁은 범위)
 */
@RestController
@RequestMapping("/api/v1/compliance")
public class ComplianceController {

    private final ComplianceService complianceService;

    /**
     * @param complianceService 변경 이력 서비스
     */
    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    /**
     * 변경 이력을 조회합니다.
     *
     * @param projectId 프로젝트 키 (선택)
     * @param agentId   장치 식별자 (선택, 있으면 우선)
     * @return {@code {"total": n, "changes": [...]}}
     */
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

    /**
     * 특정 프로젝트의 변경 이력을 조회합니다.
     *
     * @param projectId 프로젝트 키
     * @return {@code {"total": n, "changes": [...]}}
     */
    @GetMapping("/changes/project/{projectId}")
    public Map<String, Object> byProject(@PathVariable String projectId) {
        final List<ComplianceChange> changes = complianceService.byProject(projectId);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("project_id", projectId);
        body.put("total", changes.size());
        body.put("changes", ComplianceService.toResponse(changes));
        return body;
    }

    /**
     * 특정 장치의 변경 이력을 조회합니다.
     *
     * @param agentId 장치 식별자
     * @return {@code {"total": n, "changes": [...]}}
     */
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
