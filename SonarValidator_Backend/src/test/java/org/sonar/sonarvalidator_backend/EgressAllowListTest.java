package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectRule;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicyRule;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicyViolation;
import org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.sonar.sonarvalidator_backend.Service.PolicyRegistryService;
import org.sonar.sonarvalidator_backend.support.StubRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 서브넷의 <b>연결 허용 목록</b>({@code allowed_peers}) 을 검증합니다.
 *
 * <h2>⚠️ 이 기능이 겨냥하는 요구</h2>
 * <p>"기밀망은 인터넷으로 나가면 안 된다" 를 운영자가 화면에서 표현할 수 있어야
 * 하고, 배포할 때 <b>정해 둔 VLAN/대역만</b> 연결되도록 장치에 반영되어야 합니다.
 *
 * <h2>⚠️ 목록은 조이기만 해야 한다 (가장 중요한 불변식)</h2>
 * <p>허용 목록에 상대를 넣는 것으로 등급 규칙을 <b>완화할 수 있으면</b>
 * 운영자의 실수 한 번이 망분리를 없앱니다. 그래서 기밀망의 목록에 공개망을
 * 넣어도 위반은 그대로 남아야 합니다. 아래 테스트가 이 경계를 고정합니다.
 */
class EgressAllowListTest {

    /** 배열 첫 값을 꺼냅니다. (정책 스키마가 스칼라를 배열로 감쌉니다) */
    private static String first(JsonNode node, String key) {
        final JsonNode array = node.path(key);
        assertTrue(array.isArray() && !array.isEmpty(), key + " 가 비어 있으면 안 됩니다");
        return array.get(0).asText();
    }

    /**
     * 검증 엔진용 서브넷을 만듭니다.
     *
     * @param id      식별자
     * @param cidr    대역
     * @param zone    등급
     * @param allowed 허용 상대
     * @return 서브넷
     */
    private static PolicySubnet subnet(String id, String cidr, ZoneClass zone, String... allowed) {
        final PolicySubnet value = PolicySubnet.of(id, cidr, zone);
        value.setAllowedPeers(List.of(allowed));
        return value;
    }

    /**
     * 검증 엔진용 규칙을 만듭니다.
     *
     * @param id  식별자
     * @param src 출발
     * @param dst 도착
     * @return 규칙
     */
    private static PolicyRule rule(String id, String src, String dst) {
        final PolicyRule value = new PolicyRule();
        value.setId(id);
        value.setSource(src);
        value.setDestination(dst);
        value.setProtocol("tcp");
        // ⚠️ 포트를 반드시 지정합니다. 비워 두면 "허용 포트가 없는 규칙" 경고가
        //    같은 규칙 식별자로 함께 붙어, 아래 위반 조회가 엉뚱한 항목을
        //    집어 옵니다. (실제로 심각도가 MAJOR 로 보이는 오진을 만들었습니다)
        value.setPort(443);
        value.setEnabled(true);
        return value;
    }

    // ------------------------------------------------------------------
    //  도메인 판정
    // ------------------------------------------------------------------

    @Test
    @DisplayName("허용 목록이 비면 제한이 없다")
    void emptyListMeansUnrestricted() {
        final PolicySubnet open = PolicySubnet.of("s1", "10.0.8.0/24", ZoneClass.OPEN);
        assertFalse(open.isRestricted(), "빈 목록은 제한 아님");
        assertTrue(open.allowsPeer("10.0.9.0/24"), "제한이 없으면 모두 허용");
        assertTrue(open.allowsInternet(), "제한이 없으면 인터넷도 허용");
    }

    @Test
    @DisplayName("허용 목록은 식별자와 CIDR 을 같은 서브넷으로 본다")
    void identifiersAndCidrAreEquivalent() {
        // ⚠️ 화면은 서브넷 식별자를, 수집 초안은 CIDR 을 씁니다. 목록에 어느 쪽을
        //    적어도 같은 서브넷으로 인정해야 "허용했는데 막히는" 일이 없습니다.
        final PolicySubnet byCidr = subnet("s1", "10.0.8.0/24", ZoneClass.SENSITIVE,
                "10.0.9.0/24");
        assertTrue(byCidr.allowsPeer("10.0.9.0/24"), "CIDR 그대로");
        assertTrue(byCidr.allowsPeer("10.0.9.7/24"), "같은 대역의 다른 표기");
        assertFalse(byCidr.allowsPeer("10.0.99.0/24"), "목록 밖은 거부");
    }

