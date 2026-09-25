package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * ANTLR 파서 파이프라인 검증입니다.
 *
 * <p>입력 샘플은 C++ Prober 의 {@code cli_output_parser_test.cpp} 에 있는 실제
 * 장비 출력을 그대로 옮긴 것입니다. Java 쪽 구현이 같은 결과를 내는지,
 * 그리고 순회가 <b>ANTLR 자동 생성 Visitor</b> 로 이뤄지는지 확인합니다.
 */
class CliOutputParserTest {

    private final CliOutputParser parser = new CliOutputParser();

    /* ==================== ip a (NIC 상태) ==================== */

    private static final String IP_ADDR = """
            1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN qlen 1000
                link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
                inet 127.0.0.1/8 scope host lo
                   valid_lft forever preferred_lft forever
            2: eth1.131@eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP qlen 1000
                link/ether 02:42:7c:24:78:01 brd ff:ff:ff:ff:ff:ff
                inet 10.10.131.1/24 scope global eth1.131
                   valid_lft forever preferred_lft forever
                inet6 fe80::42:7cff:fe24:7801/64 scope link
            15: eth4: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UNKNOWN qlen 1000
                link/ether 02:42:7c:24:78:04 brd ff:ff:ff:ff:ff:ff
                inet 172.16.255.2/24 scope global eth4
            """;

    @Test
    @DisplayName("ip a: 인터페이스/주소/부모를 뽑아낸다")
    void nicStatus() {
        final ObjectNode body = parser.parseNicStatus(IP_ADDR);

        assertTrue(body.path("parsed").asBoolean(), () -> "파싱 실패: " + body);
        final JsonNode interfaces = body.path("interfaces");
        assertEquals(3, interfaces.size(), () -> "인터페이스 개수: " + body);

        final JsonNode lo = interfaces.get(0);
        assertEquals("lo", lo.path("name").asString());
        assertEquals("65536", lo.path("mtu").asString());
        assertEquals("UNKNOWN", lo.path("state").asString());

        final JsonNode vlan = interfaces.get(1);
        assertEquals("eth1.131", vlan.path("name").asString());
        assertEquals("eth1", vlan.path("parent").asString(),
                "서브인터페이스 부모 분리");
        assertEquals("02:42:7c:24:78:01", vlan.path("mac").asString());
        assertEquals(4, vlan.path("flags").size(), "BROADCAST/MULTICAST/UP/LOWER_UP");

        final JsonNode addresses = vlan.path("addresses");
        assertEquals(2, addresses.size(), "inet + inet6");
        assertEquals("inet", addresses.get(0).path("family").asString());
        assertEquals("10.10.131.1", addresses.get(0).path("address").asString());
        assertEquals(24, addresses.get(0).path("prefix_len").asInt());
        assertEquals("global", addresses.get(0).path("scope").asString());
        assertEquals("forever", addresses.get(0).path("valid_lft").asString());
        assertEquals("inet6", addresses.get(1).path("family").asString());
        assertEquals("fe80::42:7cff:fe24:7801", addresses.get(1).path("address").asString());

        assertEquals("eth4", interfaces.get(2).path("name").asString());
        assertEquals("172.16.255.2",
                interfaces.get(2).path("addresses").get(0).path("address").asString());
    }

    /* ==================== ovs-vsctl show ==================== */

    private static final String OVS_SHOW = """
            3945208f-1a6d-418c-8909-ff1701a5b643
                Bridge br0
                    Port br0
                        Interface br0
                            type: internal
                    Port eth1
                        tag: 141
                        Interface eth1
                    Port eth0
                        trunks: [141]
                        Interface eth0
            """;

