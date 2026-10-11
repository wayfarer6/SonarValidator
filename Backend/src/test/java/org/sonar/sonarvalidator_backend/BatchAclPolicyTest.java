package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Config.SiteProperties;
import org.sonar.sonarvalidator_backend.Policy.strategy.BatchPolicyContext;
import org.sonar.sonarvalidator_backend.Policy.strategy.PolicyBuildContext;
import org.sonar.sonarvalidator_backend.Policy.strategy.PolicyJson;
import org.sonar.sonarvalidator_backend.Policy.strategy.vendor.arista.veos.SwitchPolicy;
import org.sonar.sonarvalidator_backend.Policy.strategy.vendor.cisco.iosxe.RouterPolicy;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 차단 ACL 을 <b>선언적으로</b> 내려보내는 경로를 검증합니다.
 *
 * <h2>⚠️ 왜 연결 하나짜리 규칙으로는 부족한가</h2>
 * <p>IOS 확장 ACL 은 규칙 하나만 지우는 문법이 없어 이름으로 통째로 다시 써야
 * 합니다. 그래서 서버는 {@code acl_apply} 노드에 "지금 남아 있어야 하는 규칙
 * 전체" 를 싣습니다. 이 테스트는 그 노드가:
 * <ul>
 *   <li>금지 연결만 담는지 (허용을 넣으면 ACL 이 무의미하게 커짐)</li>
 *   <li>마스크를 <b>와일드카드</b>로 바꾸는지 (넷마스크를 넣으면 차단이 사라짐)</li>
 *   <li>차단이 0건일 때도 노드를 보내는지 (안 보내면 마지막 해제가 반영 안 됨)</li>
 * </ul>
 * 를 확인합니다.
 */
class BatchAclPolicyTest {

    /** 배열 첫 값을 꺼냅니다. (문서 스키마가 스칼라를 배열로 감쌉니다) */
    private static String first(JsonNode node, String key) {
        final JsonNode array = node.get(key);
        assertNotNull(array, key + " 키가 있어야 합니다");
        assertTrue(array.isArray() && array.size() > 0, key + " 배열이 비어 있으면 안 됩니다");
        return array.get(0).asText();
    }

    /**
     * 테스트용 최소 서브넷을 만듭니다.
     *
     * @param id   서브넷 식별자
     * @param cidr CIDR 대역
     * @return 채워진 서브넷
     */
    private static org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet subnet(String id,
                                                                                     String cidr) {
        final var value = new org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet();
        value.setSubnetId(id);
        value.setCidr(cidr);
        return value;
    }

    /**
     * 연결 뷰 하나를 만듭니다.
     *
     * @param source     출발 대역
     * @param destination 도착 대역
     * @param forbidden  금지 연결인지
     * @return 연결 뷰
     */
    private static PolicyBuildContext.ConnectionView connection(String source,
                                                                String destination,
                                                                boolean forbidden) {
        return new PolicyBuildContext.ConnectionView("rule-1", true, "peer", destination, null,
                source, destination, "tcp", null, forbidden, "등급 건너뛰기");
    }

    @Test
    @DisplayName("라우터 일괄 ACL 은 와일드카드 마스크와 apply 명령을 쓴다")
    void routerBatchUsesWildcardMask() {
        final SiteProperties site = new SiteProperties();
        site.getRouterDefaults().setDefaultInterface("GigabitEthernet0/0/1");
        final RouterPolicy policy = new RouterPolicy(site);

        final ObjectNode node = policy.batchEnforcementRule(new BatchPolicyContext(
                subnet("s1", "10.0.8.0/24"), "Cisco", "IOS XE",
                List.of(connection("10.0.8.0/24", "10.0.9.0/24", true))));

        assertNotNull(node, "일괄 노드 생성");
        // ⚠️ "create" 가 아니라 "apply" 입니다. "create" 면 Prober 의 적용기가
        //    ospf/logging 분기로 들어가 아무 일도 하지 않습니다.
        assertEquals("apply", first(node, "command"), "선언적 적용 명령");
        assertEquals("SONAR-CSO", first(node.path("rule_target"), "acl_name"), "ACL 이름");
        assertEquals("GigabitEthernet0/0/1",
                first(node.path("rule_target"), "applied_interface"), "설정된 인터페이스");

        final JsonNode entry = node.path("acl_rules").get(0);
        final JsonNode criteria = entry.path("match_criteria");
        assertEquals("10.0.8.0", first(criteria, "ip_saddr"), "네트워크 주소");
        // ⚠️ 넷마스크(255.255.255.0)를 넣으면 ACL 이 엉뚱한 대역을 막습니다.
        assertEquals("0.0.0.255", first(criteria, "ip_saddr_wildcard"), "반전 마스크");
        assertEquals("0.0.0.255", first(criteria, "ip_daddr_wildcard"), "반전 마스크");
        assertEquals("ip", first(criteria, "protocol"), "포트 구분 없는 IP 차단");
        assertEquals("deny", first(entry, "action"), "차단");
    }

