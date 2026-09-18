package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.Config.DeviceConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.sonar.sonarvalidator_backend.Model.Config.Vendors.AlpineFirewallConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.Vendors.AristaSwitchConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.Vendors.CiscoRouterConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.Vendors.FrrRouterConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.Vendors.LinuxVmConfigParser;
import org.sonar.sonarvalidator_backend.Model.Config.Vendors.OpenVSwitchConfigParser;
import org.sonar.sonarvalidator_backend.Service.DeviceConfigService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 벤더별 설정 파서가 Agent 텔레메트리를 중립 설정으로 올바르게 옮기는지 검증합니다.
 *
 * <p>아래 JSON 은 실제 GNS3 랩에서 에이전트가 보낸 payload 를 그대로 축약한 것입니다.
 * (mock 서버 로그에서 캡처) 그래야 파서가 실제 수집 형식에 대해 검증됩니다.
 */
class DeviceConfigParserTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private JsonNode json(String text) {
        return mapper.readTree(text);
    }

    /**
     * FRR 라우터: 커널 {@code ip route show} 는 라우트 코드가 없고
     * {@code destination}/{@code via} 키를 씁니다. 중립 설정으로 흡수돼야 합니다.
     */
    @Test
    @DisplayName("FRR 라우터: 커널 형식 라우팅을 prefix/nextHop 으로 흡수한다")
    void frrKernelRoutes() {
        final JsonNode payload = json("""
                {
                  "agent": "agent-test",
                  "kernel": "5.15.83-0-virt",
                  "nic_status": {
                    "interfaces": [
                      {"name":"lo","mtu":"65536","state":"UNKNOWN","flags":["LOOPBACK","UP"],
                       "addresses":[{"family":"inet","address":"10.255.255.1","prefix_len":32}]},
                      {"name":"eth1","mtu":"1500","mac":"0c:97:0f:9f:00:01","state":"UP",
                       "addresses":[{"family":"inet","address":"10.99.10.1","prefix_len":24}]}
                    ]
                  },
                  "route_status": {
                    "routes": [
                      {"destination":"0.0.0.0/0","via":"192.168.122.1","interface_name":"eth0","is_default":true},
                      {"destination":"10.10.128.0/21","via":"10.99.10.4","interface_name":"eth1",
                       "protocol":"ospf","metric":"20"}
                    ]
                  },
                  "arp_table": {"entries":[
                    {"address":"172.16.255.4","mac":"0c:97:0f:9f:00:07","interface":"eth7","state":"STALE"}
                  ]}
                }
                """);

        final DeviceConfigParser parser = new FrrRouterConfigParser();
        assertTrue(parser.supports("FRR", "ROUTER"));

        final NeutralDeviceConfig config = parser.parse("agent-test", "FRR", payload);

        assertEquals("FRR", config.getVendor());
        assertEquals("ROUTER", config.getDeviceType());
        assertEquals(2, config.getInterfaces().size());
        assertEquals(2, config.getRoutes().size());

        // Linux 표기(destination/via)가 중립 키로 옮겨졌는지.
        final NeutralDeviceConfig.RouteConfig defaultRoute = config.getRoutes().get(0);
        assertEquals("0.0.0.0/0", defaultRoute.getPrefix());
        assertEquals("192.168.122.1", defaultRoute.getNextHop());
        assertTrue(defaultRoute.getDefaultRoute());

        final NeutralDeviceConfig.RouteConfig ospfRoute = config.getRoutes().get(1);
        assertEquals("ospf", ospfRoute.getProtocol());
        assertFalse(ospfRoute.getDefaultRoute());
        assertEquals(20L, ospfRoute.getMetric());

        // 인터페이스 주소가 CIDR 로 정규화됐는지.
        assertEquals(List.of("10.99.10.1/24"), config.getInterfaces().get("eth1").getAddresses());
        assertEquals(1, config.getArpEntries().size());
        assertEquals("1", config.getMetadata().get("ospfRouteCount"));
    }

    /**
     * Alpine 방화벽: 서브인터페이스 이름에서 VLAN 을 분해해야 합니다.
     * 이 랩에서 방화벽이 VLAN 종단을 하므로 존 게이트웨이 매핑에 필요합니다.
     */
    @Test
    @DisplayName("Alpine 방화벽: eth1.131 을 VLAN 131 + parent eth1 로 분해한다")
    void alpineFirewallVlanTermination() {
        final JsonNode payload = json("""
                {
                  "agent": "fw-01",
                  "nft_rules": {},
                  "nic_status": {
                    "interfaces": [
                      {"name":"eth0","mtu":"1500","mac":"02:42:c3:a4:51:00","state":"UNKNOWN",
                       "addresses":[{"family":"inet","address":"10.99.143.2","prefix_len":24}]},
                      {"name":"eth1.131","parent":"eth1","mtu":"1500","state":"UP",
                       "addresses":[{"family":"inet","address":"10.10.131.1","prefix_len":24}]},
                      {"name":"eth1.132","parent":"eth1","mtu":"1500","state":"UP",
                       "addresses":[{"family":"inet","address":"10.10.132.1","prefix_len":24}]}
                    ]
                  },
                  "firewall_rules": {
                    "tables": [
                      {"family":"inet","name":"filter","chains":[
                        {"name":"input","policy":"accept","rules":[]},
                        {"name":"forward","policy":"accept","rules":[]}
                      ]}
                    ]
                  }
                }
                """);

        final DeviceConfigParser parser = new AlpineFirewallConfigParser();
        assertTrue(parser.supports("nftables", "FIREWALL"));

        final NeutralDeviceConfig config = parser.parse("fw-01", "nftables", payload);

        assertEquals("FIREWALL", config.getDeviceType());

        // 서브인터페이스가 VLAN 으로 등록되고 물리 포트가 parent 로 남는지.
        final NeutralDeviceConfig.InterfaceConfig sub = config.getInterfaces().get("eth1.131");
        assertNotNull(sub);
        assertEquals(131, sub.getAccessVlan());
        assertEquals("eth1", sub.getParent());
        assertEquals("routed", sub.getMode());
        assertEquals(List.of("10.10.131.1/24"), sub.getAddresses());

        assertTrue(config.getVlans().containsKey(131));
        assertTrue(config.getVlans().get(131).getMembers().contains("eth1.131"));

        // nft 규칙이 사람이 읽는 형태로 펼쳐졌는지.
        assertEquals(2, config.getFirewallRules().size());
        assertTrue(config.getFirewallRules().get(0).contains("inet filter chain input policy accept"),
                config.getFirewallRules().toString());
    }

    /**
     * Open vSwitch: 액세스 포트의 tag 와 트렁크의 trunks 가
     * VLAN 정의와 포트 모드로 동시에 반영돼야 합니다.
     */
    @Test
    @DisplayName("Open vSwitch: tag 는 access VLAN, trunks 는 trunk VLAN 으로 반영된다")
    void openVSwitchVlansAndTrunks() {
        final JsonNode payload = json("""
                {
                  "agent": "sw-01",
                  "nic_status": {"interfaces":[
                    {"name":"eth0","mtu":"1500","mac":"02:42:4f:ff:fe:00","state":"UNKNOWN",
                     "addresses":[]},
                    {"name":"eth1","mtu":"1500","mac":"02:42:4f:ff:fe:01","state":"UNKNOWN",
                     "addresses":[]}
                  ]},
                  "vlan_status": {"vlans":[
                    {"vlan_id":111,"name":"VLAN111","status":"active","ports":["eth1"]},
                    {"vlan_id":112,"name":"VLAN112","status":"active","ports":["eth2"]}
                  ]},
                  "trunk_status": {"ports":[
                    {"name":"eth0","mode":"trunk","trunk_vlans":[111,112],"admin_enabled":true},
                    {"name":"eth1","mode":"access","access_vlan":111,"trunk_vlans":[],
                     "admin_enabled":true}
                  ]},
                  "ovs_topology": {"bridges":[{"name":"br0","ports":[]}]}
                }
                """);

        final DeviceConfigParser parser = new OpenVSwitchConfigParser();
        assertTrue(parser.supports("OpenVSwitch", "SWITCH"));

        final NeutralDeviceConfig config = parser.parse("sw-01", "OpenVSwitch", payload);

        assertEquals("SWITCH", config.getDeviceType());
        assertEquals("OpenvSwitch", config.getFormat());
        assertEquals(List.of("br0"), config.getBridges());

        // 트렁크 포트
        final NeutralDeviceConfig.InterfaceConfig uplink = config.getInterfaces().get("eth0");
        assertEquals("trunk", uplink.getMode());
        assertEquals(List.of(111, 112), uplink.getTrunkVlans());

        // 액세스 포트
        final NeutralDeviceConfig.InterfaceConfig access = config.getInterfaces().get("eth1");
        assertEquals("access", access.getMode());
        assertEquals(111, access.getAccessVlan());

        // VLAN 정의
        assertEquals("VLAN111", config.getVlans().get(111).getName());
        assertTrue(config.getVlans().get(111).getMembers().contains("eth1"));
    }

    /**
     * Linux VM: 기본 경로와 인터페이스 주소가 그대로 올라와야 합니다.
     */
    @Test
    @DisplayName("Linux VM: 인터페이스/라우팅/이웃을 중립 구조로 옮긴다")
    void linuxVmBasics() {
        final JsonNode payload = json("""
                {
                  "agent": "atics-01",
                  "kernel": "6.8.0",
                  "nic_status": {"interfaces":[
                    {"name":"ens3","mtu":"1500","mac":"0c:f3:02:45:00:00","state":"UP",
                     "addresses":[
                       {"family":"inet","address":"10.10.131.10","prefix_len":24},
                       {"family":"inet6","address":"fe80::ef3:2ff:fe45:0","prefix_len":64}
                     ]}
                  ]},
                  "route_status": {"routes":[
                    {"destination":"0.0.0.0/0","via":"10.10.131.1","interface_name":"ens3",
                     "is_default":true},
                    {"destination":"10.10.131.0/24","interface_name":"ens3"}
                  ]},
                  "arp_table": {"entries":[
                    {"address":"10.10.131.1","mac":"0c:aa:bb:cc:dd:ee","interface":"ens3",
                     "state":"REACHABLE"}
                  ]}
                }
                """);

        final DeviceConfigParser parser = new LinuxVmConfigParser();
        assertTrue(parser.supports("Ubuntu", "VM"));

        final NeutralDeviceConfig config = parser.parse("atics-01", "Ubuntu", payload);

        assertEquals("VM", config.getDeviceType());
        assertEquals(2, config.getRoutes().size());
        assertTrue(config.getRoutes().get(0).getDefaultRoute());

        final NeutralDeviceConfig.InterfaceConfig ens3 = config.getInterfaces().get("ens3");
        assertEquals(2, ens3.getAddresses().size());
        assertEquals("0c:f3:02:45:00:00", ens3.getMacAddress());
        assertEquals(1500, ens3.getMtu());

        assertEquals(1, config.getArpEntries().size());
        assertEquals("REACHABLE", config.getArpEntries().get(0).getState());
    }

    /**
     * Arista: VLAN 정의와 switchport 정보를 합쳐야 포트-VLAN 매핑이 완성됩니다.
     */
    @Test
    @DisplayName("Arista: show vlan brief 와 switchport 를 합쳐 포트 모드를 만든다")
    void aristaVlanAndSwitchport() {
        final JsonNode payload = json("""
                {
                  "agent": "arista-01",
                  "nic_status": {"interfaces":[
                    {"name":"Ethernet1","addresses":[
                      {"family":"inet","address":"172.18.10.2","prefix_len":24}]}
                  ]},
                  "vlan_status": {"vlans":[
                    {"vlan_id":8,"name":"VLAN8","status":"active","ports":["Cpu","Et2"]}
                  ]},
                  "trunk_status": {"ports":[
                    {"name":"Ethernet1","mode":"trunk","access_vlan":99,"trunk_vlans":[111,112]},
                    {"name":"Ethernet2","mode":"access","access_vlan":8,"trunk_vlans":[]}
                  ]},
                  "arp_table": {"entries":[
                    {"address":"172.18.10.1","mac":"0c:ae:21:dd:00:01","interfaces":["Ethernet1"]}
                  ]}
                }
                """);

        final DeviceConfigParser parser = new AristaSwitchConfigParser();
        assertTrue(parser.supports("Arista", "SWITCH"));

        final NeutralDeviceConfig config = parser.parse("arista-01", "Arista", payload);

        assertEquals("ARISTA_vEOS", config.getFormat());
        assertEquals("trunk", config.getInterfaces().get("Ethernet1").getMode());
        assertEquals(List.of(111, 112), config.getInterfaces().get("Ethernet1").getTrunkVlans());
        assertEquals(99, config.getInterfaces().get("Ethernet1").getAccessVlan());
        assertEquals(8, config.getInterfaces().get("Ethernet2").getAccessVlan());

        // ARP 의 interfaces 배열이 단일 문자열로 합쳐졌는지.
        assertEquals("Ethernet1", config.getArpEntries().get(0).getInterfaceName());
        assertEquals("1", config.getMetadata().get("trunkPortCount"));
    }

    /**
     * 벤더 판정과 폴백: 제품명이 비어 있거나 모르는 값이어도 예외 없이 빈 설정을 돌려줘야 합니다.
     */
    @Test
    @DisplayName("모르는 제품/빈 payload 에서도 예외 없이 빈 설정을 돌려준다")
    void gracefulFallback() {
        final DeviceConfigService service = new DeviceConfigService(List.of(
                new CiscoRouterConfigParser(),
                new FrrRouterConfigParser(),
                new AlpineFirewallConfigParser(),
                new OpenVSwitchConfigParser(),
                new LinuxVmConfigParser(),
                new AristaSwitchConfigParser()));

        // Cisco 는 제품명에 Cisco 가 있어야 선택됩니다.
        assertTrue(service.parserFor("Cisco 8000v", "ROUTER").isPresent());
        assertEquals("CISCO_IOS", service.parserFor("Cisco 8000v", "ROUTER").orElseThrow().format());

        // 모르는 제품 → 파서 없음, 그래도 예외 없이 빈 설정.
        final NeutralDeviceConfig unknown = service.parse("x-01", "UnknownVendor", json("{}"));
        assertTrue(unknown.isEmpty());
        assertFalse(unknown.getWarnings().isEmpty());

        // payload 가 null 이어도 크래시하지 않습니다.
        final NeutralDeviceConfig nullPayload = service.parse("x-02", "FRR", null);
        assertTrue(nullPayload.isEmpty());

        assertTrue(service.supportedFormats().contains("CISCO_IOS"));
        assertTrue(service.supportedFormats().contains("OpenvSwitch"));
    }

    /**
     * Cisco 는 {@code show ip interface brief} 에 서브넷 마스크가 없습니다.
     * 파서가 마스크를 추측하면 잘못된 도달성 분석이 나오므로, 없는 채로 두는지 확인합니다.
     */
    @Test
    @DisplayName("Cisco: 서브넷 마스크가 없으면 prefix_len 을 만들지 않는다")
    void ciscoDoesNotInventPrefixLength() {
        final JsonNode payload = json("""
                {
                  "agent": "c8000v",
                  "nic_status": {"interfaces":[
                    {"name":"GigabitEthernet1","state":"up",
                     "addresses":[{"family":"inet","address":"192.168.122.254"}]},
                    {"name":"GigabitEthernet3","state":"down","addresses":[]}
                  ]},
                  "route_status": {"routes":[
                    {"prefix":"0.0.0.0/0","next_hop":"192.168.122.1","protocol":"static"}
                  ]}
                }
                """);

        final DeviceConfigParser parser = new CiscoRouterConfigParser();
        final NeutralDeviceConfig config = parser.parse("c8000v", "Cisco 8000v", payload);

        // 마스크가 없으므로 주소는 그대로 남고 "/24" 같은 값이 붙지 않아야 합니다.
        assertEquals(List.of("192.168.122.254"),
                config.getInterfaces().get("GigabitEthernet1").getAddresses());
        assertEquals(0, config.getInterfaces().get("GigabitEthernet3").getAddresses().size());

        // Cisco 는 IOS CLI 라 switchport VLAN 을 수집하지 못함을 경고로 남겨야 합니다.
        assertTrue(config.getWarnings().stream().anyMatch(w -> w.contains("VLAN")));
        assertEquals("CISCO_IOS", config.getFormat());
    }
}
