package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Service.ObservedConnectivity;

/**
 * 라우팅 테이블에서 대역 간 실제 연결을 유도하는 규칙을 검증합니다.
 *
 * <p>핵심 계약:
 * <ol>
 *   <li>한 장치가 두 대역에 L3 주소를 가지면 그 대역들은 <b>연결</b>입니다.</li>
 *   <li>라우팅 테이블이 비어 있어도(실측 Arista 는 경로 0건) 인터페이스 주소로 판단합니다.</li>
 *   <li>등급을 건너뛰는 조합은 {@code forbidden} 으로 표시됩니다(화면에서 빨간 선).</li>
 *   <li>연결이 없는 장치·대역은 만들지 않습니다(오탐 금지).</li>
 * </ol>
 */
class ObservedConnectivityTest {

    private static Project project(ProjectSubnet... subnets) {
        final Project project = new Project();
        project.setProjectKey("PRJ-TEST");
        for (final ProjectSubnet subnet : subnets) {
            project.getSubnets().add(subnet);
        }
        return project;
    }

    private static ProjectSubnet subnet(String id, String cidr, ZoneClass zone, String agentId) {
        final PolicySubnet domain = PolicySubnet.of(id, cidr, zone);
        domain.setAgentId(agentId);
        return ProjectSubnet.from(domain);
    }

    private static NeutralDeviceConfig config(String... interfaceAddresses) {
        final NeutralDeviceConfig config = new NeutralDeviceConfig();
        for (final String entry : interfaceAddresses) {
            final int at = entry.indexOf('=');
            final String name = entry.substring(0, at);
            final String address = entry.substring(at + 1);
            config.interfaceOrCreate(name).getAddresses().add(address);
        }
        return config;
    }

    /** 라우팅 테이블 한 줄을 추가합니다. (수집 파서가 만드는 형태) */
    private static void route(NeutralDeviceConfig config, String protocol, String prefix,
                              String nextHop) {
        final NeutralDeviceConfig.RouteConfig entry = new NeutralDeviceConfig.RouteConfig();
        entry.setProtocol(protocol);
        entry.setPrefix(prefix);
        entry.setNextHop(nextHop);
        config.getRoutes().add(entry);
    }

    private static void mac(NeutralDeviceConfig config, String interfaceName, String value) {
        config.interfaceOrCreate(interfaceName).setMacAddress(value);
    }

    private static void neighbor(NeutralDeviceConfig config, String interfaceName,
                                 String address, String mac) {
        final NeutralDeviceConfig.ArpEntry entry = new NeutralDeviceConfig.ArpEntry();
        entry.setInterfaceName(interfaceName);
        entry.setAddress(address);
        entry.setMac(mac);
        config.getArpEntries().add(entry);
    }