    @Test
    @DisplayName("인터넷은 목록에 넣어야 허용된다")
    void internetMustBeListed() {
        final PolicySubnet withoutInternet = subnet("s1", "10.0.8.0/24",
                ZoneClass.CONFIDENTIAL, "10.0.9.0/24");
        assertTrue(withoutInternet.isRestricted(), "제한 중");
        assertFalse(withoutInternet.allowsInternet(), "인터넷을 안 넣었으므로 차단 대상");

        final PolicySubnet withInternet = subnet("s1", "10.0.8.0/24",
                ZoneClass.CONFIDENTIAL, "10.0.9.0/24", "internet");
        assertTrue(withInternet.allowsInternet(), "인터넷을 넣었으므로 허용 목록이 막지 않음");
    }

    // ------------------------------------------------------------------
    //  검증 엔진
    // ------------------------------------------------------------------

    @Test
    @DisplayName("허용 목록 밖으로 나가는 규칙은 위반으로 보고된다")
    void egressOutsideAllowListIsReported() {
        final var engine = new SegmentationBddEngine();
        final var report = engine.validate(
                List.of(
                        subnet("C", "10.0.10.0/24", ZoneClass.CONFIDENTIAL, "10.0.11.0/24"),
                        PolicySubnet.of("Allowed", "10.0.11.0/24", ZoneClass.SENSITIVE),
                        PolicySubnet.of("Outside", "10.0.12.0/24", ZoneClass.SENSITIVE)),
                List.of(rule("Rule-1", "C", "Outside")));

        assertFalse(report.isCompliant(), "목록 밖 연결이 있으면 준수 아님");
        final var violation = report.getViolations().stream()
                .filter(v -> "Rule-1".equals(v.ruleId()))
                .findFirst().orElse(null);
        assertNotNull(violation, "규칙 위반이 보고되어야 합니다");
        // ⚠️ 기밀망이 관련된 제한 위반은 CRITICAL 입니다.
        //    MAJOR 로 낮추면 운영자가 "나중에 고쳐도 되는 것" 으로 오해합니다.
        assertEquals(PolicyViolation.Severity.CRITICAL, violation.severity(),
                "기밀망의 목록 밖 연결은 CRITICAL");
        assertTrue(violation.reason().contains("허용 목록"), "사유: " + violation.reason());
    }

    @Test
    @DisplayName("허용 목록 안쪽 연결은 위반이 아니다")
    void egressInsideAllowListIsClean() {
        final var engine = new SegmentationBddEngine();
        final var report = engine.validate(
                List.of(
                        subnet("C", "10.0.10.0/24", ZoneClass.CONFIDENTIAL, "10.0.11.0/24"),
                        PolicySubnet.of("Allowed", "10.0.11.0/24", ZoneClass.SENSITIVE)),
                List.of(rule("Rule-1", "C", "Allowed")));

        assertTrue(report.isCompliant(), "허용한 상대만 있으면 준수: " + report.getMessages());
    }

