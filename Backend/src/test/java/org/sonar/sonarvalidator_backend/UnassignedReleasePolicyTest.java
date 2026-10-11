package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Service.PolicyRegistryService;

import tools.jackson.databind.JsonNode;

/**
 * 배정이 없는 장치에 <b>해제 노드</b>가 나가는지 검증합니다.
 *
 * <h2>⚠️ 이 테스트가 겨냥하는 실패 모드 — 차단이 안 풀림</h2>
 * <p>운영자가 프로젝트에서 장치를 빼거나 금지 연결을 모두 지우면, 그 장치에는
 * 우리가 이전에 넣은 차단(OVS 쿠키 플로우, 라우터 전용 체인)이 남습니다.
 * 정책 노드를 아예 보내지 않으면 장치는 "규칙 없음" 과 "이번엔 안 보냄" 을
 * 구분할 수 없어 <b>차단이 영원히 남습니다.</b>
 *
 * <p>실측: 서브넷 배정을 해제한 Switch-4 에
 * {@code cookie=0x534f4e41, dl_vlan=131, nw_dst=192.168.122.0/24 actions=drop}
 * 이 그대로 남았고, 그 시점 정책은 {@code summary.source=default} 에
 * {@code acl_apply} 가 <b>없었습니다.</b>
 *
 * <h2>반대 방향도 함께 고정한다</h2>
 * <ul>
 *   <li>{@code automaticEnforcementEnabled=false} — 외부 도구가 규칙을 관리하는
 *       배포에서는 우리가 규칙을 지우면 안 됩니다. 해제 노드가 나가면 안 됩니다.</li>
 *   <li>방화벽·VM — {@code batchEnforcementRule} 을 구현하지 않으므로 해제 노드가
 *       없습니다. 방화벽의 외부 nft 규칙을 건드리지 않는 근거입니다.</li>
 *   <li>VM 은 경로 장치가 아니므로 애초에 차단 규칙을 받지 않습니다.</li>
 * </ul>
 */
class UnassignedReleasePolicyTest {

    @Test
    @DisplayName("배정 없는 OVS 스위치는 빈 acl_apply 로 이전 차단을 지운다")
    void unassignedSwitchGetsReleaseNode() {
        final PolicyRegistryService service = new PolicyRegistryService();
        final JsonNode policy = service.forAgent(
                "Switch-1", DeviceType.SWITCH, "Switch-1", "Open vSwitch", "OpenVSwitch");

        assertEquals("default", policy.path("summary").path("source").asString(""));
        assertTrue(policy.path("summary").path("release").asBoolean(false),
                "해제 노드가 실렸음을 응답만 보고 알 수 있어야 합니다");
        assertEquals(0, policy.path("policies").size(), "예제 규칙은 여전히 보내지 않습니다");

        final JsonNode acl = policy.path("acl_apply");
        assertTrue(acl.isObject(), "빈 목록 선언(acl_apply)이 있어야 차단이 풀립니다");
        // ⚠️ 비어 있어야 합니다. 여기에 규칙이 들어가면 "해제" 가 아니라 "차단" 입니다.
        assertTrue(acl.path("acl_rules").isArray() && acl.path("acl_rules").isEmpty(),
                "해제 노드에는 차단 규칙이 없어야 합니다");
        assertEquals("apply", acl.path("command").get(0).asString(""));
        // Prober 의 ovs-ofctl 대상은 브리지입니다. 없으면 적용 자체가 실패합니다.
        assertNotNull(acl.path("rule_target").path("bridge_name").get(0),
                "브리지 이름이 있어야 OVS 흐름을 지울 수 있습니다");
    }

    @Test
    @DisplayName("배정 없는 라우터는 빈 acl_apply 로 이전 차단을 지운다")
    void unassignedRouterGetsReleaseNode() {
        final PolicyRegistryService service = new PolicyRegistryService();
        final JsonNode policy = service.forAgent(
                "Gateway-Router", DeviceType.ROUTER, "Gateway-Router", null, "FRR");

        final JsonNode acl = policy.path("acl_apply");
        assertTrue(acl.isObject(), "라우터도 해제 노드를 받아야 합니다");
        assertTrue(acl.path("acl_rules").isEmpty(), "해제 노드에는 차단 규칙이 없습니다");
        // 전용 체인 이름은 있어야 장치가 그 체인만 비웁니다.
        assertNotNull(acl.path("rule_target").path("acl_name").get(0), "체인 이름이 필요합니다");
    }

    @Test
    @DisplayName("배정 없는 방화벽·VM 에는 해제 노드를 보내지 않는다")
    void firewallAndVmAreNotReleased() {
        final PolicyRegistryService service = new PolicyRegistryService();

        // 방화벽 규칙은 외부 도구가 관리합니다. 우리가 지우면 안 됩니다.
        final JsonNode firewall = service.forAgent(
                "Firewall", DeviceType.FIREWALL, "Firewall", null, "nftables");
        assertNull(firewall.get("acl_apply"), "방화벽의 외부 nft 규칙을 건드리면 안 됩니다");

        // VM 은 경로 장치가 아닙니다.
        final JsonNode vm = service.forAgent("VDI-1", DeviceType.VM, "VDI-1", null, "Ubuntu");
        assertNull(vm.get("acl_apply"), "VM 은 차단 주체가 아닙니다");
    }

    @Test
    @DisplayName("자동 집행이 꺼져 있으면 해제 노드도 만들지 않는다")
    void externalEnforcementModeNeverReleases() {
        final PolicyRegistryService service = new PolicyRegistryService();
        service.setAutomaticEnforcementEnabled(false);

        final JsonNode policy = service.forAgent(
                "Switch-1", DeviceType.SWITCH, "Switch-1", "Open vSwitch", "OpenVSwitch");

        assertEquals("external-enforcement", policy.path("summary").path("source").asString(""));
        assertNull(policy.get("acl_apply"),
                "외부 관리 모드에서는 우리가 규칙을 지우면 안 됩니다");
    }

    @Test
    @DisplayName("관측 제품이 OVS 가 아니면 스위치 해제 노드를 만들지 않는다")
    void nonOvsSwitchIsNotReleased() {
        final PolicyRegistryService service = new PolicyRegistryService();
        // Arista 는 이 전략이 OVS 문법을 만들지 않으므로 대상이 아닙니다.
        final JsonNode policy = service.forAgent(
                "Switch-1", DeviceType.SWITCH, "Switch-1", "Arista", "Arista vEOS");
        assertNull(policy.get("acl_apply"),
                "OVS 가 아닌 스위치에 ovs-ofctl 문법을 보내면 조용히 실패합니다");
    }
}