    @Test
    @DisplayName("라우팅 테이블이 비어 있어도 SVI 주소가 있으면 VLAN 간 연결을 찾는다")
    void detectsInterVlanFromInterfaceAddresses() {
        // 실측 Arista vEOS: VLAN8/VLAN9 는 SVI 이고 수집된 경로는 0건입니다.
        final NeutralDeviceConfig arista = config("Vlan8=10.0.8.1/24", "Vlan9=10.0.9.1/24");
        final Project project = project(
                subnet("VLAN8", "10.0.8.0/24", ZoneClass.OPEN, "Arista-Switch"),
                subnet("VLAN9", "10.0.9.0/24", ZoneClass.OPEN, "Arista-Switch"));

        final List<ObservedConnectivity.Link> links = ObservedConnectivity
                .betweenSubnetsOfEachDevice(project, Map.of("Arista-Switch", arista));

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.agentId()).isEqualTo("Arista-Switch");
            assertThat(link.sourceId()).isEqualTo("VLAN8");
            assertThat(link.targetId()).isEqualTo("VLAN9");
            assertThat(link.forbidden()).isFalse();
        });
    }

    @Test
    @DisplayName("라우팅 테이블의 connected 경로도 L3 근거로 쓴다")
    void usesConnectedRoutesAsEvidence() {
        final NeutralDeviceConfig cisco = new NeutralDeviceConfig();
        route(cisco, "static", "0.0.0.0/0", "192.168.122.1");
        route(cisco, "connected", "192.168.122.0/24", "directly connected");
        route(cisco, "connected", "172.128.0.0/24", "directly connected");

        // 프로젝트는 그 안의 주소를 /32 로 가집니다(포함 관계 판정).
        final Project project = project(
                subnet("A", "192.168.122.254/32", ZoneClass.OPEN, "Cisco-Router"),
                subnet("B", "172.128.0.1/32", ZoneClass.OPEN, "Cisco-Router"));

        final List<ObservedConnectivity.Link> links = ObservedConnectivity
                .betweenSubnetsOfEachDevice(project, Map.of("Cisco-Router", cisco));

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.sourceCidr()).isEqualTo("192.168.122.254/32");
            assertThat(link.targetCidr()).isEqualTo("172.128.0.1/32");
        });
    }

    @Test
    @DisplayName("등급을 건너뛰는 연결은 forbidden 으로 표시된다")
    void marksZoneSkippingAsForbidden() {
        final NeutralDeviceConfig router = config("Vlan8=10.0.8.1/24", "Vlan9=10.0.9.1/24");
        final Project project = project(
                subnet("VLAN8", "10.0.8.0/24", ZoneClass.CONFIDENTIAL, "Arista-Switch"),
                subnet("VLAN9", "10.0.9.0/24", ZoneClass.OPEN, "Arista-Switch"));

        final List<ObservedConnectivity.Link> links = ObservedConnectivity
                .betweenSubnetsOfEachDevice(project, Map.of("Arista-Switch", router));

        assertThat(links).singleElement()
                .satisfies(link -> assertThat(link.forbidden()).isTrue());
    }

    @Test
    @DisplayName("수집된 설정이 없으면 연결을 만들지 않는다")
    void withoutCollectedConfigThereIsNoLink() {
        final Project project = project(
                subnet("VLAN8", "10.0.8.0/24", ZoneClass.OPEN, "Arista-Switch"),
                subnet("VLAN9", "10.0.9.0/24", ZoneClass.OPEN, "Arista-Switch"));

        assertThat(ObservedConnectivity.betweenSubnetsOfEachDevice(project, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("다른 장치의 대역은 서로 연결하지 않는다")
    void subnetsOfDifferentDevicesAreNotLinked() {
        final Project project = project(
                subnet("A", "10.0.8.0/24", ZoneClass.OPEN, "Arista-Switch"),
                subnet("B", "172.18.10.0/24", ZoneClass.OPEN, "Cisco-Router"));

        final List<ObservedConnectivity.Link> links = ObservedConnectivity.betweenSubnetsOfEachDevice(
                project,
                Map.of("Arista-Switch", config("Vlan8=10.0.8.1/24"),
                        "Cisco-Router", config("GigabitEthernet1=172.18.10.2/24")));

        assertThat(links).isEmpty();
    }

    @Test
    @DisplayName("같은 대역이 두 행으로 있어도 연결이 중복되지 않는다")
    void duplicateSubnetRowsDoNotDuplicateLinks() {
        final NeutralDeviceConfig router = config("Vlan8=10.0.8.1/24", "Vlan9=10.0.9.1/24");
        final Project project = project(
                subnet("VLAN8", "10.0.8.0/24", ZoneClass.OPEN, "Arista-Switch"),
                subnet("VLAN8-copy", "10.0.8.0/24", ZoneClass.OPEN, "Arista-Switch"),
                subnet("VLAN9", "10.0.9.0/24", ZoneClass.OPEN, "Arista-Switch"));

        final List<ObservedConnectivity.Link> links = ObservedConnectivity
                .betweenSubnetsOfEachDevice(project, Map.of("Arista-Switch", router));

        assertThat(links).singleElement();
    }

    @Test
    @DisplayName("장치 대역 밖의 주소는 연결로 보지 않는다")
    void unrelatedSubnetIsNotLinked() {
        final NeutralDeviceConfig router = config("Vlan8=10.0.8.1/24", "Vlan9=10.0.9.1/24");
        final Project project = project(
                subnet("VLAN8", "10.0.8.0/24", ZoneClass.OPEN, "Arista-Switch"),
                subnet("VLAN9", "10.0.9.0/24", ZoneClass.OPEN, "Arista-Switch"),
                // 이 장치가 라우팅하지 않는 대역
                subnet("OTHER", "10.99.0.0/24", ZoneClass.OPEN, "Arista-Switch"));

        final List<ObservedConnectivity.Link> links = ObservedConnectivity
                .betweenSubnetsOfEachDevice(project, Map.of("Arista-Switch", router));

        assertThat(links).hasSize(1);
        assertThat(links.getFirst().sourceCidr()).doesNotContain("10.99");
        assertThat(links.getFirst().targetCidr()).doesNotContain("10.99");
    }

    // ------------------------------------------------------------------
    //  인터넷 노출 판정 (토폴로지의 Internet 노드와 검증이 공유하는 규칙)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("기본 경로가 있는 장치만 인터넷 노출로 본다")
    void onlyDefaultRouteMakesADeviceInternetExposed() {
        final NeutralDeviceConfig cisco = new NeutralDeviceConfig();
        route(cisco, "static", "0.0.0.0/0", "192.168.122.1");
        route(cisco, "connected", "172.128.0.0/24", "directly connected");

        // Arista 는 기본 경로가 없습니다(수집 경로 0건).
        final NeutralDeviceConfig arista = config("Vlan8=10.0.8.1/24", "Vlan9=10.0.9.1/24");

        final Set<String> exposed = ObservedConnectivity.internetExposedAgents(
                Map.of("Cisco-Router", cisco, "Arista-Switch", arista));

        assertThat(exposed).containsExactly("Cisco-Router");
    }

    @Test
    @DisplayName("default_route 플래그만 있어도 인터넷 노출로 본다")
    void defaultRouteFlagAloneCounts() {
        final NeutralDeviceConfig config = new NeutralDeviceConfig();
        final NeutralDeviceConfig.RouteConfig entry = new NeutralDeviceConfig.RouteConfig();
        entry.setProtocol("static");
        entry.setPrefix("0.0.0.0/0");
        entry.setDefaultRoute(true);
        config.getRoutes().add(entry);

        assertThat(ObservedConnectivity.hasDefaultRoute(config)).isTrue();
        assertThat(ObservedConnectivity.internetExposedAgents(Map.of("R", config)))
                .containsExactly("R");
    }

    @Test
    @DisplayName("수집 설정이 없으면 인터넷 노출도 없다")
    void noConfigsMeansNoExposure() {
        assertThat(ObservedConnectivity.internetExposedAgents(Map.of())).isEmpty();
        assertThat(ObservedConnectivity.internetExposedAgents(null)).isEmpty();
        assertThat(ObservedConnectivity.hasDefaultRoute(null)).isFalse();
    }

    @Test
    @DisplayName("기본 경로 nextHop 소유자를 찾아 방화벽-라우터-게이트웨이 홉을 보존한다")
    void resolvesDefaultRouteNextHopOwners() {
        final NeutralDeviceConfig firewall = config("wan=10.99.143.2/24");
        route(firewall, "static", "0.0.0.0/0", "10.99.143.1");

        final NeutralDeviceConfig c4iRouter = config(
                "inside=10.99.143.1/24", "outside=10.99.10.4/24");
        route(c4iRouter, "static", "0.0.0.0/0", "10.99.10.1");

        final NeutralDeviceConfig gateway = config(
                "inside=10.99.10.1/24", "outside=192.168.122.10/24");
        route(gateway, "static", "0.0.0.0/0", "192.168.122.1");

        final List<ObservedConnectivity.DefaultRouteHop> hops =
                ObservedConnectivity.defaultRouteHops(Map.of(
                        "Firewall", firewall,
                        "C4I-Network-Router", c4iRouter,
                        "Gateway-Router", gateway));

        assertThat(hops).anySatisfy(hop -> {
            assertThat(hop.sourceAgentId()).isEqualTo("Firewall");
            assertThat(hop.targetAgentId()).isEqualTo("C4I-Network-Router");
            assertThat(hop.nextHop()).isEqualTo("10.99.143.1");
            assertThat(hop.targetInterface()).isEqualTo("inside");
        }).anySatisfy(hop -> {
            assertThat(hop.sourceAgentId()).isEqualTo("C4I-Network-Router");
            assertThat(hop.targetAgentId()).isEqualTo("Gateway-Router");
            assertThat(hop.nextHop()).isEqualTo("10.99.10.1");
        }).anySatisfy(hop -> {
            assertThat(hop.sourceAgentId()).isEqualTo("Gateway-Router");
            assertThat(hop.targetAgentId()).isNull();
            assertThat(hop.nextHop()).isEqualTo("192.168.122.1");
        });
    }

    @Test
    @DisplayName("비활성 기본 경로는 실제 홉으로 그리지 않는다")
    void ignoresUnselectedDefaultRouteHops() {
        final NeutralDeviceConfig router = config("wan=192.0.2.2/24");
        final NeutralDeviceConfig.RouteConfig route = new NeutralDeviceConfig.RouteConfig();
        route.setPrefix("0.0.0.0/0");
        route.setNextHop("192.0.2.1");
        route.setSelected(false);
        router.getRoutes().add(route);

        assertThat(ObservedConnectivity.defaultRouteHops(Map.of("Router", router))).isEmpty();
    }

    @Test
    @DisplayName("link-local 이웃 MAC으로 스위치와 상위 라우터의 물리 연결을 찾는다")
    void detectsPhysicalLinkFromNeighborMac() {
        final NeutralDeviceConfig router = config("eth1=10.99.10.1/24");
        mac(router, "eth1", "02:42:81:2b:10:01");
        neighbor(router, "eth1", "fe80::42:81ff:fe2b:1100", "02:42:81:2b:11:00");

        final NeutralDeviceConfig upstreamSwitch = config();
        upstreamSwitch.setDeviceType("SWITCH");
        mac(upstreamSwitch, "eth0", "02-42-81-2B-11-00");

        final Project project = project();
        project.setManagementPrefix("172.16.255.0/24");

        assertThat(ObservedConnectivity.physicalLinks(project, Map.of(
                "Gateway-Router", router, "Switch-0", upstreamSwitch)))
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.sourceAgentId()).isEqualTo("Gateway-Router");
                    assertThat(link.sourceInterface()).isEqualTo("eth1");
                    assertThat(link.targetAgentId()).isEqualTo("Switch-0");
                    assertThat(link.targetInterface()).isEqualTo("eth0");
                });
    }

    @Test
    @DisplayName("관리 인터페이스에서 관측한 이웃은 물리 업무망 연결에서 제외한다")
    void excludesManagementNeighborLink() {
        final NeutralDeviceConfig router = config("eth7=172.16.255.10/24");
        mac(router, "eth7", "02:42:81:2b:10:07");
        neighbor(router, "eth7", "fe80::42:81ff:fe2b:110b", "02:42:81:2b:11:0b");

        final NeutralDeviceConfig managementSwitch = config("eth11=172.16.255.20/24");
        mac(managementSwitch, "eth11", "02:42:81:2b:11:0b");

        final Project project = project();
        project.setManagementPrefix("172.16.255.0/24");

        assertThat(ObservedConnectivity.physicalLinks(project, Map.of(
                "Router", router, "Switch", managementSwitch))).isEmpty();
    }

    @Test
    @DisplayName("마지막 Gateway 외부 홉까지 이어진 모든 상위 장비를 인터넷 도달 가능으로 본다")
    void resolvesInternetReachabilityAcrossRouteChain() {
        final List<ObservedConnectivity.DefaultRouteHop> hops = List.of(
                new ObservedConnectivity.DefaultRouteHop(
                        "Firewall", "10.99.143.1", "wan", "C4I-Router", "inside"),
                new ObservedConnectivity.DefaultRouteHop(
                        "C4I-Router", "10.99.10.1", "outside", "Gateway-Router", "inside"),
                new ObservedConnectivity.DefaultRouteHop(
                        "Gateway-Router", "192.168.122.1", "outside", null, null),
                new ObservedConnectivity.DefaultRouteHop(
                        "Isolated-Router", "10.0.0.1", "wan", "Unknown-Router", "inside"));

        assertThat(ObservedConnectivity.internetReachableAgents(hops))
                .containsExactlyInAnyOrder("Firewall", "C4I-Router", "Gateway-Router")
                .doesNotContain("Isolated-Router", "Unknown-Router");
    }

    // ------------------------------------------------------------------
    //  방화벽(강제 장비) 제외 — SVI 보유는 "연결" 이 아니라 "존 분리" 의 증거
    // ------------------------------------------------------------------

    @Test
    @DisplayName("방화벽은 여러 존에 SVI 를 가져도 VLAN 간 연결로 보지 않는다")
    void firewallDoesNotCreateInterVlanLinks() {
        final NeutralDeviceConfig firewall =
                config("eth1.131=10.10.131.1/24", "eth1.132=10.10.132.1/24");
        firewall.setDeviceType("FIREWALL");
        final Project project = project(
                subnet("VLAN131", "10.10.131.0/24", ZoneClass.CONFIDENTIAL, "Firewall"),
                subnet("VLAN132", "10.10.132.0/24", ZoneClass.SENSITIVE, "Firewall"));

        final List<ObservedConnectivity.Link> links = ObservedConnectivity
                .betweenSubnetsOfEachDevice(project, Map.of("Firewall", firewall));

        assertThat(links).isEmpty();
    }

    @Test
    @DisplayName("방화벽이 같은 등급 VLAN 통신을 명시적으로 허용하면 VLAN 간선을 만든다")
    void firewallCreatesLinksForExplicitlyAcceptedZonePair() {
        final NeutralDeviceConfig firewall =
                config("eth1.131=10.10.131.1/24", "eth1.132=10.10.132.1/24");
        firewall.setDeviceType("FIREWALL");
        firewall.getFirewallRules().add(
                "nft rule {\"comment\":\"CSO-C-to-C\",\"accept\":null}");
        final Project project = project(
                subnet("VLAN131", "10.10.131.0/24", ZoneClass.CONFIDENTIAL, "Firewall"),
                subnet("VLAN132", "10.10.132.0/24", ZoneClass.CONFIDENTIAL, "Firewall"));

        assertThat(ObservedConnectivity.betweenSubnetsOfEachDevice(
                project, Map.of("Firewall", firewall)))
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.sourceId()).isEqualTo("VLAN131");
                    assertThat(link.targetId()).isEqualTo("VLAN132");
                });
    }

    @Test
    @DisplayName("방화벽의 존-인터넷 차단 규칙은 기본 경로가 있어도 인터넷 가능 표시를 막는다")
    void firewallInternetDenyOverridesDefaultRouteReachability() {
        final NeutralDeviceConfig firewall = config("wan=10.99.143.2/24");
        firewall.setDeviceType("FIREWALL");
        firewall.getFirewallRules().add(
                "nft rule {\"comment\":\"CSO-C-to-Open-Internet-deny\",\"drop\":null}");

        assertThat(ObservedConnectivity.internetAllowed(
                "Firewall", firewall, ZoneClass.CONFIDENTIAL)).isFalse();
        assertThat(ObservedConnectivity.internetAllowed(
                "Firewall", firewall, ZoneClass.SENSITIVE)).isTrue();
    }

    @Test
    @DisplayName("식별자에 Firewall 이 들어가면 보고 유형이 없어도 제외한다")
    void firewallIsExcludedByDeviceIdInference() {
        final NeutralDeviceConfig firewall =
                config("eth1.131=10.10.131.1/24", "eth1.132=10.10.132.1/24");
        final Project project = project(
                subnet("VLAN131", "10.10.131.0/24", ZoneClass.CONFIDENTIAL, "GNS3.Firewall"),
                subnet("VLAN132", "10.10.132.0/24", ZoneClass.SENSITIVE, "GNS3.Firewall"));

        assertThat(ObservedConnectivity.betweenSubnetsOfEachDevice(
                project, Map.of("GNS3.Firewall", firewall))).isEmpty();
    }
}