    @Test
    @DisplayName("허용 목록은 등급 규칙을 완화하지 못한다")
    void allowListCannotLoosenZoneRule() {
        final var engine = new SegmentationBddEngine();
        // ⚠️ 기밀망의 허용 목록에 공개망을 <b>넣었습니다.</b>
        //    이것은 "연결해도 좋다" 는 승인이 아닙니다 — 등급 건너뛰기는
        //    그대로 위반이어야 합니다. 여기서 준수가 나오면 운영자가
        //    목록 한 줄로 망분리를 끌 수 있다는 뜻입니다.
        final var report = engine.validate(
                List.of(
                        subnet("C", "10.0.10.0/24", ZoneClass.CONFIDENTIAL, "O"),
                        PolicySubnet.of("O", "10.0.30.0/24", ZoneClass.OPEN)),
                List.of(rule("Rule-1", "C", "O")));

        assertFalse(report.isCompliant(), "허용 목록에 넣어도 등급 위반은 남아야 합니다");
        // 등급 위반은 BDD 판정으로 나오므로 사유 문구 대신 <b>양 끝점</b>으로
        // 확인합니다. 문구가 바뀌어도 계약이 흔들리지 않습니다.
        assertTrue(report.getViolations().stream()
                        .anyMatch(v -> "C".equals(v.sourceSubnetId())
                                && "O".equals(v.targetSubnetId())),
                "기밀망 → 공개망 위반이 보고되어야 합니다: " + report.getMessages());
    }

    @Test
    @DisplayName("허용 목록이 없는 서브넷은 검사 대상이 아니다")
    void unrestrictedSubnetIsNotChecked() {
        final var engine = new SegmentationBddEngine();
        final var report = engine.validate(
                List.of(
                        PolicySubnet.of("A", "10.0.10.0/24", ZoneClass.SENSITIVE),
                        PolicySubnet.of("B", "10.0.11.0/24", ZoneClass.SENSITIVE)),
                List.of(rule("Rule-1", "A", "B")));

        assertTrue(report.isCompliant(), "제한이 없으면 기존 동작 그대로: " + report.getMessages());
    }

    // ------------------------------------------------------------------
    //  정책 생성 (장치로 나가는 차단)
    // ------------------------------------------------------------------

    /**
     * 프로젝트를 메모리 저장소로 감쌉니다.
     *
     * @param project 프로젝트
     * @return 저장소 스텁
     */
    private static ProjectRepository repositoryOf(Project project) {
        return StubRepository.of(ProjectRepository.class, (method, args) -> switch (method) {
            case "findAllByOrderByCreatedAtDesc" -> new ArrayList<>(List.of(project));
            case "findByProjectKey" -> Optional.of(project);
            default -> StubRepository.UNHANDLED;
        });
    }

    /**
     * 서브넷 엔티티를 만듭니다.
     *
     * @param id      식별자
     * @param cidr    대역
     * @param zone    등급
     * @param agent   담당 Agent
     * @param parent  소속 프로젝트
     * @param allowed 허용 상대
     * @return 서브넷
     */
    private static ProjectSubnet entity(String id, String cidr, ZoneClass zone, String agent,
                                        Project parent, String... allowed) {
        final ProjectSubnet value = new ProjectSubnet();
        value.setSubnetId(id);
        value.setCidr(cidr);
        value.setZoneClass(zone);
        value.setAgentId(agent);
        value.setProject(parent);
        value.setAllowedPeerList(List.of(allowed));
        return value;
    }

    /**
     * 기밀망에 허용 목록을 적은 프로젝트를 만듭니다.
     *
     * @param allowed 대상 허용 목록
     * @return 프로젝트
     */
    private static Project restrictedProject(String... allowed) {
        final Project project = new Project();
        project.setProjectKey("egress");
        project.setName("허용 목록 검증");
        project.setCreatedAt(new Date());
        project.getSubnets().add(entity("C", "192.168.122.254/32", ZoneClass.CONFIDENTIAL,
                "cisco-router", project, allowed));
        project.getSubnets().add(entity("VLAN8", "10.0.8.0/24", ZoneClass.SENSITIVE,
                "ovs", project));
        return project;
    }

    private static ProjectRule routerRule(Project project, String src, String dst) {
        final ProjectRule value = new ProjectRule();
        value.setRuleId("Rule-1");
        value.setSource(src);
        value.setDestination(dst);
        value.setProtocol("tcp");
        value.setEnabled(true);
        value.setProject(project);
        return value;
    }

