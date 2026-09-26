package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonar.sonarvalidator_backend.support.StubRepository.UNHANDLED;
import static org.sonar.sonarvalidator_backend.support.StubRepository.of;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.dto.ProjectDto;
import org.sonar.sonarvalidator_backend.Model.entity.Notification;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine;
import org.sonar.sonarvalidator_backend.Repository.ComplianceChangeRepository;
import org.sonar.sonarvalidator_backend.Repository.NotificationRepository;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.sonar.sonarvalidator_backend.Service.ComplianceService;
import org.sonar.sonarvalidator_backend.Service.NotificationService;
import org.sonar.sonarvalidator_backend.Service.ProjectService;

/**
 * CSO 정책 위반(등급 건너뛰기) 감지와 경고 발생을 검증합니다.
 *
 * <h2>검증 대상</h2>
 * 프로젝트를 저장했을 때, <b>Confidential 과 Open 을 직접 연결</b>하는 규칙이
 * 있으면 서버가 그것을 CRITICAL 로 판정하고 경고 알림을 만들어야 합니다.
 *
 * <h2>⚠️ 왜 이 테스트가 필요한가</h2>
 * 이 경로가 없던 동안, 위반이 있는 정책을 저장해도 알림이 <b>하나도 생기지
 * 않았습니다.</b> 저장이 성공했다는 이유로 화면에는 정상 저장으로 보였고,
 * 운영자는 나중에 푸시가 거부될 때서야 위반을 알게 됩니다.
 *
 * <p>랩에서 실측한 결과:
 * <pre>
 *   PUT /api/v1/projects/poc-dai-pbl   (Confidential -> Open 규칙 추가)
 *     -> compliant=false, by_severity={CRITICAL:1, MAJOR:5}
 *     -> SECURITY/critical 알림 생성 (이 테스트가 지키는 동작)
 * </pre>
 */
class CsoViolationWarningTest {

    private final List<Notification> notifications = new ArrayList<>();
    private final List<Project> projects = new ArrayList<>();

    private ProjectService service;

    @BeforeEach
    void setUp() {
        notifications.clear();
        projects.clear();

        // 프로젝트 저장소: 저장된 프로젝트를 키로 찾을 수 있어야 합니다.
        final ProjectRepository projectRepository = of(
                ProjectRepository.class,
                (method, args) -> switch (method) {
                    case "save" -> {
                        final Project project = (Project) args[0];
                        projects.removeIf(p -> p.getProjectKey().equals(project.getProjectKey()));
                        projects.add(project);
                        yield project;
                    }
                    case "findAllByOrderByCreatedAtDesc" -> List.copyOf(projects);
                    case "findByProjectKey" -> projects.stream()
                            .filter(p -> args[0] != null && args[0].equals(p.getProjectKey()))
                            .findFirst();
                    default -> UNHANDLED;
                });

        final ComplianceChangeRepository complianceRepository = of(
                ComplianceChangeRepository.class, (method, args) -> UNHANDLED);

        final NotificationRepository notificationRepository = of(
                NotificationRepository.class,
                (method, args) -> switch (method) {
                    case "save" -> {
                        notifications.add((Notification) args[0]);
                        yield args[0];
                    }
                    case "findFirstByDedupeKeyOrderByOccurredAtDesc" -> {
                        if (args[0] == null) {
                            yield Optional.empty();
                        }
                        yield notifications.stream()
                                .filter(n -> args[0].equals(n.getDedupeKey()))
                                .findFirst();
                    }
                    default -> UNHANDLED;
                });

        service = new ProjectService(
                projectRepository,
                new ComplianceService(complianceRepository),
                new NotificationService(notificationRepository));
    }