    @Test
    @DisplayName("ovs-vsctl show:들여쓰기로 브리지/포트/인터페이스 계층을 만든다")
    void ovsShow() {
        final ObjectNode body = parser.parseOvsTopology(OVS_SHOW);

        assertTrue(body.path("parsed").asBoolean(), () -> "파싱 실패: " + body);
        final JsonNode bridges = body.path("bridges");
        assertEquals(1, bridges.size(), () -> "브리지 개수: " + body);

        final JsonNode br0 = bridges.get(0);
        assertEquals("br0", br0.path("name").asString());

        final JsonNode ports = br0.path("ports");
        assertEquals(3, ports.size(), () -> "포트 개수: " + ports);

        assertEquals("br0", ports.get(0).path("name").asString());
        assertEquals("internal",
                ports.get(0).path("interfaces").get(0).path("type").asString());

        assertEquals("eth1", ports.get(1).path("name").asString());
        assertEquals(141, ports.get(1).path("tag").asInt(), "access tag");

        assertEquals("eth0", ports.get(2).path("name").asString());
        assertEquals(1, ports.get(2).path("trunks").size(), "trunk 목록");
        assertEquals(141, ports.get(2).path("trunks").get(0).asInt());
    }

    /* ==================== ovs-vsctl list port ==================== */

    private static final String OVS_LIST = """
            name                : br0
            tag                 : []
            trunks              : []
            vlan_mode           : []
            --
            name                : eth1
            tag                 : 141
            trunks              : []
            vlan_mode           : []
            --
            name                : eth0
            tag                 : []
            trunks              : [141]
            vlan_mode           : []
            """;

    @Test
    @DisplayName("ovs-vsctl list port: -- 구분자마다 레코드를 새로 연다")
    void ovsList() {
        final ObjectNode body = parser.parseOvsTopology(OVS_LIST);

        assertTrue(body.path("parsed").asBoolean(), () -> "파싱 실패: " + body);
        final JsonNode ports = body.path("ports");
        assertEquals(3, ports.size(), () -> "레코드 개수: " + body);

        assertEquals("br0", ports.get(0).path("name").asString());
        assertEquals(0, ports.get(0).path("tag").size(), "빈 리스트는 []");

        assertEquals("eth1", ports.get(1).path("name").asString());
        assertEquals(141, ports.get(1).path("tag").asInt());

        assertEquals("eth0", ports.get(2).path("name").asString());
        assertEquals(141, ports.get(2).path("trunks").get(0).asInt());
    }

    /* ==================== show ip route (FRR) ==================== */

    private static final String FRR_ROUTE = """
            Codes: K - kernel route, C - connected, S - static, R - RIP,
                   O - OSPF, I - IS-IS, B - BGP, E - EIGRP, N - NHRP,

            O>* 0.0.0.0/0 [110/1] via 10.99.10.1, eth0, weight 1, 00:17:20
            S>* 10.10.128.0/21 [1/0] via 10.99.143.2, eth1, weight 1, 00:18:17
            O   10.99.10.0/24 [110/100] is directly connected, eth0, weight 1, 00:17:35
            C>* 10.99.10.0/24 is directly connected, eth0, 00:18:18
            C>* 172.16.255.0/24 is directly connected, eth7, 00:18:18
            """;

    @Test
    @DisplayName("show ip route (FRR): 라우트 코드/목적지/게이트웨이를 분리한다")
    void frrRoute() {
        final ObjectNode body = parser.parseRouteStatus(FRR_ROUTE, CliVendor.FRR);

        assertTrue(body.path("parsed").asBoolean(), () -> "파싱 실패: " + body);
        final JsonNode routes = body.path("routes");
        assertEquals(5, routes.size(), () -> "라우트 개수: " + body);

        final JsonNode first = routes.get(0);
        assertEquals("0.0.0.0/0", first.path("prefix").asString());
        assertEquals("10.99.10.1", first.path("next_hop").asString());
        assertEquals("eth0", first.path("interface_name").asString());
        assertEquals("ospf", first.path("protocol").asString(), "O → ospf");
        assertTrue(first.path("selected").asBoolean(), ">* 는 selected");
        assertTrue(first.path("is_default").asBoolean(), "0.0.0.0/0 은 기본 경로");

        assertEquals("static", routes.get(1).path("protocol").asString(), "S → static");
        assertEquals("10.99.143.2", routes.get(1).path("next_hop").asString(),
                "S>* 게이트웨이");

        final JsonNode connected = routes.get(3);
        assertEquals("connected", connected.path("protocol").asString(), "C → connected");
        assertEquals("10.99.10.0/24", connected.path("prefix").asString());

        final JsonNode protocols = body.path("protocols");
        assertTrue(protocols.size() >= 3, () -> "프로토콜 종류: " + protocols);
    }

