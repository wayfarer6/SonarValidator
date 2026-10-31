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

class BatchAclWiringTest {

    private static ProjectRepository repositoryOf(Project project) {
        final List<Project> rows = new ArrayList<>(List.of(project));
        return of(ProjectRepository.class, (method, args) -> switch (method) {
            case "findAllByOrderByCreatedAtDesc" -> rows;
            case "findByProjectKey" -> Optional.of(project);
            default -> org.sonar.sonarvalidator_backend.support.StubRepository.UNHANDLED;
        });
    }

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




        assertNotNull(policy.get("acl_apply"), "빈 목록도 보내야 합니다");
        assertEquals(0, policy.get("acl_apply").path("acl_rules").size(),
                "차단 목록은 비어 있어야 합니다");
    }
}
