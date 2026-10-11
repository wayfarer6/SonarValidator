package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonar.sonarvalidator_backend.support.StubRepository.of;

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
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.sonar.sonarvalidator_backend.Service.PolicyRegistryService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 차단 ACL({@code acl_apply})이 <b>정책 최상위</b>에 실려 나가는지 검증합니다.
 *
 * <h2>⚠️ 이 테스트가 겨냥하는 실패 모드</h2>
 * <p>전략 단위 테스트({@link BatchAclPolicyTest})는 노드의 <b>내용</b>만
 * 봅니다. 그런데 실제로 차단이 걸리려면 그 노드가
 * <ul>
 *   <li>정책 최상위 키 {@code acl_apply} 로 나가야 하고,</li>
 *   <li>Prober 가 읽는 {@code policies[]} 와 <b>같은 객체</b>에 있어야 합니다.</li>
 * </ul>
 * 둘 중 하나가 어긋나면 장치는 항상 빈 차단 목록을 받습니다 — 규칙은 만들어지고
 * 로그도 정상으로 보이는데 <b>차단만 안 되는</b> 상태입니다.
 *
 * <p>그래서 여기서는 {@code forAgent} 진입점부터 확인합니다. Prober 가 실제로
 * 보는 것과 같은 경로입니다.
 */
class BatchAclWiringTest {

    /**
     * 프로젝트 하나를 메모리 저장소로 감쌉니다.
     *
     * @param project 프로젝트
     * @return 저장소 스텁
     */
    private static ProjectRepository repositoryOf(Project project) {
        final List<Project> rows = new ArrayList<>(List.of(project));
        return of(ProjectRepository.class, (method, args) -> switch (method) {
            case "findAllByOrderByCreatedAtDesc" -> rows;
            case "findByProjectKey" -> Optional.of(project);
            default -> org.sonar.sonarvalidator_backend.support.StubRepository.UNHANDLED;
        });
    }

    /**
     * 서브넷을 만듭니다.
     *
     * @param id     서브넷 식별자
     * @param cidr   대역
     * @param zone   등급
     * @param agent  배정된 Agent 식별자
     * @param parent 소속 프로젝트
     * @return 서브넷
     */
    private static ProjectSubnet subnet(String id,
                                        String cidr,
                                        ZoneClass zone,
                                        String agent,
                                        Project parent) {
        final ProjectSubnet value = new ProjectSubnet();
        value.setSubnetId(id);
        value.setCidr(cidr);
        value.setZoneClass(zone);
        value.setAgentId(agent);
        value.setProject(parent);
        return value;
    }

    /**
     * 기밀망 → 개방망 <b>금지</b> 규칙을 담은 프로젝트를 만듭니다.
     *
     * <p>{@code Confidential(3) → Open(1)} 은 Sensitive(2) 를 건너뛰므로
     * 금지 연결입니다. 즉 두 장치 모두 {@code acl_apply} 를 받아야 합니다.
     *
     * @return 프로젝트
     */
    private static Project forbiddenProject() {
        final Project project = new Project();
        project.setProjectKey("acl-wiring");
        project.setName("ACL 배선 검증");
        project.setCreatedAt(new Date());

        project.getSubnets().add(subnet("s-conf", "10.0.8.0/24", ZoneClass.CONFIDENTIAL,
                "cisco-router", project));
        project.getSubnets().add(subnet("s-open", "10.0.9.0/24", ZoneClass.OPEN,
                "ovs-switch", project));

        final ProjectRule rule = new ProjectRule();
        rule.setRuleId("rule-conf-to-open");
        rule.setSource("s-conf");
        rule.setDestination("s-open");
        rule.setProtocol("tcp");
        rule.setEnabled(true);
        rule.setProject(project);
        project.getRules().add(rule);
        return project;
    }

    /** 배열 첫 값을 꺼냅니다. (정책 스키마가 스칼라를 배열로 감쌉니다) */
    private static String first(JsonNode node, String key) {
        final JsonNode array = node.path(key);
        assertTrue(array.isArray() && !array.isEmpty(), key + " 가 비어 있으면 안 됩니다");
        return array.get(0).asText();
    }