    /* ==================== show ip route (커널, 라우트 코드 없음) ==================== */

    private static final String KERNEL_ROUTE = """
            default via 10.99.10.1 dev eth0 proto static
            10.10.128.0/21 via 10.99.143.2 dev eth1 proto ospf metric 20
            10.99.10.0/24 dev eth0 proto kernel scope link src 10.99.10.2
            """;

    @Test
    @DisplayName("ip route show (커널): destination/via 키로 읽는다")
    void kernelRoute() {
        final ObjectNode body = parser.parseRouteStatus(KERNEL_ROUTE, CliVendor.FRR);

        assertTrue(body.path("parsed").asBoolean(), () -> "파싱 실패: " + body);
        final JsonNode routes = body.path("routes");
        assertEquals(3, routes.size(), () -> "라우트 개수: " + body);

        assertEquals("0.0.0.0/0", routes.get(0).path("prefix").asString(),
                "default → 0.0.0.0/0");
        assertEquals("10.99.10.1", routes.get(0).path("next_hop").asString());
        assertEquals("static", routes.get(0).path("protocol").asString());

        assertEquals("10.10.128.0/21", routes.get(1).path("prefix").asString());
        assertEquals(20, routes.get(1).path("metric").asInt());
        assertEquals("ospf", routes.get(1).path("protocol").asString());

        assertEquals("kernel", routes.get(2).path("protocol").asString());
    }

    @Test
    @DisplayName("ip route show: 주소 아닌 텍스트는 라우트로 삼키지 않는다")
    void kernelRouteIgnoresNonRouteNoise() {
        // 예전 문법은 routeHead 에 IFNAME 을 허용해 아무 단어나 목적지로 받았다.
        // 그래서 `hello world` 가 라우트 2건으로 파싱됐고(parsed:true), 소비자는
        // "조회했는데 경로 없음" 대신 "경로 2건" 으로 읽었다. 목적지 자리를
        // 커널이 실제로 내는 토큰으로 좁혀 이 오탐을 문법 수준에서 제거했다.
        final ObjectNode body = parser.parseRouteStatus(
                "hello world\nthis is not a route\n", CliVendor.FRR);

        assertEquals(0, body.path("route_count").asInt(), () -> "잡음이 라우트로 파싱됨: " + body);
        assertFalse(body.path("parsed").asBoolean()
                && body.path("route_count").asInt() > 0,
                () -> "잡음이 라우트로 파싱됨: " + body);
    }

    /* ==================== show ip interface brief (Cisco) ==================== */

    private static final String CISCO_BRIEF = """
            Interface              IP-Address      OK? Method Status                Protocol
            GigabitEthernet1       192.168.122.254 YES NVRAM  up                    up
            GigabitEthernet2       unassigned      YES NVRAM  administratively down down
            GigabitEthernet3       unassigned      YES NVRAM  down                  down
            """;

    @Test
    @DisplayName("show ip interface brief: unassigned/status/protocol 을 나눈다")
    void interfaceBrief() {
        final ObjectNode body = parser.parseInterfaceStatus(CISCO_BRIEF, CliVendor.CISCO);

        assertTrue(body.path("parsed").asBoolean(), () -> "파싱 실패: " + body);
        final JsonNode interfaces = body.path("interfaces");
        assertEquals(3, interfaces.size(), () -> "인터페이스 개수: " + body);

        final JsonNode first = interfaces.get(0);
        assertEquals("GigabitEthernet1", first.path("name").asString());
        assertEquals("192.168.122.254", first.path("ip_address").asString());
        assertEquals("up", first.path("status").asString());
        assertEquals("up", first.path("protocol").asString());

        assertTrue(interfaces.get(1).path("unassigned").asBoolean(), "unassigned 플래그");
    }

    /* ==================== nft list ruleset ==================== */