    // ------------------------------------------------------------------
    //  위반 감지
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Confidential 과 Open 을 직접 연결하면 CRITICAL 위반이다")
    void confidentialToOpenIsCritical() {
        final Project project = saveProject("PRJ-CSO",
                List.of(subnet("S-C", "10.10.131.0/24", "Confidential"),
                        subnet("S-O", "10.30.141.0/24", "Open")),
                List.of(rule("R-1", "S-C", "S-O")));

        final SegmentationBddEngine.Report report = service.validateStored(project.getProjectKey());

        assertEquals(1, report.getViolationCount(), "위반 1건");
        final var violation = report.getViolations().get(0);
        assertEquals("CRITICAL", violation.severity().name(),
                "한 단계를 건너뛰는 연결은 즉시 조치 대상입니다");

        // 사유는 "Subnet A -> Subnet B 연결은 허용되지 않습니다" 형식이어야 합니다.
        // 운영자는 이 문장만 보고 어느 서브넷을 고칠지 결정합니다.
        assertTrue(violation.reason().contains("->"),
                "연결 경로(출발 -> 도착)가 있어야 합니다: " + violation.reason());
        assertTrue(violation.reason().contains("허용되지 않습니다"),
                "금지 사실이 문장으로 드러나야 합니다: " + violation.reason());
        assertTrue(violation.reason().contains("10.10.131.0/24"),
                "출발 대역이 지목되어야 합니다: " + violation.reason());
        assertTrue(violation.reason().contains("10.30.141.0/24"),
                "도착 대역이 지목되어야 합니다: " + violation.reason());
        // 등급 차이도 남아야 "왜" 를 알 수 있습니다.
        assertTrue(violation.reason().contains("Confidential"), violation.reason());
        assertTrue(violation.reason().contains("Open"), violation.reason());
    }

    @Test
    @DisplayName("인접 등급 연결은 위반이 아니다")
    void adjacentZonesAreAllowed() {
        // Confidential(3) - Sensitive(2) - Open(1) 은 인접하므로 허용입니다.
        // 이걸 위반으로 잡으면 정상 정책이 전부 막힙니다.
        final Project project = saveProject("PRJ-OK",
                List.of(subnet("S-C", "10.10.131.0/24", "Confidential"),
                        subnet("S-S", "10.20.111.0/24", "Sensitive"),
                        subnet("S-O", "10.30.141.0/24", "Open")),
                List.of(ruleWithPort("R-1", "S-C", "S-S", 443),
                        ruleWithPort("R-2", "S-S", "S-O", 80)));

        final SegmentationBddEngine.Report report = service.validateStored(project.getProjectKey());

        assertEquals(0, criticalCount(report), "인접 등급은 CRITICAL 위반이 아닙니다");
    }

    @Test
    @DisplayName("2단계 건너뛰기는 양방향 모두 위반이다")
    void skipIsViolationInBothDirections() {
        final Project project = saveProject("PRJ-BOTH",
                List.of(subnet("S-C", "10.10.131.0/24", "Confidential"),
                        subnet("S-O", "10.30.141.0/24", "Open")),
                List.of(rule("R-1", "S-C", "S-O"), rule("R-2", "S-O", "S-C")));

        final SegmentationBddEngine.Report report = service.validateStored(project.getProjectKey());

        assertEquals(2, criticalCount(report), "방향이 달라도 각각 위반입니다");
    }

    // ------------------------------------------------------------------
    //  경고 발생
    // ------------------------------------------------------------------

    @Test
    @DisplayName("위반이 있는 정책을 저장하면 SECURITY/critical 경고가 생성된다")
    void savingViolationRaisesCriticalWarning() {
        saveProject("PRJ-WARN",
                List.of(subnet("S-C", "10.10.131.0/24", "Confidential"),
                        subnet("S-O", "10.30.141.0/24", "Open")),
                List.of(rule("R-1", "S-C", "S-O")));

        final Notification cso = findCso(notifications);
        assertNotNull(cso, "CSO 위반 경고가 있어야 합니다. 실제: " + titles(notifications));
        assertEquals("SECURITY", cso.getCategory(),
                "망분리 위반은 보안 사건입니다 (POLICY 는 운영 행위 알림)");
        assertEquals("critical", cso.getSeverity());
        assertTrue(cso.getTitle().contains("망분리 위반"),
                "제목에 종류가 드러나야 합니다: " + cso.getTitle());
        assertTrue(cso.getMessage().contains("Confidential"),
                "메시지에 어느 등급이 문제인지 있어야 합니다");
        assertTrue(cso.getMessage().contains("Open"), "Open 도 명시");
        assertEquals("/policy?project_id=PRJ-WARN", cso.getLink(),
                "클릭하면 그 프로젝트의 위반 화면으로 가야 합니다");
        assertEquals("cso-violation:PRJ-WARN:1", cso.getDedupeKey());
    }

