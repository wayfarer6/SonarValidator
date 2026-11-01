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

    private static void route(NeutralDeviceConfig config, String protocol, String prefix,
                              String nextHop) {
        final NeutralDeviceConfig.RouteConfig entry = new NeutralDeviceConfig.RouteConfig();
        entry.setProtocol(protocol);
        entry.setPrefix(prefix);
        entry.setNextHop(nextHop);
        config.getRoutes().add(entry);
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

    //  인터넷 노출 판정 (토폴로지의 Internet 노드와 검증이 공유하는 규칙)

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
    void downInterfaceDoesNotRecreateAnObservedEdgeFromItsAddressOrStaleRoute() {
        final var config = config("eth0=10.1.0.1/24", "eth1=10.2.0.1/24");
        final var project = project(subnet("A", "10.1.0.0/24", ZoneClass.OPEN, "R"),
                subnet("B", "10.2.0.0/24", ZoneClass.OPEN, "R"));
        route(config, "connected", "10.2.0.0/24", "directly connected");
        assertThat(ObservedConnectivity.betweenSubnetsOfEachDevice(project, Map.of("R", config))).hasSize(1);
        config.getInterfaces().get("eth1").setAdminState("down");
        assertThat(ObservedConnectivity.betweenSubnetsOfEachDevice(project, Map.of("R", config))).isEmpty();
        assertThat(ObservedConnectivity.isSubnetObserved(config, "10.2.0.0/24")).isFalse();
        config.getInterfaces().get("eth1").setAdminState("up");
        assertThat(ObservedConnectivity.betweenSubnetsOfEachDevice(project, Map.of("R", config))).hasSize(1);
    }

    @Test
    void defaultRouteOnDownUplinkIsNotInternetEvidence() {
        final var config = config("eth0=192.0.2.2/24");
        route(config, "static", "0.0.0.0/0", "192.0.2.1");
        assertThat(ObservedConnectivity.hasDefaultRoute(config)).isTrue();
        config.getInterfaces().get("eth0").setOperState("DOWN");
        assertThat(ObservedConnectivity.hasDefaultRoute(config)).isFalse();
        config.getInterfaces().get("eth0").setOperState("UP");
        assertThat(ObservedConnectivity.hasDefaultRoute(config)).isTrue();
    }

    @Test
    void vlanChildOfDownParentAndUnselectedRoutesAreNotEvidence() {
        final var config = config("eth0=192.0.2.2/24", "eth0.20=10.20.0.1/24");
        config.getInterfaces().get("eth0.20").setParent("eth0");
        config.getInterfaces().get("eth0").setAdminState("down");
        assertThat(ObservedConnectivity.isSubnetObserved(config, "10.20.0.0/24")).isFalse();
        route(config, "static", "0.0.0.0/0", "198.51.100.1");
        config.getRoutes().getLast().setSelected(false);
        assertThat(ObservedConnectivity.hasDefaultRoute(config)).isFalse();
    }
    @Test
    void vlanBridgeAttachmentRequiresExplicitAssociationAndTwoObservedActivePorts() {
        final var switchConfig = config();
        switchConfig.interfaceOrCreate("Ethernet1").setOperState("up");
        switchConfig.interfaceOrCreate("Ethernet2").setOperState("up");
        switchConfig.vlanOrCreate(20).getMembers().addAll(List.of("Et1", "Et2"));
        final var subnet = subnet("data", "10.20.20.0/24", ZoneClass.OPEN, "Ubuntu");
        final var project = project(subnet);
        assertThat(ObservedConnectivity.vlanAttachments(project, "switch", switchConfig)).isEmpty();
        subnet.setVlanId(20);
        assertThat(ObservedConnectivity.vlanAttachments(project, "switch", switchConfig)).singleElement()
                .satisfies(link -> assertThat(link.up()).isTrue());
        switchConfig.interfaceOrCreate("Ethernet2").setOperState("notconnect");
        assertThat(ObservedConnectivity.vlanAttachments(project, "switch", switchConfig)).singleElement()
                .satisfies(link -> assertThat(link.up()).isFalse());
        switchConfig.interfaceOrCreate("Ethernet2").setOperState("up");
        switchConfig.interfaceOrCreate("Ethernet2").setAdminState("down");
        assertThat(ObservedConnectivity.vlanAttachments(project, "switch", switchConfig)).singleElement()
                .satisfies(link -> assertThat(link.up()).isFalse());
        assertThat(ObservedConnectivity.isSubnetObserved(switchConfig, "10.20.20.0/24")).isFalse();
    }

}
