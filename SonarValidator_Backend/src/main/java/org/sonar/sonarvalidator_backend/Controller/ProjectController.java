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

/**
 * 프로젝트 편집 기능의 REST 진입점입니다.
 *
 * <h2>프론트엔드 흐름과의 대응</h2>
 * <pre>
 *   /project                    list()            프로젝트 목록
 *   /project/create             create()          프로젝트 생성 (키 발급)
 *   /project/create/subnet      update()          서브넷 등급 지정 저장
 *   /project/create/segmentation update + validate 규칙 저장 + 검증
 *   /project/create/preview     validate()        저장 없이 미리보기 검증
 * </pre>
 *
 * <p>편집기가 화면 상태를 통째로 보내므로 수정은 {@link PutMapping} 하나로
 * 충분합니다. 부분 수정 API 는 두지 않았습니다 — 계약이 단순해지고,
 * "화면에 보이는 그대로" 저장된다는 보장이 생깁니다.
 *
 * <p>모든 응답 키는 snake_case 입니다. (기존 {@code /api/v1/agents} 와 동일)
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    /**
     * @param projectService 프로젝트 서비스
     */
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 프로젝트 목록을 반환합니다.
     *
     * @return {@code {"total": n, "projects": [...]}}
     */
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

    /**
     * 프로젝트 1건을 조회합니다. (서브넷/규칙 포함)
     *
     * @param projectId 프로젝트 키
     * @return 프로젝트 상세
     */
    @GetMapping("/{projectId}")
    public Map<String, Object> get(@PathVariable String projectId) {
        return ProjectMapper.toResponse(projectService.getByKey(projectId));
    }

    /**
     * 프로젝트를 생성합니다.
     *
     * <p>본문이 비어 있어도 동작합니다. (이름 없는 초안 프로젝트)
     *
     * @param body 생성 요청 (null 허용)
     * @return 생성된 프로젝트
     */
    @PostMapping
    public Map<String, Object> create(@RequestBody(required = false) ProjectDto.CreateRequest body) {
        final ProjectDto.CreateRequest request = body == null
                ? new ProjectDto.CreateRequest(null, null, null, null, null)
                : body;
        return ProjectMapper.toResponse(projectService.create(request));
    }

    /**
     * 프로젝트 메타데이터와 정책(서브넷/규칙)을 수정합니다.
     *
     * @param projectId 프로젝트 키
     * @param body      수정 요청
     * @return 수정된 프로젝트
     */
    @PutMapping("/{projectId}")
    public Map<String, Object> update(@PathVariable String projectId,
                                      @RequestBody ProjectDto.UpdateRequest body) {
        final ProjectDto.UpdateRequest request = body == null
                ? new ProjectDto.UpdateRequest(null, null, null, null, null, null)
                : body;
        return ProjectMapper.toResponse(projectService.update(projectId, request));
    }

    /**
     * 프로젝트를 삭제합니다.
     *
     * @param projectId 프로젝트 키
     * @return {@code {"deleted": true}}
     */
    @DeleteMapping("/{projectId}")
    public Map<String, Object> delete(@PathVariable String projectId) {
        projectService.delete(projectId);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", true);
        body.put("project_id", projectId);
        return body;
    }

    /**
     * 저장된 프로젝트를 망분리 정책으로 검증합니다.
     *
     * @param projectId 프로젝트 키
     * @return 검증 보고서
     */
    @GetMapping("/{projectId}/validation")
    public Map<String, Object> validateStored(@PathVariable String projectId) {
        return ProjectMapper.toValidationResponse(projectService.validateStored(projectId));
    }

    /**
     * 저장하지 않고 전달받은 정책만 검증합니다.
     *
     * <p>편집기에서 "Save" 를 누르기 전 위반 여부를 확인하는 용도입니다.
     * 요청 본문이 없으면 저장된 프로젝트 상태를 검증합니다.
     *
     * @param projectId 프로젝트 키
     * @param body      검증 요청 (null 이면 저장된 상태 사용)
     * @return 검증 보고서
     */
    @PostMapping("/{projectId}/validation")
    public Map<String, Object> validate(@PathVariable String projectId,
                                        @RequestBody(required = false) ProjectDto.ValidateRequest body) {
        final SegmentationBddEngine.Report report = (body == null
                || (body.subnets() == null && body.rules() == null))
                ? projectService.validateStored(projectId)
                : projectService.validate(body);
        return ProjectMapper.toValidationResponse(report);
    }

    /**
     * 등급을 건너뛰는 서브넷 쌍(금지 연결 목록)을 반환합니다.
     *
     * <p>편집기가 "왜 이 조합이 금지인지" 를 미리 보여줄 때 씁니다. 규칙 검증과
     * 별개로 정책 자체를 설명하는 정적 정보입니다.
     *
     * @param projectId 프로젝트 키
     * @return 금지 쌍 목록
     */
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

    /**
     * 저장하지 않고 전달받은 정책만 검증합니다. (프로젝트와 무관)
     *
     * <p>프로젝트 편집기의 각 단계가 저장 전에 검증을 미리 돌릴 때 씁니다.
     * 경로가 {@code /draft/validation} 인 이유는 {@code /{projectId}} 패턴과
     * 충돌하지 않게 하기 위함입니다. ({@code draft} 를 프로젝트 키로 오인하지
     * 않도록 별도 매핑으로 분리)
     *
     * @param body 검증 요청
     * @return 검증 보고서
     */
    @PostMapping("/draft/validation")
    public Map<String, Object> validateDraft(@RequestBody ProjectDto.ValidateRequest body) {
        final ProjectDto.ValidateRequest request = body == null
                ? new ProjectDto.ValidateRequest(List.of(), List.of())
                : body;
        return ProjectMapper.toValidationResponse(projectService.validate(request));
    }
}