    @Test
    @DisplayName("허용 목록에 인터넷이 없으면 기본 경로를 차단한다")
    void missingInternetProducesDefaultRouteDeny() {
        final Project project = restrictedProject("VLAN8");
        project.getRules().add(routerRule(project, "C", "VLAN8"));

        final PolicyRegistryService service =
                new PolicyRegistryService(repositoryOf(project));
        final ObjectNode policy = service.forAgent("cisco-router", DeviceType.ROUTER,
                "cisco-router", "Cisco", "IOS XE");

        // ⚠️ 규칙에 인터넷이 적혀 있지 않아도 막아야 합니다. 운영자의 요구가
        //    "기밀망은 인터넷 차단" 인데 ACL 에 기본 경로 거부가 없으면
        //    요구사항이 장치에 반영되지 않습니다.
        final JsonNode rules = policy.path("acl_apply").path("acl_rules");
        assertTrue(rules.isArray() && !rules.isEmpty(), "차단 목록이 있어야 합니다");

        final JsonNode defaultDeny = rules.valueStream()
                .filter(node -> "0.0.0.0".equals(
                        first(node.path("match_criteria"), "ip_daddr")))
                .findFirst().orElse(null);
        assertNotNull(defaultDeny, "기본 경로(0.0.0.0/0) 거부가 포함되어야 합니다: " + rules);

        final JsonNode criteria = defaultDeny.path("match_criteria");
        // ⚠️ /0 의 와일드카드는 255.255.255.255 입니다. 넷마스크(0.0.0.0)를
        //    넣으면 매칭되는 주소가 사실상 없어져 차단이 조용히 사라집니다.
        assertEquals("255.255.255.255", first(criteria, "ip_daddr_wildcard"),
                "기본 경로는 모든 주소와 매칭되어야 합니다");
        assertEquals("deny", first(defaultDeny, "action"), "차단");
    }

    @Test
    @DisplayName("허용 목록에 인터넷이 있으면 기본 경로를 차단하지 않는다")
    void listedInternetSkipsDefaultRouteDeny() {
        final Project project = restrictedProject("VLAN8", "internet");
        project.getRules().add(routerRule(project, "C", "VLAN8"));

        final PolicyRegistryService service =
                new PolicyRegistryService(repositoryOf(project));
        final ObjectNode policy = service.forAgent("cisco-router", DeviceType.ROUTER,
                "cisco-router", "Cisco", "IOS XE");

        final JsonNode rules = policy.path("acl_apply").path("acl_rules");
        final boolean hasDefaultDeny = rules.valueStream().anyMatch(node ->
                "0.0.0.0".equals(first(node.path("match_criteria"), "ip_daddr")));
        assertFalse(hasDefaultDeny, "인터넷을 허용했으므로 기본 경로 거부가 없어야 합니다: " + rules);
    }

    @Test
    @DisplayName("허용 목록 밖 규칙은 장치 차단으로 내려간다")
    void ruleOutsideAllowListBecomesDeny() {
        final Project project = restrictedProject("VLAN8");
        // VLAN9 는 목록에 없습니다.
        project.getSubnets().add(entity("VLAN9", "10.0.9.0/24", ZoneClass.SENSITIVE,
                "ovs", project));
        project.getRules().add(routerRule(project, "C", "VLAN9"));

        final PolicyRegistryService service =
                new PolicyRegistryService(repositoryOf(project));
        final ObjectNode policy = service.forAgent("cisco-router", DeviceType.ROUTER,
                "cisco-router", "Cisco", "IOS XE");

        final JsonNode rules = policy.path("acl_apply").path("acl_rules");
        final JsonNode deny = rules.valueStream()
                .filter(node -> "10.0.9.0".equals(
                        first(node.path("match_criteria"), "ip_daddr")))
                .findFirst().orElse(null);
        assertNotNull(deny, "목록 밖 연결이 차단되어야 합니다: " + rules);
        assertEquals("deny", first(deny, "action"), "차단");
        // 사유는 항목 최상위에 붙습니다. (match_criteria 안이 아님)
        assertTrue(deny.path("reason").get(0).asText().contains("허용 목록"),
                "사유가 허용 목록 때문임을 알려야 합니다: " + deny);
    }