    private static final String NFT_RULESET = """
            table ip filter {
            \tchain input {
            \t\ttype filter hook input priority filter; policy accept;
            \t}

            \tchain forward {
            \t\ttype filter hook forward priority filter; policy accept;
            \t}
            }
            table ip nat {
            \tchain postrouting {
            \t\ttype nat hook postrouting priority srcnat; policy accept;
            \t\toifname "eth0" masquerade
            \t}
            }
            """;

    @Test
    @DisplayName("nft list ruleset: 테이블/체인/규칙 3계층을 만든다")
    void nftRuleset() {
        final ObjectNode body = parser.parseFirewallRules(NFT_RULESET);

        assertTrue(body.path("parsed").asBoolean(), () -> "파싱 실패: " + body);
        final JsonNode tables = body.path("tables");
        assertEquals(2, tables.size(), () -> "테이블 개수: " + body);

        final JsonNode filter = tables.get(0);
        assertEquals("ip", filter.path("family").asString());
        assertEquals("filter", filter.path("name").asString());
        assertEquals(2, filter.path("chains").size(), "체인 개수");

        final JsonNode input = filter.path("chains").get(0);
        assertEquals("input", input.path("name").asString());
        assertEquals("filter", input.path("type").asString());
        assertEquals("input", input.path("hook").asString());
        assertEquals("accept", input.path("policy").asString());

        final JsonNode nat = tables.get(1);
        assertEquals("nat", nat.path("name").asString());
        final JsonNode postrouting = nat.path("chains").get(0);
        assertEquals("postrouting", postrouting.path("name").asString());
        assertEquals("nat", postrouting.path("type").asString());
        assertEquals("srcnat", postrouting.path("priority").asString());
        assertEquals(1, postrouting.path("rules").size(), "규칙 개수");

        assertEquals(2, body.path("table_count").asInt());
        assertEquals(3, body.path("chain_count").asInt());
        assertEquals(1, body.path("rule_count").asInt());
    }

    /* ==================== show vlan brief (Arista) ==================== */

    private static final String ARISTA_VLAN = """
            VLAN  Name                             Status    Ports
            ----- -------------------------------- --------- -------------------------------
            1     default                          active    Et4, Et5, Et6, Et7, Et8, Et9
                                                             Et10, Et11, Et12
            8     VLAN8                            active    Cpu, Et2
            99    TRANSIT                          active    \s
            """;

    @Test
    @DisplayName("show vlan brief: 헤더/구분선을 버리고 이어지는 포트 줄을 병합한다")
    void aristaVlan() {
        final ObjectNode body = parser.parseSwitchVlan(ARISTA_VLAN);

        assertTrue(body.path("parsed").asBoolean(), () -> "파싱 실패: " + body);
        final JsonNode vlans = body.path("vlans");
        assertEquals(3, vlans.size(), () -> "VLAN 개수: " + body);

        final JsonNode first = vlans.get(0);
        assertEquals(1, first.path("vlan_id").asInt());
        assertEquals("default", first.path("name").asString());
        assertEquals("active", first.path("status").asString());
        assertEquals(9, first.path("ports").size(),
                () -> "이어지는 줄 병합(Et10~Et12): " + first);

        assertEquals(8, vlans.get(1).path("vlan_id").asInt());
        assertEquals("VLAN8", vlans.get(1).path("name").asString());
        assertEquals(2, vlans.get(1).path("ports").size(), "Cpu, Et2");

        assertEquals(99, vlans.get(2).path("vlan_id").asInt());
        assertEquals("TRANSIT", vlans.get(2).path("name").asString());
        assertEquals(0, vlans.get(2).path("ports").size(), "포트 없음");
    }

    /* ==================== ARP / 이웃 테이블 ==================== */