    @Test
    @DisplayName("라우터 일괄 ACL 은 허용 연결을 담지 않는다")
    void routerBatchSkipsAllowedConnections() {
        final RouterPolicy policy = new RouterPolicy(new SiteProperties());

        final ObjectNode node = policy.batchEnforcementRule(new BatchPolicyContext(
                subnet("s1", "10.0.8.0/24"), "Cisco", "IOS XE",
                List.of(connection("10.0.8.0/24", "10.0.9.0/24", false),
                        connection("10.0.8.0/24", "1.1.1.1/32", true))));

        assertEquals(1, node.path("acl_rules").size(),
                "금지 연결만 담아야 합니다 (허용을 넣으면 ACL 이 커지기만 합니다)");
        assertEquals("1.1.1.1", first(node.path("acl_rules").get(0).path("match_criteria"), "ip_daddr"),
                "남은 항목은 금지 연결");
    }

    @Test
    @DisplayName("차단이 0건이어도 일괄 노드를 보낸다")
    void routerBatchStillSentWhenNothingIsForbidden() {
        final RouterPolicy policy = new RouterPolicy(new SiteProperties());

        final ObjectNode node = policy.batchEnforcementRule(new BatchPolicyContext(
                subnet("s1", "10.0.8.0/24"), "Cisco", "IOS XE",
                List.of(connection("10.0.8.0/24", "10.0.9.0/24", false))));

        // ⚠️ 여기서 null 을 돌려주면 "규칙 없음" 과 "이번엔 안 보냄" 을 장치가
        //    구분하지 못합니다. 운영자가 마지막 금지 연결을 지운 순간이
        //    장치에 반영되지 않습니다.
        assertNotNull(node, "빈 목록도 보내야 합니다");
        assertEquals(0, node.path("acl_rules").size(), "차단 목록은 비어 있어야 합니다");
    }

    @Test
    @DisplayName("주소가 깨진 연결은 조용히 빼고 나머지를 적용한다")
    void routerBatchSkipsUnparseableCidr() {
        final RouterPolicy policy = new RouterPolicy(new SiteProperties());

        final ObjectNode node = policy.batchEnforcementRule(new BatchPolicyContext(
                subnet("s1", "10.0.8.0/24"), "Cisco", "IOS XE",
                List.of(connection("10.0.8.0/24", "대역아님", true),
                        connection("10.0.8.0/24", "10.0.9.0/24", true))));

        assertEquals(1, node.path("acl_rules").size(), "만들 수 있는 줄만 남아야 합니다");
        // 주소가 깨진 줄을 그대로 넣으면 IOS 가 ACL 전체를 거부할 수 있습니다.
        assertEquals("10.0.9.0", first(node.path("acl_rules").get(0).path("match_criteria"), "ip_daddr"),
                "정상 연결만 남는다");
    }

