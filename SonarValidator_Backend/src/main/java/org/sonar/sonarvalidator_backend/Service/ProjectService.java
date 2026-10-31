package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private static final int DEFAULT_PORT = PacketVariables.ANY_PORT;

    private static final String DEFAULT_ROUTE_PREFIX = "0.0.0.0/0";

    private final ProjectRepository repository;
    private final SegmentationBddEngine engine = new SegmentationBddEngine();

    private AgentMessageRouterService router;

    @Autowired(required = false)
    public void setAgentRouter(AgentMessageRouterService router) {
        this.router = router;
    }

    private final ComplianceService complianceService;

    private final NotificationService notificationService;

    public ProjectService(ProjectRepository repository,
                          ComplianceService complianceService,
                          NotificationService notificationService) {
        this.repository = repository;
        this.complianceService = complianceService;
        this.notificationService = notificationService;
    }

    //  조회

    @Transactional(readOnly = true)
    public List<Project> listAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Project getByKey(String projectKey) {
        return repository.findByProjectKey(projectKey)
                .orElseThrow(() -> new ProjectNotFoundException(projectKey));
    }

    //  생성 / 수정

    @Transactional
    public Project create(ProjectDto.CreateRequest request) {
        final Project project = new Project();
        project.setProjectKey(resolveKey(request.projectId()));
        project.setName(defaultIfBlank(request.name(), "Untitled Project"));
        project.setCategory(request.category());
        project.setDescription(request.description());
        project.setStatus(defaultIfBlank(request.status(), "Planning"));
        // ⚠️ 제어평면 대역은 프로젝트마다 다를 수 있습니다 (요구사항).
        //   지정하지 않으면 전역 기본값(sonar.site.management-prefix)을 씁니다.
        project.setManagementPrefix(blankToNull(request.managementPrefix()));

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
        // 제어평면 대역은 "변경 없음(null)" 과 "지우기(빈 값)" 을 구분합니다.
        // ⚠️ null 이면 유지, 빈 문자열이면 전역 기본값으로 되돌립니다.
        if (request.managementPrefix() != null) {
            project.setManagementPrefix(blankToNull(request.managementPrefix()));
        }
        if (request.managementServerIp() != null) {
            project.setManagementServerIp(blankToNull(request.managementServerIp()));
        }
        if (request.managementServerPort() != null) {
            final int port = request.managementServerPort();
            if (port < 1 || port > 65535) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "management_server_port must be between 1 and 65535");
            }
            project.setManagementServerPort(port);
        }

        final List<PolicySubnet> subnets = request.subnets() == null
                ? null
                : mapSubnets(request.subnets());
        final List<PolicyRule> rules = request.rules() == null
                ? null
                : mapRules(request.rules());

        checkEntries(subnets, PolicySubnet::getId, "subnet");
        checkEntries(rules, PolicyRule::getId, "rule");
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

        warnOnViolations(saved);

        return saved;
    }

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

    private static String label(ZoneClass zone) {
        return zone == null ? "미지정" : zone.label();
    }

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

    //  검증

    @Transactional(readOnly = true)
    public SegmentationBddEngine.Report validateStored(String projectKey) {
        final Project project = getByKey(projectKey);
        return engine.validate(project.toPolicySubnets(), project.toPolicyRules(),
                internetExposedAgents());
    }

    private Set<String> internetExposedAgents() {
        if (router == null) {
            return Set.of();
        }
        // 판정 규칙은 ObservedConnectivity 한 곳에 둡니다 — 토폴로지가 같은
        // 함수로 인터넷 간선을 그리므로 화면과 검증이 어긋나지 않습니다.
        return ObservedConnectivity.internetExposedAgents(router.allConfigs());
    }

    public SegmentationBddEngine.Report validate(ProjectDto.ValidateRequest request) {
        return engine.validate(mapSubnets(request.subnets()), mapRules(request.rules()),
                internetExposedAgents());
    }

    public List<String[]> forbiddenPairs(List<PolicySubnet> subnets) {
        return engine.forbiddenPairs(subnets);
    }

    //  내부 헬퍼

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

    private static <T> void checkEntries(List<T> entries, java.util.function.Function<T, String> idOf, String kind) {
        if (entries == null) return;
        final Set<String> seen = new LinkedHashSet<>();
        for (final T entry : entries) {
            if (entry == null || !seen.add(idOf.apply(entry).trim())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, kind + " entries must be non-null with unique IDs");
            }
        }
    }

    private List<PolicySubnet> mapSubnets(List<ProjectDto.SubnetPayload> payloads) {
        final List<PolicySubnet> result = new ArrayList<>();
        if (payloads == null) {
            return result;
        }
        int sequence = 0;
        for (final ProjectDto.SubnetPayload payload : payloads) {
            if (payload == null) {
                result.add(null);
                continue;
            }
            sequence++;
            final PolicySubnet subnet = payload.toPolicySubnet();
            if (subnet.getId() == null || subnet.getId().isBlank()) {
                subnet.setId("Subnet-" + String.format("%04d", sequence));
            }
            // A missing CSO class remains unclassified until the operator chooses it.
            result.add(subnet);
        }
        return result;
    }

    private List<PolicyRule> mapRules(List<ProjectDto.RulePayload> payloads) {
        final List<PolicyRule> result = new ArrayList<>();
        if (payloads == null) {
            return result;
        }
        int sequence = 0;
        for (final ProjectDto.RulePayload payload : payloads) {
            if (payload == null) {
                result.add(null);
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

    private String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.NOT_FOUND)
    public static class ProjectNotFoundException extends RuntimeException {
        public ProjectNotFoundException(String projectKey) {
            super("project not found: " + projectKey);
        }
    }
}