    @Test
    @DisplayName("Cisco 라우터 정책은 최상위 acl_apply 에 차단 ACL 을 싣는다")
    void ciscoRouterPolicyCarriesAclApply() {
        final PolicyRegistryService service =
                new PolicyRegistryService(repositoryOf(forbiddenProject()));

        final ObjectNode policy = service.forAgent("cisco-router", DeviceType.ROUTER,
                "cisco-router", "Cisco", "IOS XE");

        // ⚠️ Prober 는 policies[] 와 acl_apply 를 <b>같은 객체</b>에서 찾습니다.
        //    acl_apply 를 policies[] 안이나 summary 안에 넣으면 아무도 읽지 않습니다.
        assertTrue(policy.has("policies"), "규칙 목록이 있어야 합니다");
        assertNotNull(policy.get("acl_apply"), "ACL 은 최상위에 있어야 합니다");

        final JsonNode acl = policy.get("acl_apply");
        assertEquals("apply", first(acl, "command"), "선언적 적용 명령");
        assertEquals("SONAR-CSO", first(acl.path("rule_target"), "acl_name"), "ACL 이름");

        final JsonNode criteria = acl.path("acl_rules").get(0).path("match_criteria");
        assertEquals("10.0.8.0", first(criteria, "ip_saddr"), "출발 네트워크 주소");
        assertEquals("0.0.0.255", first(criteria, "ip_saddr_wildcard"), "반전 마스크");
        assertEquals("10.0.9.0", first(criteria, "ip_daddr"), "도착 네트워크 주소");
        assertEquals("deny", first(acl.path("acl_rules").get(0), "action"), "차단");
    }

    @Test
    @DisplayName("OVS 스위치 정책은 최상위 acl_apply 에 브리지 대상 플로우를 싣는다")
    void ovsSwitchPolicyCarriesAclApply() {
        final PolicyRegistryService service =
                new PolicyRegistryService(repositoryOf(forbiddenProject()));

        final ObjectNode policy = service.forAgent("ovs-switch", DeviceType.SWITCH,
                "ovs-switch", "Linux", "OpenVSwitch");

        assertNotNull(policy.get("acl_apply"), "OVS 도 일괄 노드를 받아야 합니다");

        final JsonNode acl = policy.get("acl_apply");
        assertEquals("apply", first(acl, "command"), "선언적 적용 명령");
        // ⚠️ ovs-ofctl 의 대상은 브리지입니다. 업링크 포트를 넣으면
        //    "no bridge named eth0" 로 실패합니다.
        assertEquals("br0", first(acl.path("rule_target"), "bridge_name"), "브리지 대상");

        final JsonNode criteria = acl.path("acl_rules").get(0).path("match_criteria");
        // OpenFlow 는 CIDR 을 그대로 받습니다 (IOS 와 반대).
        assertEquals("10.0.8.0/24", first(criteria, "ip_saddr"), "CIDR 그대로");
        assertNull(criteria.get("ip_saddr_wildcard"), "OVS 에는 반전 마스크를 넣지 않습니다");
    }

    @Test
    @DisplayName("Arista 스위치는 acl_apply 를 받지 않는다")
    void aristaSwitchHasNoAclApply() {
        final PolicyRegistryService service =
                new PolicyRegistryService(repositoryOf(forbiddenProject()));

        final ObjectNode policy = service.forAgent("ovs-switch", DeviceType.SWITCH,
                "ovs-switch", "Arista", "Arista vEOS");

        // ⚠️ Arista FastCli 경로에는 ACL 투입이 구현되어 있지 않습니다.
        //    OVS 문법(ovs-ofctl)을 보내면 장치가 조용히 실패합니다.
        assertNull(policy.get("acl_apply"), "Arista 에는 OVS 일괄 노드를 보내지 않습니다");
    }

    @Test
    @DisplayName("금지 연결이 없으면 빈 차단 목록을 보낸다")
    void noForbiddenStillSendsEmptyAcl() {
        final Project project = new Project();
        project.setProjectKey("acl-empty");
        project.setName("차단 없음");
        project.setCreatedAt(new Date());
        project.getSubnets().add(subnet("s-open", "10.0.9.0/24", ZoneClass.OPEN,
                "cisco-router", project));
        project.getSubnets().add(subnet("s-open2", "10.0.10.0/24", ZoneClass.OPEN,
                "ovs-switch", project));

        final ProjectRule rule = new ProjectRule();
        rule.setRuleId("rule-open");
        rule.setSource("s-open");
        rule.setDestination("s-open2");
        rule.setEnabled(true);
        rule.setProject(project);
        project.getRules().add(rule);

        final PolicyRegistryService service =
                new PolicyRegistryService(repositoryOf(project));
        final ObjectNode policy = service.forAgent("cisco-router", DeviceType.ROUTER,
                "cisco-router", "Cisco", "IOS XE");

        // ⚠️ 여기서 노드를 빼면 "규칙 없음" 과 "이번엔 안 보냄" 을 장치가
        //    구분하지 못합니다. 운영자가 마지막 금지 연결을 지운 순간이
        //    장치에 반영되지 않습니다 — 차단이 영원히 안 풀립니다.
        assertNotNull(policy.get("acl_apply"), "빈 목록도 보내야 합니다");
        assertEquals(0, policy.get("acl_apply").path("acl_rules").size(),
                "차단 목록은 비어 있어야 합니다");
    }
}
