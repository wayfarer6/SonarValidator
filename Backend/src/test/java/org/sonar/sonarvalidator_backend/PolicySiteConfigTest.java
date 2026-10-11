package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Config.SiteProperties;
import org.sonar.sonarvalidator_backend.Policy.strategy.PolicyBuildContext;
import org.sonar.sonarvalidator_backend.Policy.strategy.vendor.arista.veos.SwitchPolicy;
import org.sonar.sonarvalidator_backend.Policy.strategy.vendor.canonical.ubuntu.VmPolicy;
import org.sonar.sonarvalidator_backend.Policy.strategy.vendor.cisco.iosxe.RouterPolicy;
import org.sonar.sonarvalidator_backend.Policy.strategy.vendor.linux.nftables.FirewallPolicy;

import tools.jackson.databind.node.ObjectNode;

/**
 * 정책 전략이 하드코딩 대신 <b>사이트 설정</b>을 쓰는지 검증합니다.
 *
 * <h2>⚠️ 왜 이 테스트가 필요한가</h2>
 * <p>설정 클래스를 만들어도 전략이 상수를 그대로 쓰면 의미가 없습니다.
 * 여기서는 설정을 바꾸면 <b>실제 정책 JSON 이 달라지는지</b>를 봅니다 —
 * "제품·배포마다 다른 값을 코드 수정 없이 반영한다" 는 목표의 증거입니다.
 *
 * <p>그래서 설정 접근자만 확인하지 않고 {@code defaultRule()} 이 만들어낸
 * 노드의 <b>값</b>을 봅니다. (설정이 실제로 정책까지 흘러가는지)
 */
class PolicySiteConfigTest {

    /** 정책 노드의 배열 첫 값을 꺼냅니다. */
    private static String first(ObjectNode node, String key) {
        final var array = node.get(key);
        assertNotNull(array, key + " 키가 있어야 합니다");
        assertTrue(array.isArray() && array.size() > 0, key + " 배열이 비어 있으면 안 됩니다");
        return array.get(0).asText();
    }

    @Test
    @DisplayName("스위치 폴백 포트는 설정값을 따른다 (하드코딩이 아니다)")
    void switchDefaultPortComesFromConfiguration() {
        final SiteProperties site = new SiteProperties();
        site.getSwitchDefaults().setDefaultPort("Ethernet9");

        final SwitchPolicy policy = new SwitchPolicy(site);
        final ObjectNode rule = policy.defaultRule();

        // ⚠️ 이전에는 "Ethernet 1" 이 코드에 박혀 있었습니다.
        assertEquals("Ethernet9", first(rule, "port"),
                "설정을 바꾸면 정책의 port 도 바뀌어야 합니다");
    }

    @Test
    @DisplayName("스위치 실행 규칙의 업링크·브리지도 설정값을 따른다")
    void switchEnforcementUsesConfiguredUplink() {
        final SiteProperties site = new SiteProperties();
        site.getSwitchDefaults().setUplinkPort("Ethernet1");
        site.getSwitchDefaults().setBridgeName("Bridge0");

        final SwitchPolicy policy = new SwitchPolicy(site);
        final PolicyBuildContext context = PolicyBuildContext.forConnection(
                subnet("s1", "10.0.8.0/24"),
                "Arista",
                "Arista vEOS",
                new PolicyBuildContext.ConnectionView(
                        "rule-1", true, "s2", "10.0.9.0/24", null,
                        "10.0.8.0/24", "10.0.9.0/24", "tcp", null, true, "금지"));

        final ObjectNode rule = policy.enforcementRule(context);
        assertNotNull(rule, "실행 규칙 생성");
        // ⚠️ 이전에는 UPLINK_PORT/BRIDGE_NAME 상수였습니다.
        assertEquals("Ethernet1", first(rule, "applied_interface"), "설정된 업링크");
        assertEquals("Bridge0", first(rule, "bridge_name"), "설정된 브리지");
    }

    @Test
    @DisplayName("방화벽 정책은 설정된 nftables 테이블 이름을 쓴다")
    void firewallUsesConfiguredTable() {
        final SiteProperties site = new SiteProperties();
        site.getFirewallDefaults().setTableName("sonar_custom");

        final FirewallPolicy policy = new FirewallPolicy(site);
        final PolicyBuildContext context = PolicyBuildContext.forDeclaration(
                subnet("s1", "10.0.8.0/24"), "Linux", "nftables");

        final ObjectNode rule = policy.declarationRule(context);
        // ⚠️ 이전에는 FIREWALL_TABLE = "sonar" 로 박혀 있었습니다.
        assertEquals("sonar_custom", first(rule, "table_name"), "설정된 테이블 이름");
    }

    @Test
    @DisplayName("라우터 폴백 인터페이스는 설정값을 따른다")
    void routerDefaultInterfaceComesFromConfiguration() {
        final SiteProperties site = new SiteProperties();
        site.getRouterDefaults().setDefaultInterface("Ethernet1");

        final RouterPolicy policy = new RouterPolicy(site);
        final ObjectNode rule = policy.defaultRule();

        assertEquals("Ethernet1", first(rule, "interface"),
                "설정을 바꾸면 라우터 인터페이스도 바뀌어야 합니다");
    }

    @Test
    @DisplayName("VM 설정 백엔드는 설정값을 따르되 인터페이스는 자리표시자를 유지한다")
    void vmConfigBackendAndInterfaceToken() {
        final SiteProperties custom = new SiteProperties();
        custom.getVmDefaults().setConfigBackend("networkd");
        final VmPolicy customPolicy = new VmPolicy(custom);

        final ObjectNode customRule = customPolicy.declarationRule(
                PolicyBuildContext.forDeclaration(subnet("s1", "10.20.111.0/24"),
                        "Canonical", "Ubuntu Linux"));
        assertEquals("networkd", first(customRule, "config_backend"), "설정된 백엔드");

        // ⚠️ VM 의 인터페이스는 설정이 아니라 <b>자리표시자</b>여야 합니다.
        //    서버는 실제 NIC 이름(ens33/eth0/ens3)을 알 수 없어, 고정하면
        //    `Cannot find device` 로 모든 VM 정책이 실패합니다.
        final VmPolicy defaultPolicy = new VmPolicy(new SiteProperties());
        final ObjectNode defaultRule = defaultPolicy.defaultRule();

        assertEquals(VmPolicy.PRIMARY_INTERFACE_TOKEN, first(defaultRule, "interface"),
                "VM 인터페이스는 자리표시자여야 합니다");
        assertFalse("eth0".equals(first(defaultRule, "interface")),
                "설정값으로 고정하면 치환 경로가 끊깁니다");
    }

    /**
     * 테스트용 최소 서브넷을 만듭니다. (JPA 없이 도메인 값만 필요)
     *
     * @param id   서브넷 식별자
     * @param cidr CIDR 대역
     * @return 채워진 서브넷
     */
    private static org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet subnet(String id, String cidr) {
        final var value = new org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet();
        value.setSubnetId(id);
        value.setCidr(cidr);
        return value;
    }
}