    @Test
    @DisplayName("단건 집행 규칙은 허용 연결에 대해 null 이다")
    void routerEnforcementIsNullForAllowed() {
        final RouterPolicy policy = new RouterPolicy(new SiteProperties());

        assertNull(policy.enforcementRule(PolicyBuildContext.forConnection(
                        subnet("s1", "10.0.8.0/24"), "Cisco", "IOS XE",
                        connection("10.0.8.0/24", "10.0.9.0/24", false))),
                "허용 연결은 규칙이 아닙니다 (장치 기본이 permit)");

        assertNotNull(policy.enforcementRule(PolicyBuildContext.forConnection(
                        subnet("s1", "10.0.8.0/24"), "Cisco", "IOS XE",
                        connection("10.0.8.0/24", "10.0.9.0/24", true))),
                "금지 연결은 규칙이 있어야 합니다");
    }

    @Test
    @DisplayName("OVS 일괄 ACL 은 브리지를 대상으로 CIDR 을 그대로 쓴다")
    void ovsBatchTargetsBridgeWithCidr() {
        final SiteProperties site = new SiteProperties();
        site.getSwitchDefaults().setBridgeName("br0");
        site.getSwitchDefaults().setUplinkPort("eth0");
        final SwitchPolicy policy = new SwitchPolicy(site);

        final ObjectNode node = policy.batchEnforcementRule(new BatchPolicyContext(
                subnet("s1", "10.0.8.0/24"), "Linux", "OpenVSwitch",
                List.of(connection("10.0.8.0/24", "10.0.9.0/24", true))));

        assertNotNull(node, "OVS 는 일괄 노드를 만들어야 합니다");
        assertEquals("apply", first(node, "command"), "선언적 적용 명령");
        // ⚠️ ovs-ofctl 의 대상은 브리지입니다. 업링크 포트를 넣으면
        //    "no bridge named eth0" 로 실패합니다.
        assertEquals("br0", first(node.path("rule_target"), "bridge_name"), "브리지 대상");
        assertEquals(SwitchPolicy.COOKIE, first(node, "cookie"), "우리 흐름 표식");

        final JsonNode criteria = node.path("acl_rules").get(0).path("match_criteria");
        // OpenFlow 는 CIDR 을 그대로 받습니다. IOS 처럼 반전 마스크로 바꾸면
        // 파서가 혼동합니다.
        assertEquals("10.0.8.0/24", first(criteria, "ip_saddr"), "CIDR 그대로");
        assertNull(criteria.get("ip_saddr_wildcard"), "OVS 에는 반전 마스크를 넣지 않습니다");
    }

    @Test
    @DisplayName("Arista 스위치는 OVS 일괄 노드를 만들지 않는다")
    void aristaDoesNotGetOvsBatch() {
        final SwitchPolicy policy = new SwitchPolicy(new SiteProperties());

        // ⚠️ Arista 는 FastCli 경로이고 ACL 규칙 투입이 구현되어 있지 않습니다.
        //    OVS 문법을 보내면 장치가 조용히 실패합니다.
        assertNull(policy.batchEnforcementRule(new BatchPolicyContext(
                        subnet("s1", "10.0.8.0/24"), "Arista", "Arista vEOS",
                        List.of(connection("10.0.8.0/24", "10.0.9.0/24", true)))),
                "Arista 에는 OVS 일괄 노드를 보내지 않습니다");
    }

    @Test
    @DisplayName("와일드카드 마스크는 넷마스크의 역이다")
    void wildcardIsInverseOfMask() {
        assertEquals("0.0.0.255", PolicyJson.wildcardOf("24"));
        assertEquals("0.0.0.0", PolicyJson.wildcardOf("32"), "호스트 하나는 전부 0");
        assertEquals("0.0.255.255", PolicyJson.wildcardOf("16"));
        assertEquals("255.255.255.255", PolicyJson.wildcardOf("0"));
        // ⚠️ 넷마스크와 값이 같으면 어딘가에서 뒤집기를 빠뜨린 것입니다.
        assertFalse("255.255.255.0".equals(PolicyJson.wildcardOf("24")),
                "넷마스크를 그대로 쓰면 차단이 사라집니다");
        assertNull(PolicyJson.wildcardOf("33"), "범위 밖 접두사는 null");
        assertNull(PolicyJson.wildcardOf("abc"), "숫자가 아니면 null");
    }
}
