package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.sonar.sonarvalidator_backend.Model.dto.ProjectDto;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Policy.PolicyRule;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicyViolation;
import org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트의 생성/조회/수정과 망분리 검증을 담당합니다.
 *
 * <h2>검증이 서비스에 있는 이유</h2>
 * <p>프론트엔드도 저장 시점에 같은 규칙으로 즉시 피드백을 줍니다. 그러나
 * <b>최종 판정은 서버가 해야</b> 합니다. 프론트엔드 검증은 우회할 수 있고,
 * 자동 수집된 실제 설정(방화벽 규칙)을 반영하지 못하기 때문입니다.
 * 그래서 "위반 여부"는 이 서비스가 {@link SegmentationBddEngine} 으로 계산합니다.
 *
 * <h2>경계 사례 처리</h2>
 * <ul>
 *   <li>대역이 잘못된 서브넷은 검증에서 제외하고 위반 목록에 MINOR 로 남깁니다.
 *       → 사용자가 어느 항목을 고쳐야 하는지 알 수 있습니다.</li>
 *   <li>프로젝트가 없으면 {@link ProjectNotFoundException} 을 던져 404 로 매핑합니다.</li>
 * </ul>
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    /** 검증에서 제외할 규칙을 표시하는 값. */
    private static final int DEFAULT_PORT = PacketVariables.ANY_PORT;

    private final ProjectRepository repository;
    private final SegmentationBddEngine engine = new SegmentationBddEngine();

    /**
     * 변경 이력 기록기입니다.
     *
     * <p>검증만 하는 단위 테스트에서 매번 목을 만들지 않도록 생성자에서
     * {@code null} 을 허용하고, 기록 지점에서만 확인합니다.
     */
    private final ComplianceService complianceService;

    /**
     * 알림 기록기입니다.
     *
     * <p>{@code complianceService} 와 같은 이유로 {@code null} 을 허용합니다.
     * 프로젝트 변경처럼 <b>운영자가 나중에 확인해야 하는 사건</b>을 알림으로도
     * 남깁니다. 변경 이력은 "무엇이 바뀌었나" 를, 알림은 "언제 알려졌나" 를
     * 담당하므로 목적이 다릅니다.
     */
    private final NotificationService notificationService;

    /**
     * @param repository          프로젝트 저장소
     * @param complianceService   변경 이력 서비스 (null 허용 — 테스트 편의)
     * @param notificationService 알림 서비스 (null 허용 — 테스트 편의)
     */
    public ProjectService(ProjectRepository repository,
                          ComplianceService complianceService,
                          NotificationService notificationService) {
        this.repository = repository;
        this.complianceService = complianceService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------
    //  조회
    // ------------------------------------------------------------------

    /**
     * 전체 프로젝트를 최신순으로 조회합니다.
     *
     * @return 프로젝트 목록
     */
    @Transactional(readOnly = true)
    public List<Project> listAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 프로젝트 키로 조회합니다.
     *
     * @param projectKey 외부 키
     * @return 프로젝트
     * @throws ProjectNotFoundException 없을 때
     */
    @Transactional(readOnly = true)
    public Project getByKey(String projectKey) {
        return repository.findByProjectKey(projectKey)
                .orElseThrow(() -> new ProjectNotFoundException(projectKey));
    }

    // ------------------------------------------------------------------
    //  생성 / 수정
    // ------------------------------------------------------------------

    /**
     * 프로젝트를 만듭니다.
     *
     * <p>키가 없으면 서버가 생성합니다. 프론트엔드는 보통
     * {@code Math.random} 기반 키를 만들어 보내지만, 그 값을 신뢰할 수 없으므로
     * 중복이면 새로 발급합니다.
     *
     * @param request 생성 요청
     * @return 저장된 프로젝트
     */
    @Transactional
    public Project create(ProjectDto.CreateRequest request) {
        final Project project = new Project();
        project.setProjectKey(resolveKey(request.projectId()));
        project.setName(defaultIfBlank(request.name(), "Untitled Project"));
        project.setCategory(request.category());
        project.setDescription(request.description());
        project.setStatus(defaultIfBlank(request.status(), "Planning"));

        final java.util.Date now = new java.util.Date();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);

        final Project saved = repository.save(project);
        log.info("project created: key={} name={}", saved.getProjectKey(), saved.getName());

        if (notificationService != null) {
            notificationService.notifyQuietly(
                    "PROJECT",
                    "info",
                    "프로젝트 생성: " + saved.getName(),
                    "카테고리 " + (saved.getCategory() == null ? "-" : saved.getCategory())
                            + ", 상태 " + saved.getStatus(),
                    saved.getProjectKey(),
                    null,
                    "system",
                    "/project/editor/" + saved.getProjectKey(),
                    null);
        }
        return saved;
    }

    /**
     * 프로젝트 메타데이터와 정책(서브넷/규칙)을 수정합니다.
     *
     * <p>null 인 필드는 변경하지 않습니다. 서브넷/규칙 목록을 주면
     * <b>전부 교체</b> 합니다. (편집기가 화면 상태를 통째로 보내는 흐름과 일치)
     *
     * @param projectKey 외부 키
     * @param request    수정 요청
     * @return 수정된 프로젝트
     * @throws ProjectNotFoundException 없을 때
     */
    @Transactional
    public Project update(String projectKey, ProjectDto.UpdateRequest request) {
        final Project project = getByKey(projectKey);

        if (request.name() != null && !request.name().isBlank()) {
            project.setName(request.name().trim());
        }
        if (request.category() != null) {
            project.setCategory(request.category());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        if (request.status() != null && !request.status().isBlank()) {
            project.setStatus(request.status().trim());
        }

        final List<PolicySubnet> subnets = request.subnets() == null
                ? null
                : mapSubnets(request.subnets());
        final List<PolicyRule> rules = request.rules() == null
                ? null
                : mapRules(request.rules());

        project.replacePolicy(subnets, rules);
        project.setUpdatedAt(new java.util.Date());

        final Project saved = repository.save(project);
        log.info("project updated: key={} subnets={} rules={}",
                saved.getProjectKey(), saved.getSubnets().size(), saved.getRules().size());

        if (complianceService != null) {
            complianceService.recordQuietly(
                    "Project",
                    saved.getProjectKey(),
                    null,
                    "Policy Update",
                    "프로젝트 정책 수정 (서브넷 " + saved.getSubnets().size()
                            + "건, 규칙 " + saved.getRules().size() + "건)",
                    "system",
                    null);
        }

        // 정책 변경은 나중에 "언제 무엇을 고쳤나" 를 확인해야 하는 사건이므로
        // 알림으로도 남깁니다. dedupeKey 를 두지 않는 이유: 정책 수정은 운영자가
        // 의도해서 하는 행동이고, 반복돼도 합치면 이력을 잃습니다.
        if (notificationService != null) {
            notificationService.notifyQuietly(
                    "POLICY",
                    "info",
                    "정책 수정: " + saved.getName(),
                    "서브넷 " + saved.getSubnets().size() + "건, 규칙 "
                            + saved.getRules().size() + "건으로 갱신되었습니다.",
                    saved.getProjectKey(),
                    null,
                    "system",
                    "/project/editor/" + saved.getProjectKey(),
                    null);
        }

        // ⚠️ 저장 직후 <b>위반을 다시 판정</b>해 경고를 만듭니다.
        //   저장이 성공했다고 정책이 옳은 것은 아닙니다. 등급을 건너뛰는 연결이
        //   저장되면 그 상태로 푸시가 거부되고, 장치에는 아무 정책도 내려가지
        //   않습니다. 운영자는 "저장됐다" 는 성공 메시지만 보고 넘어갑니다.
        //
        //   그래서 저장 자체는 막지 않되(초안 작업을 방해하지 않기 위해)
        //   위험한 상태를 <b>즉시 알립니다.</b>
        warnOnViolations(saved);

        return saved;
    }

    /**
     * 저장된 프로젝트의 위반을 판정하고, 위험한 위반에 대해 경고를 만듭니다.
     *
     * <h2>⚠️ 왜 CRITICAL 만 경고하는가</h2>
     * <p>{@code MAJOR}(포트 미지정)는 "검토 대상" 이지 즉시 위험한 상태가
     * 아닙니다. 랩의 모든 규칙에 {@code any} 포트를 쓰면 매 저장마다 경고가
     * 쏟아지고, 그러면 진짜 {@code CRITICAL} 이 그 속에 묻힙니다.
     * {@code CRITICAL} 은 <b>등급을 건너뛰는 직접 연결</b> — 망분리의 정의를
     * 정면으로 어기는 유일한 경우입니다.
     *
     * <h2>dedupeKey 를 프로젝트+건수로 두는 이유</h2>
     * <p>같은 프로젝트에서 위반 건수가 그대로인데 저장만 반복하면 알림을
     * 합칩니다(5분 창). 반면 <b>건수가 바뀌면 새 알림</b>입니다 —
     * 위반을 하나 더 만들었는지 / 하나 고쳤는지가 구분되어야 합니다.
     *
     * @param project 저장된 프로젝트
     */
    private void warnOnViolations(Project project) {
        final SegmentationBddEngine.Report report;
        try {
            report = engine.validate(project.toPolicySubnets(), project.toPolicyRules());
        } catch (RuntimeException ex) {
            // 판정 실패가 저장 결과를 뒤집으면 안 됩니다. 저장은 이미 성공했습니다.
            log.warn("post-save violation check failed for project={}: {}",
                    project.getProjectKey(), ex.getMessage());
            return;
        }

        final List<PolicyViolation> criticals = new ArrayList<>();
        for (final PolicyViolation violation : report.getViolations()) {
            if (violation.severity() == PolicyViolation.Severity.CRITICAL) {
                criticals.add(violation);
            }
        }
        if (criticals.isEmpty()) {
            return;
        }

        // 사람이 읽는 요약: 어떤 등급이 어디로 건너뛰는지 한 줄로 보여줍니다.
        final StringBuilder summary = new StringBuilder();
        final Set<String> distinct = new LinkedHashSet<>();
        for (final PolicyViolation violation : criticals) {
            final String label = label(violation.sourceZone()) + " → " + label(violation.targetZone());
            distinct.add(label);
        }
        int shown = 0;
        for (final String label : distinct) {
            if (shown++ > 0) {
                summary.append(", ");
            }
            summary.append(label);
            if (shown >= 4) {
                break;
            }
        }

        log.warn("CSO violation detected: project={} critical={} pairs={}",
                project.getProjectKey(), criticals.size(), summary);

        if (notificationService == null) {
            return;
        }

        notificationService.notifyQuietly(
                // SECURITY 로 두는 이유: 망분리 위반은 보안 사건입니다.
                // POLICY 는 "정책을 고쳤다" 는 운영 행위 알림이라 성격이 다릅니다.
                "SECURITY",
                "critical",
                "망분리 위반 " + criticals.size() + "건: " + project.getName(),
                "등급을 건너뛰는 직접 연결이 정책에 포함되어 있습니다 (" + summary + "). "
                        + "Confidential 과 Open 은 직접 연결할 수 없습니다. "
                        + "이 상태로는 정책 푸시가 거부됩니다.",
                project.getProjectKey(),
                null,
                "policy",
                "/policy?project_id=" + project.getProjectKey(),
                "cso-violation:" + project.getProjectKey() + ":" + criticals.size());
    }

    /**
     * 등급을 화면 표기로 바꿉니다. (요약 문장용)
     *
     * @param zone 등급 (null 허용)
     * @return 표기 문자열
     */
    private static String label(ZoneClass zone) {
        return zone == null ? "미지정" : zone.label();
    }

    /**
     * 프로젝트를 삭제합니다.
     *
     * @param projectKey 외부 키
     * @throws ProjectNotFoundException 없을 때
     */
    @Transactional
    public void delete(String projectKey) {
        final Project project = getByKey(projectKey);
        final String name = project.getName();
        repository.delete(project);
        log.info("project deleted: key={}", projectKey);

        // 삭제는 되돌릴 수 없으므로 warning 으로 남깁니다.
        // (info 로 두면 목록에서 묻혀 "그 프로젝트 어디 갔지" 를 놓칩니다)
        if (notificationService != null) {
            notificationService.notifyQuietly(
                    "PROJECT",
                    "warning",
                    "프로젝트 삭제: " + (name == null ? projectKey : name),
                    "프로젝트 " + projectKey + " 가 삭제되었습니다.",
                    projectKey,
                    null,
                    "system",
                    "/project",
                    null);
        }
    }

    // ------------------------------------------------------------------
    //  검증
    // ------------------------------------------------------------------

    /**
     * 저장된 프로젝트의 정책을 검증합니다.
     *
     * @param projectKey 외부 키
     * @return 검증 보고서
     * @throws ProjectNotFoundException 없을 때
     */
    @Transactional(readOnly = true)
    public SegmentationBddEngine.Report validateStored(String projectKey) {
        final Project project = getByKey(projectKey);
        return engine.validate(project.toPolicySubnets(), project.toPolicyRules());
    }

    /**
     * 저장하지 않고 전달받은 정책만 검증합니다. (편집기 실시간 미리보기용)
     *
     * @param request 검증 요청
     * @return 검증 보고서
     */
    public SegmentationBddEngine.Report validate(ProjectDto.ValidateRequest request) {
        return engine.validate(mapSubnets(request.subnets()), mapRules(request.rules()));
    }

    /**
     * 등급을 건너뛰는 서브넷 쌍 목록을 반환합니다.
     *
     * <p>프로젝트 편집기가 "어떤 조합이 애초에 금지인지" 를 안내할 때 씁니다.
     *
     * @param subnets 서브넷 목록
     * @return 금지 쌍 목록 (출발 식별자, 도착 식별자)
     */
    public List<String[]> forbiddenPairs(List<PolicySubnet> subnets) {
        return engine.forbiddenPairs(subnets);
    }

    // ------------------------------------------------------------------
    //  내부 헬퍼
    // ------------------------------------------------------------------

    /**
     * 프로젝트 키를 확정합니다. 비어 있거나 중복이면 새로 만듭니다.
     *
     * @param candidate 클라이언트가 보낸 키
     * @return 사용 가능한 키
     */
    private String resolveKey(String candidate) {
        if (candidate != null && !candidate.isBlank()
                && !repository.existsByProjectKey(candidate.trim())) {
            return candidate.trim();
        }
        String generated;
        do {
            generated = "PRJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (repository.existsByProjectKey(generated));
        return generated;
    }

    /**
     * DTO 서브넷 목록을 도메인 객체로 바꿉니다. 등급이 없으면 OPEN 으로 둡니다.
     *
     * @param payloads 요청 서브넷 목록 (null 허용)
     * @return 도메인 서브넷 목록
     */
    private List<PolicySubnet> mapSubnets(List<ProjectDto.SubnetPayload> payloads) {
        final List<PolicySubnet> result = new ArrayList<>();
        if (payloads == null) {
            return result;
        }
        int sequence = 0;
        for (final ProjectDto.SubnetPayload payload : payloads) {
            if (payload == null) {
                continue;
            }
            sequence++;
            final PolicySubnet subnet = payload.toPolicySubnet();
            if (subnet.getId() == null || subnet.getId().isBlank()) {
                subnet.setId("Subnet-" + String.format("%04d", sequence));
            }
            if (subnet.getZoneClass() == null) {
                subnet.setZoneClass(ZoneClass.OPEN);
            }
            result.add(subnet);
        }
        return result;
    }

    /**
     * DTO 규칙 목록을 도메인 객체로 바꿉니다. 식별자가 없으면 채번합니다.
     *
     * @param payloads 요청 규칙 목록 (null 허용)
     * @return 도메인 규칙 목록
     */
    private List<PolicyRule> mapRules(List<ProjectDto.RulePayload> payloads) {
        final List<PolicyRule> result = new ArrayList<>();
        if (payloads == null) {
            return result;
        }
        int sequence = 0;
        for (final ProjectDto.RulePayload payload : payloads) {
            if (payload == null) {
                continue;
            }
            sequence++;
            final PolicyRule rule = payload.toPolicyRule();
            if (rule.getId() == null || rule.getId().isBlank()) {
                rule.setId("Rule-" + String.format("%04d", sequence));
            }
            if (rule.getPort() == DEFAULT_PORT && payload.port() == null) {
                rule.setPort(DEFAULT_PORT);
            }
            result.add(rule);
        }
        return result;
    }

    /** null/공백이면 대체값을 돌려줍니다. */
    private String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    /** 프로젝트를 찾지 못했을 때 던지는 예외입니다. (404 로 매핑) */
    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class ProjectNotFoundException extends RuntimeException {
        /**
         * @param projectKey 찾지 못한 키
         */
        public ProjectNotFoundException(String projectKey) {
            super("project not found: " + projectKey);
        }
    }
}