    @Test
    @DisplayName("ip neigh show: lladdr/dev/state 를 뽑는다")
    void arpLinux() {
        final ObjectNode body = parser.parseArpTable(
                "10.0.9.1 dev ens3 lladdr 0c:2d:07:65:99:f3 REACHABLE\n"
                        + "10.0.9.100 dev ens3 lladdr 0c:ae:dc:fd:00:00 STALE\n",
                CliVendor.LINUX);

        assertTrue(body.path("parsed").asBoolean(), () -> "파싱 실패: " + body);
        final JsonNode entries = body.path("entries");
        assertEquals(2, entries.size(), () -> "ARP 항목: " + body);

        assertEquals("10.0.9.1", entries.get(0).path("address").asString());
        assertEquals("0c:2d:07:65:99:f3", entries.get(0).path("mac").asString());
        assertEquals("REACHABLE", entries.get(0).path("state").asString());
        assertEquals("STALE", entries.get(1).path("state").asString());
    }

    @Test
    @DisplayName("show ip arp: 헤더 줄은 데이터로 오해하지 않는다")
    void arpArista() {
        final ObjectNode body = parser.parseArpTable("""
                Address         Age (sec)  Hardware Addr   Interface
                172.18.10.1       2:31:51  0cae.21dd.0001  Ethernet1
                10.0.8.100        2:25:33  0c87.2f1f.0000  Vlan8, Ethernet2
                """, CliVendor.ARISTA);

        assertTrue(body.path("parsed").asBoolean(), () -> "파싱 실패: " + body);
        final JsonNode entries = body.path("entries");
        assertEquals(2, entries.size(), () -> "헤더 제외 항목: " + body);

        assertEquals("172.18.10.1", entries.get(0).path("address").asString());
        assertEquals("0c:ae:21:dd:00:01", entries.get(0).path("mac").asString(),
                "점 표기 MAC → 콜론 표기");
        assertEquals("Ethernet1", entries.get(0).path("interface").asString());

        // `Vlan8, Ethernet2` — 쉼표를 별도 토큰으로 뜯어 두었으므로 두 항목이다.
        final JsonNode multi = entries.get(1);
        assertEquals(2, multi.path("interfaces").size(), () -> "인터페이스 목록: " + multi);
        assertEquals("Vlan8", multi.path("interfaces").get(0).asString());
        assertEquals("Ethernet2", multi.path("interfaces").get(1).asString());
        assertEquals("Vlan8", multi.path("interface").asString(), "대표는 첫 항목");
    }

    /* ==================== 관용성 계약 ==================== */

    @Test
    @DisplayName("어떤 입력이 와도 예외를 던지지 않고 parsed 플래그를 남긴다")
    void neverThrows() {
        final String garbage = "!@#$%^&*()\u0000\n<<<>>>\n";

        final ObjectNode nic = parser.parseNicStatus(garbage);
        assertTrue(nic.has("parsed"), () -> "parsed 키 없음: " + nic);

        final ObjectNode route = parser.parseRouteStatus(garbage, CliVendor.FRR);
        assertTrue(route.has("parsed"), () -> "parsed 키 없음: " + route);

        final ObjectNode nft = parser.parseFirewallRules(garbage);
        assertTrue(nft.has("parsed"), () -> "parsed 키 없음: " + nft);

        final ObjectNode query = parser.parseQueryOutput(CliVendor.LINUX, "nic", garbage);
        assertTrue(query.has("parsed"), () -> "parsed 키 없음: " + query);
    }

    @Test
    @DisplayName("parseQueryOutput: target 문자열로 문법을 고른다")
    void queryOutputDispatch() {
        assertTrue(parser.parseQueryOutput(CliVendor.FRR, "route", FRR_ROUTE)
                .path("routes").size() >= 5, "route → 라우트");
        assertTrue(parser.parseQueryOutput(CliVendor.OPEN_VSWITCH, "topology", OVS_SHOW)
                .path("bridges").size() == 1, "topology → OVS 브리지");
        assertTrue(parser.parseQueryOutput(CliVendor.NFTABLES, "ruleset", NFT_RULESET)
                .path("tables").size() == 2, "ruleset → nft 테이블");
        assertTrue(parser.parseQueryOutput(CliVendor.ARISTA, "vlan", ARISTA_VLAN)
                .path("vlans").size() == 3, "vlan → VLAN 목록");
    }
}