    @Test
    @DisplayName("위반이 없으면 CSO 경고를 만들지 않는다")
    void compliantProjectRaisesNoWarning() {
        saveProject("PRJ-CLEAN",
                List.of(subnet("S-C", "10.10.131.0/24", "Confidential"),
                        subnet("S-S", "10.20.111.0/24", "Sensitive")),
                List.of(ruleWithPort("R-1", "S-C", "S-S", 443)));

        assertEquals(null, findCso(notifications),
                "통과한 정책에 경고를 만들면 알림이 무의미해집니다");
    }

    @Test
    @DisplayName("MAJOR 위반(포트 미지정)만 있으면 CSO 경고를 만들지 않는다")
    void majorOnlyDoesNotRaiseCsoWarning() {
        // 포트 미지정은 "검토 대상" 입니다. 이것도 경고하면 랩의 모든 규칙이
        // any 포트를 쓰는 환경에서 경고가 쏟아져 진짜 CRITICAL 이 묻힙니다.
        saveProject("PRJ-MAJOR",
                List.of(subnet("S-C", "10.10.131.0/24", "Confidential"),
                        subnet("S-S", "10.20.111.0/24", "Sensitive")),
                List.of(rule("R-1", "S-C", "S-S")));

        assertEquals(null, findCso(notifications),
                "MAJOR 만으로는 경고하지 않습니다: " + titles(notifications));
    }

    @Test
    @DisplayName("위반 건수가 바뀌면 새 경고를 만든다")
    void warningReflectsViolationCount() {
        final List<ProjectDto.SubnetPayload> subnets = List.of(
                subnet("S-C", "10.10.131.0/24", "Confidential"),
                subnet("S-O", "10.30.141.0/24", "Open"));

        saveProject("PRJ-N", subnets, List.of(rule("R-1", "S-C", "S-O")));
        saveProject("PRJ-N", subnets, List.of(rule("R-1", "S-C", "S-O"), rule("R-2", "S-O", "S-C")));

        // dedupeKey 에 건수를 넣은 이유: 위반을 하나 더 만들었는지 /
        // 하나 고쳤는지가 알림에서 구분되어야 합니다.
        assertTrue(notifications.stream().anyMatch(n -> "cso-violation:PRJ-N:1".equals(n.getDedupeKey())),
                "1건 경고가 있어야 합니다");
        assertTrue(notifications.stream().anyMatch(n -> "cso-violation:PRJ-N:2".equals(n.getDedupeKey())),
                "2건 경고가 별도로 있어야 합니다 (합치면 개선/악화를 알 수 없음)");
    }

    // ------------------------------------------------------------------
    //  헬퍼
    // ------------------------------------------------------------------

    private Project saveProject(String key,
                                List<ProjectDto.SubnetPayload> subnets,
                                List<ProjectDto.RulePayload> rules) {
        final Project created = service.create(
                new ProjectDto.CreateRequest(key, "테스트 " + key, "Defense", null, "DRAFT"));
        return service.update(key, new ProjectDto.UpdateRequest(
                created.getName(), created.getCategory(), created.getDescription(),
                created.getStatus(), subnets, rules));
    }

    private static ProjectDto.SubnetPayload subnet(String id, String cidr, String zone) {
        return new ProjectDto.SubnetPayload(id, cidr, zone, id, null, false);
    }

    private static ProjectDto.RulePayload rule(String id, String src, String dst) {
        return new ProjectDto.RulePayload(id, src, dst, null, "any", "MANUAL", true, null);
    }

    private static ProjectDto.RulePayload ruleWithPort(String id, String src, String dst, int port) {
        return new ProjectDto.RulePayload(id, src, dst, port, "tcp", "MANUAL", true, null);
    }

    private static long criticalCount(SegmentationBddEngine.Report report) {
        return report.getViolations().stream()
                .filter(v -> "CRITICAL".equals(v.severity().name()))
                .count();
    }

    private static Notification findCso(List<Notification> all) {
        return all.stream()
                .filter(n -> n.getDedupeKey() != null && n.getDedupeKey().startsWith("cso-violation:"))
                .findFirst()
                .orElse(null);
    }

    private static List<String> titles(List<Notification> all) {
        final List<String> out = new ArrayList<>();
        for (final Notification n : all) {
            out.add(n.getCategory() + "/" + n.getSeverity() + " " + n.getTitle());
        }
        return out;
    }
}