    @Test
    @DisplayName("공개망에는 인터넷 차단을 만들지 않는다")
    void openZoneKeepsInternet() {
        final Project project = new Project();
        project.setProjectKey("open-egress");
        project.setName("공개망");
        project.setCreatedAt(new Date());
        // ⚠️ 공개망은 인터넷과 붙는 것이 정상입니다. 목록에 인터넷을 안 적었다고
        //    기본 경로를 막으면 서비스가 통째로 멈춥니다.
        project.getSubnets().add(entity("O", "10.0.30.0/24", ZoneClass.OPEN,
                "ovs", project, "10.0.8.0/24"));

        final PolicyRegistryService service =
                new PolicyRegistryService(repositoryOf(project));
        final ObjectNode policy = service.forAgent("ovs", DeviceType.SWITCH,
                "ovs", "Linux", "OpenVSwitch");

        final JsonNode acl = policy.get("acl_apply");
        assertNotNull(acl, "일괄 노드가 있어야 합니다");
        final boolean hasDefaultDeny = acl.path("acl_rules").valueStream().anyMatch(node ->
                "0.0.0.0".equals(first(node.path("match_criteria"), "ip_daddr")));
        assertFalse(hasDefaultDeny, "공개망의 인터넷은 막지 않습니다: " + acl.path("acl_rules"));
    }

    @Test
    @DisplayName("허용 목록의 식별자는 CIDR 참조와도 매칭된다")
    void allowListMatchesIdentifierAgainstCidr() {
        // ⚠️ 화면은 허용 항목을 <b>서브넷 식별자</b>로 보내는데, 규칙은 CIDR 로
        //    대상을 적을 수 있습니다. 한쪽만 비교하면 운영자가 체크한 상대가
        //    매칭되지 않아 <b>허용했는데 차단</b>됩니다.
        final Project project = restrictedProject("VLAN8");
        // 규칙은 같은 서브넷을 CIDR 로 가리킵니다.
        project.getRules().add(routerRule(project, "C", "10.0.8.0/24"));

        final PolicyRegistryService service =
                new PolicyRegistryService(repositoryOf(project));
        final ObjectNode policy = service.forAgent("cisco-router", DeviceType.ROUTER,
                "cisco-router", "Cisco", "IOS XE");

        final JsonNode rules = policy.path("acl_apply").path("acl_rules");
        final boolean denyToVlan8 = rules.valueStream().anyMatch(node ->
                "10.0.8.0".equals(first(node.path("match_criteria"), "ip_daddr")));
        assertFalse(denyToVlan8,
                "허용한 상대는 CIDR 표기로 참조되어도 차단되면 안 됩니다: " + rules);
    }

    @Test
    @DisplayName("허용 목록은 저장되고 다시 읽힌다")
    void allowListRoundTrips() {
        final ProjectSubnet subnet = new ProjectSubnet();
        assertNull(subnet.getAllowedPeers(), "초기값은 null(제한 없음)");
        assertTrue(subnet.allowedPeerList().isEmpty(), "빈 목록");
        assertFalse(subnet.isRestricted(), "제한 아님");

        subnet.setAllowedPeerList(List.of("VLAN9", "internet", "VLAN9", " "));
        assertEquals(List.of("VLAN9", "internet"), subnet.allowedPeerList(),
                "중복·공백 제거 후 순서 유지");
        assertTrue(subnet.isRestricted(), "제한 중");
        assertTrue(subnet.allowsPeer("VLAN9"), "식별자 허용");
        assertTrue(subnet.allowsInternet(), "인터넷 허용");
        assertFalse(subnet.allowsPeer("VLAN10"), "목록 밖 거부");

        // 도메인 객체 왕복에서도 값이 유지되어야 합니다.
        assertEquals(List.of("VLAN9", "internet"),
                subnet.toPolicySubnet().getAllowedPeers(), "도메인 변환 유지");

        subnet.setAllowedPeerList(List.of());
        assertNull(subnet.getAllowedPeers(), "빈 목록은 제한 해제");
    }
}
