// =============================================================================
//  cli_output_parser_test — ANTLR 기반 CLI 출력 파서 검증
//
//  docs/Agent_Command.md 의 "조회 명령어" 섹션에 실린 실제 출력 샘플을
//  그대로 넣고, JSON 으로 기대한 필드가 나오는지 확인한다.
//
//  네트워크/장비가 없어도 동작하도록 모든 입력은 문자열 리터럴이다.
//  실패 시 종료 코드가 0이 아니게 되어 ctest 가 실패를 감지한다.
// =============================================================================

#include <iostream>
#include <string>
#include <vector>

#include "parser/cli_output_parser.hpp"

namespace
{

int g_failures = 0;
int g_checks = 0;

void Check(bool condition, const std::string& label)
{
    ++g_checks;
    if (!condition)
    {
        ++g_failures;
        std::cerr << "[FAIL] " << label << '\n';
    }
    else
    {
        std::cout << "[ ok ] " << label << '\n';
    }
}

void CheckEq(const std::string& actual, const std::string& expected, const std::string& label)
{
    Check(actual == expected, label + " (got='" + actual + "', want='" + expected + "')");
}

using cli_parser::Vendor;

// ---------------------------------------------------------------------------
//  ip a  (docs/Agent_Command.md 방화벽 샘플)
// ---------------------------------------------------------------------------
const char* kIpAddrSample =
    "1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN qlen 1000\n"
    "    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00\n"
    "    inet 127.0.0.1/8 scope host lo\n"
    "       valid_lft forever preferred_lft forever\n"
    "2: eth1.131@eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP qlen 1000\n"
    "    link/ether 02:42:7c:24:78:01 brd ff:ff:ff:ff:ff:ff\n"
    "    inet 10.10.131.1/24 scope global eth1.131\n"
    "       valid_lft forever preferred_lft forever\n"
    "    inet6 fe80::42:7cff:fe24:7801/64 scope link\n"
    "11: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UNKNOWN qlen 1000\n"
    "    link/ether 02:42:7c:24:78:00 brd ff:ff:ff:ff:ff:ff\n"
    "    inet 10.99.143.2/24 scope global eth0\n"
    "15: eth4: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UNKNOWN qlen 1000\n"
    "    link/ether 02:42:7c:24:78:04 brd ff:ff:ff:ff:ff:ff\n"
    "    inet 172.16.255.2/24 scope global eth4\n";

void TestIpAddr()
{
    std::cout << "\n--- ip a (NIC 상태) ---\n";
    const auto json = cli_parser::ParseNicStatus(kIpAddrSample);

    Check(json.contains("interfaces"), "interfaces 배열 존재");
    const auto& interfaces = json["interfaces"];
    Check(interfaces.is_array() && interfaces.size() == 4,
          "인터페이스 4개 파싱 (got=" + std::to_string(interfaces.size()) + ")");

    // lo
    CheckEq(interfaces[0]["name"].get<std::string>(), "lo", "첫 인터페이스 이름=lo");
    Check(interfaces[0]["index"].get<int>() == 1, "lo index=1");
    CheckEq(interfaces[0]["mtu"].get<std::string>(), "65536", "lo mtu=65536");
    CheckEq(interfaces[0]["state"].get<std::string>(), "UNKNOWN", "lo state=UNKNOWN");

    // eth1.131@eth1 — 부모 인터페이스 분리
    CheckEq(interfaces[1]["name"].get<std::string>(), "eth1.131", "VLAN 서브인터페이스 이름");
    CheckEq(interfaces[1]["parent"].get<std::string>(), "eth1", "VLAN 서브인터페이스 parent=eth1");
    CheckEq(interfaces[1]["mac"].get<std::string>(), "02:42:7c:24:78:01", "MAC 파싱");
    Check(interfaces[1]["flags"].is_array() && interfaces[1]["flags"].size() == 4,
          "플래그 4개(BROADCAST,MULTICAST,UP,LOWER_UP)");

    const auto& addresses = interfaces[1]["addresses"];
    Check(addresses.is_array() && addresses.size() == 2, "주소 2개(inet + inet6)");
    CheckEq(addresses[0]["family"].get<std::string>(), "inet", "주소 family=inet");
    CheckEq(addresses[0]["address"].get<std::string>(), "10.10.131.1", "주소 값");
    Check(addresses[0]["prefix_len"].get<int>() == 24, "prefix_len=24");
    CheckEq(addresses[0]["scope"].get<std::string>(), "global", "scope=global");
    CheckEq(addresses[0]["interface"].get<std::string>(), "eth1.131", "주소 소속 인터페이스");
    CheckEq(addresses[0]["valid_lft"].get<std::string>(), "forever", "valid_lft 전파");

    CheckEq(addresses[1]["family"].get<std::string>(), "inet6", "inet6 family");
    CheckEq(addresses[1]["address"].get<std::string>(), "fe80::42:7cff:fe24:7801",
            "IPv6 축약 주소 파싱");

    // eth4 (172.16.255.2 — 관리망)
    CheckEq(interfaces[3]["name"].get<std::string>(), "eth4", "네번째 인터페이스=eth4");
    CheckEq(interfaces[3]["addresses"][0]["address"].get<std::string>(), "172.16.255.2",
            "eth4 주소 172.16.255.2");

    Check(json["parsed"].get<bool>(), "문법 오류 없음");
}

// ---------------------------------------------------------------------------
//  ovs-vsctl show  (docs/Agent_Command.md OpenvSwitch 샘플)
// ---------------------------------------------------------------------------
const char* kOvsShowSample =
    "3945208f-1a6d-418c-8909-ff1701a5b643\n"
    "    Bridge br0\n"
    "        Port br0\n"
    "            Interface br0\n"
    "                type: internal\n"
    "        Port eth1\n"
    "            tag: 141\n"
    "            Interface eth1\n"
    "        Port eth0\n"
    "            trunks: [141]\n"
    "            Interface eth0\n";

void TestOvsShow()
{
    std::cout << "\n--- ovs-vsctl show (스위치 토폴로지) ---\n";
    const auto json = cli_parser::ParseOvsTopology(kOvsShowSample);

    Check(json.contains("bridges"), "bridges 배열 존재");
    const auto& bridges = json["bridges"];
    Check(bridges.is_array() && bridges.size() == 1, "브리지 1개");

    CheckEq(bridges[0]["name"].get<std::string>(), "br0", "브리지 이름=br0");
    const auto& ports = bridges[0]["ports"];
    Check(ports.is_array() && ports.size() == 3, "포트 3개(br0, eth1, eth0)");

    CheckEq(ports[0]["name"].get<std::string>(), "br0", "포트0=br0");
    CheckEq(ports[0]["interfaces"][0]["type"].get<std::string>(), "internal",
            "br0 인터페이스 type=internal");

    CheckEq(ports[1]["name"].get<std::string>(), "eth1", "포트1=eth1");
    Check(ports[1]["tag"].get<int>() == 141, "eth1 access tag=141");

    CheckEq(ports[2]["name"].get<std::string>(), "eth0", "포트2=eth0");
    Check(ports[2]["trunks"].is_array() && ports[2]["trunks"].size() == 1 &&
              ports[2]["trunks"][0].get<int>() == 141,
          "eth0 trunk=141");

    Check(json["parsed"].get<bool>(), "OVS 문법 오류 없음");
}

// ---------------------------------------------------------------------------
//  ovs-vsctl list port  (docs/Agent_Command.md 샘플)
// ---------------------------------------------------------------------------
const char* kOvsListPortSample =
    "name                : br0\n"
    "tag                 : []\n"
    "trunks              : []\n"
    "vlan_mode           : []\n"
    "--\n"
    "name                : eth1\n"
    "tag                 : 141\n"
    "trunks              : []\n"
    "vlan_mode           : []\n"
    "--\n"
    "name                : eth0\n"
    "tag                 : []\n"
    "trunks              : [141]\n"
    "vlan_mode           : []\n";

void TestOvsListPort()
{
    std::cout << "\n--- ovs-vsctl list port ---\n";
    const auto json = cli_parser::ParseOvsTopology(kOvsListPortSample);

    Check(json.contains("ports"), "ports 배열 존재");
    const auto& ports = json["ports"];
    Check(ports.is_array() && ports.size() == 3, "포트 레코드 3개 (got=" +
                                                      std::to_string(ports.size()) + ")");

    CheckEq(ports[0]["name"].get<std::string>(), "br0", "레코드0 name=br0");
    CheckEq(ports[1]["name"].get<std::string>(), "eth1", "레코드1 name=eth1");
    Check(ports[1]["tag"].get<int>() == 141, "레코드1 tag=141");
    CheckEq(ports[2]["name"].get<std::string>(), "eth0", "레코드2 name=eth0");
    Check(ports[2]["trunks"].size() == 1 && ports[2]["trunks"][0].get<int>() == 141,
          "레코드2 trunks=[141]");
}

// ---------------------------------------------------------------------------
//  show ip route  (FRR 샘플 — docs/Agent_Command.md)
// ---------------------------------------------------------------------------
const char* kFrrRouteSample =
    "Codes: K - kernel route, C - connected, S - static, R - RIP,\n"
    "       O - OSPF, I - IS-IS, B - BGP, E - EIGRP, N - NHRP,\n"
    "\n"
    "O>* 0.0.0.0/0 [110/1] via 10.99.10.1, eth0, weight 1, 00:17:20\n"
    "S>* 10.10.128.0/21 [1/0] via 10.99.143.2, eth1, weight 1, 00:18:17\n"
    "O>* 10.20.111.0/24 [110/200] via 10.99.10.5, eth0, weight 1, 00:17:25\n"
    "O   10.99.10.0/24 [110/100] is directly connected, eth0, weight 1, 00:17:35\n"
    "C>* 10.99.10.0/24 is directly connected, eth0, 00:18:18\n"
    "C>* 172.16.255.0/24 is directly connected, eth7, 00:18:18\n";

void TestFrrRoute()
{
    std::cout << "\n--- show ip route (FRR) ---\n";
    const auto json = cli_parser::ParseRouteStatus(kFrrRouteSample, Vendor::kFrr);

    Check(json.contains("routes"), "routes 배열 존재");
    const auto& routes = json["routes"];
    Check(routes.is_array() && routes.size() == 6, "라우트 6개 (got=" +
                                                       std::to_string(routes.size()) + ")");

    // O>* 0.0.0.0/0
    CheckEq(routes[0]["protocol"].get<std::string>(), "ospf", "라우트0 protocol=ospf");
    CheckEq(routes[0]["prefix"].get<std::string>(), "0.0.0.0/0", "라우트0 prefix");
    CheckEq(routes[0]["metric"].get<std::string>(), "110/1", "라우트0 metric=110/1");
    CheckEq(routes[0]["next_hop"].get<std::string>(), "10.99.10.1", "라우트0 next_hop");
    CheckEq(routes[0]["interface_name"].get<std::string>(), "eth0", "라우트0 인터페이스");
    Check(routes[0]["selected"].get<bool>(), "라우트0 selected(*)");
    Check(routes[0]["fib"].get<bool>(), "라우트0 fib(>)");

    // S>* 정적 경로
    CheckEq(routes[1]["protocol"].get<std::string>(), "static", "라우트1 protocol=static");
    CheckEq(routes[1]["prefix"].get<std::string>(), "10.10.128.0/21", "라우트1 prefix(C4I 집계)");
    CheckEq(routes[1]["metric"].get<std::string>(), "1/0", "라우트1 metric=1/0");

    // O directly connected
    CheckEq(routes[3]["protocol"].get<std::string>(), "ospf", "라우트3 protocol=ospf");
    Check(routes[3]["connected"].get<bool>(), "라우트3 directly connected");
    CheckEq(routes[3]["interface_name"].get<std::string>(), "eth0", "라우트3 인터페이스");

    Check(json["protocols"].is_array(), "protocols 목록 존재");
    Check(json["route_count"].get<std::size_t>() == 6, "route_count=6");
    Check(json["parsed"].get<bool>(), "FRR 라우팅 문법 오류 없음");
}

// ---------------------------------------------------------------------------
//  show ip route  (Cisco 샘플 — docs/Agent_Command.md)
// ---------------------------------------------------------------------------
const char* kCiscoRouteSample =
    "Codes: L - local, C - connected, S - static, R - RIP, M - mobile, B - BGP\n"
    "       D - EIGRP, EX - EIGRP external, O - OSPF, IA - OSPF inter area\n"
    "\n"
    "Gateway of last resort is 192.168.122.1 to network 0.0.0.0\n"
    "\n"
    "S*    0.0.0.0/0 [1/0] via 192.168.122.1\n"
    "      10.0.0.0/8 is variably subnetted, 2 subnets, 2 masks\n"
    "C        10.20.0.0/24 is directly connected, GigabitEthernet4\n"
    "L        10.20.0.1/32 is directly connected, GigabitEthernet4\n"
    "C        172.128.0.0/24 is directly connected, GigabitEthernet2\n";

void TestCiscoRoute()
{
    std::cout << "\n--- show ip route (Cisco 8000v) ---\n";
    const auto json = cli_parser::ParseRouteStatus(kCiscoRouteSample, Vendor::kCisco);

    Check(json.contains("routes"), "routes 배열 존재");
    const auto& routes = json["routes"];
    Check(routes.is_array() && routes.size() == 4, "라우트 4개 (got=" +
                                                       std::to_string(routes.size()) + ")");

    CheckEq(routes[0]["protocol"].get<std::string>(), "static", "라우트0 static");
    CheckEq(routes[0]["prefix"].get<std::string>(), "0.0.0.0/0", "라우트0 기본 경로");
    CheckEq(routes[0]["next_hop"].get<std::string>(), "192.168.122.1", "라우트0 next_hop");

    CheckEq(routes[1]["protocol"].get<std::string>(), "connected", "라우트1 connected");
    CheckEq(routes[1]["interface_name"].get<std::string>(), "GigabitEthernet4",
            "라우트1 인터페이스명");
    Check(routes[1]["connected"].get<bool>(), "라우트1 directly connected");

    CheckEq(routes[2]["protocol"].get<std::string>(), "local", "라우트2 local(/32)");

    CheckEq(json["gateway_of_last_resort"].get<std::string>(), "192.168.122.1",
            "Gateway of last resort 파싱");
    Check(json["parsed"].get<bool>(), "Cisco 라우팅 문법 오류 없음");
}

// ---------------------------------------------------------------------------
//  show ip interface brief  (Cisco 샘플)
// ---------------------------------------------------------------------------
const char* kCiscoIfaceBriefSample =
    "Interface              IP-Address      OK? Method Status                Protocol\n"
    "GigabitEthernet1       192.168.122.254 YES NVRAM  up                    up      \n"
    "GigabitEthernet2       172.128.0.1     YES NVRAM  up                    up      \n"
    "GigabitEthernet3       unassigned      YES NVRAM  down                  down    \n"
    "GigabitEthernet4       10.20.0.1       YES NVRAM  up                    up      \n"
    "VirtualPortGroup0      192.168.35.1    YES NVRAM  up                    up      \n";

void TestIfaceBrief()
{
    std::cout << "\n--- show ip interface brief (Cisco) ---\n";
    const auto json = cli_parser::ParseInterfaceStatus(kCiscoIfaceBriefSample, Vendor::kCisco);

    Check(json.contains("interfaces"), "interfaces 배열 존재");
    const auto& interfaces = json["interfaces"];
    Check(interfaces.is_array() && interfaces.size() == 5, "인터페이스 5개 (got=" +
                                                               std::to_string(interfaces.size()) + ")");

    CheckEq(interfaces[0]["name"].get<std::string>(), "GigabitEthernet1", "iface0 이름");
    CheckEq(interfaces[0]["ip_address"].get<std::string>(), "192.168.122.254", "iface0 IP");
    CheckEq(interfaces[0]["status"].get<std::string>(), "up", "iface0 status=up");
    CheckEq(interfaces[0]["protocol"].get<std::string>(), "up", "iface0 protocol=up");

    // unassigned 인 경우
    Check(interfaces[2]["unassigned"].get<bool>(), "iface2 unassigned 플래그");
    CheckEq(interfaces[2]["status"].get<std::string>(), "down", "iface2 status=down");

    Check(json["parsed"].get<bool>(), "interface brief 문법 오류 없음");
}

// ---------------------------------------------------------------------------
//  nft list ruleset  (docs/Agent_Command.md 방화벽 샘플)
// ---------------------------------------------------------------------------
const char* kNftSample =
    "table ip filter {\n"
    "\tchain input {\n"
    "\t\ttype filter hook input priority filter; policy accept;\n"
    "\t}\n"
    "\n"
    "\tchain forward {\n"
    "\t\ttype filter hook forward priority filter; policy accept;\n"
    "\t}\n"
    "\n"
    "\tchain output {\n"
    "\t\ttype filter hook output priority filter; policy accept;\n"
    "\t}\n"
    "}\n"
    "table ip nat {\n"
    "\tchain postrouting {\n"
    "\t\ttype nat hook postrouting priority srcnat; policy accept;\n"
    "\t\toifname \"eth0\" masquerade\n"
    "\t}\n"
    "}\n";

void TestNftRuleset()
{
    std::cout << "\n--- nft list ruleset ---\n";
    const auto json = cli_parser::ParseFirewallRules(kNftSample);

    Check(json.contains("tables"), "tables 배열 존재");
    const auto& tables = json["tables"];
    Check(tables.is_array() && tables.size() == 2, "테이블 2개(filter, nat) (got=" +
                                                       std::to_string(tables.size()) + ")");

    CheckEq(tables[0]["family"].get<std::string>(), "ip", "테이블0 family=ip");
    CheckEq(tables[0]["name"].get<std::string>(), "filter", "테이블0 name=filter");
    Check(tables[0]["chains"].size() == 3, "테이블0 체인 3개(input/forward/output)");

    const auto& input = tables[0]["chains"][0];
    CheckEq(input["name"].get<std::string>(), "input", "체인0 name=input");
    CheckEq(input["type"].get<std::string>(), "filter", "체인0 type=filter");
    CheckEq(input["hook"].get<std::string>(), "input", "체인0 hook=input");
    CheckEq(input["policy"].get<std::string>(), "accept", "체인0 policy=accept");

    CheckEq(tables[1]["name"].get<std::string>(), "nat", "테이블1 name=nat");
    const auto& postrouting = tables[1]["chains"][0];
    CheckEq(postrouting["name"].get<std::string>(), "postrouting", "nat 체인=postrouting");
    CheckEq(postrouting["type"].get<std::string>(), "nat", "nat 체인 type=nat");
    CheckEq(postrouting["priority"].get<std::string>(), "srcnat", "nat 체인 priority=srcnat");

    Check(postrouting["rules"].size() == 1, "nat 체인 규칙 1개");
    const auto& rule = postrouting["rules"][0];
    CheckEq(rule["action"].get<std::string>(), "masquerade", "규칙 action=masquerade");
    CheckEq(rule["match_pairs"]["oifname"].get<std::string>(), "eth0", "규칙 oifname=eth0");

    Check(json["table_count"].get<std::size_t>() == 2, "table_count=2");
    Check(json["chain_count"].get<std::size_t>() == 4, "chain_count=4");
    Check(json["rule_count"].get<std::size_t>() == 1, "rule_count=1");
    Check(json["parsed"].get<bool>(), "nftables 문법 오류 없음");
}

// ---------------------------------------------------------------------------
//  nft 규칙 차단 (ACL) 샘플 — 문서의 정책 예시가 만든 결과
// ---------------------------------------------------------------------------
const char* kNftDropSample =
    "table inet filter {\n"
    "\tchain forward {\n"
    "\t\ttype filter hook forward priority filter; policy drop;\n"
    "\t\tip saddr 192.168.1.0/24 ip daddr 192.168.2.0/24 counter packets 5 bytes 300 "
    "log prefix \"NFT_DROP: \" drop # handle 5\n"
    "\t}\n"
    "}\n";

void TestNftDropRule()
{
    std::cout << "\n--- nft 규칙 (서브넷 간 차단) ---\n";
    const auto json = cli_parser::ParseFirewallRules(kNftDropSample);

    Check(json["table_count"].get<std::size_t>() == 1, "테이블 1개");
    const auto& chain = json["tables"][0]["chains"][0];
    CheckEq(chain["policy"].get<std::string>(), "drop", "forward policy=drop");
    Check(chain["rules"].size() == 1, "규칙 1개");

    const auto& rule = chain["rules"][0];
    CheckEq(rule["action"].get<std::string>(), "drop", "action=drop");
    CheckEq(rule["match_pairs"]["ip saddr"].get<std::string>(), "192.168.1.0/24",
            "source subnet 파싱");
    CheckEq(rule["match_pairs"]["ip daddr"].get<std::string>(), "192.168.2.0/24",
            "destination subnet 파싱");
    Check(rule["counter"].get<bool>(), "counter 플래그");
    CheckEq(rule["log_prefix"].get<std::string>(), "NFT_DROP: ", "log prefix 파싱");
    Check(rule["handle"].get<long long>() == 5, "handle=5 (삭제용)");
    Check(rule["packets"].get<long long>() == 5, "packets=5");
    Check(rule["bytes"].get<long long>() == 300, "bytes=300");
}

// ---------------------------------------------------------------------------
//  show vlan brief (Arista 샘플)
// ---------------------------------------------------------------------------
const char* kAristaVlanSample =
    "VLAN  Name                             Status    Ports\n"
    "----- -------------------------------- --------- -------------------------------\n"
    "1     default                          active    Et4, Et5, Et6, Et7, Et8, Et9\n"
    "                                                 Et10, Et11, Et12\n"
    "8     VLAN8                            active    Cpu, Et2\n"
    "9     VLAN9                            active    Cpu, Et3\n"
    "99    TRANSIT                          active    \n";

void TestAristaVlan()
{
    std::cout << "\n--- show vlan brief (Arista vEOS) ---\n";
    const auto json = cli_parser::ParseSwitchVlan(kAristaVlanSample);

    Check(json.contains("vlans"), "vlans 배열 존재");
    const auto& vlans = json["vlans"];
    // 샘플에는 VLAN 1, 8, 9, 99 네 줄만 있다(헤더/구분선 제외).
    Check(vlans.is_array() && vlans.size() == 4, "VLAN 4개 (got=" +
                                                     std::to_string(vlans.size()) + ")");

    Check(vlans[0]["vlan_id"].get<int>() == 1, "VLAN0 id=1");
    CheckEq(vlans[0]["name"].get<std::string>(), "default", "VLAN0 name=default");
    CheckEq(vlans[0]["status"].get<std::string>(), "active", "VLAN0 status=active");
    Check(vlans[0]["ports"].size() == 9, "VLAN0 포트 9개(이어지는 줄 병합) (got=" +
                                             std::to_string(vlans[0]["ports"].size()) + ")");

    Check(vlans[1]["vlan_id"].get<int>() == 8, "VLAN1 id=8");
    CheckEq(vlans[1]["name"].get<std::string>(), "VLAN8", "VLAN1 name=VLAN8");
    Check(vlans[1]["ports"].size() == 2, "VLAN1 포트 2개(Cpu, Et2)");
    CheckEq(vlans[1]["ports"][0].get<std::string>(), "Cpu", "VLAN1 포트0=Cpu");
    CheckEq(vlans[1]["ports"][1].get<std::string>(), "Et2", "VLAN1 포트1=Et2");

    Check(vlans[2]["vlan_id"].get<int>() == 9, "VLAN2 id=9");
    CheckEq(vlans[2]["name"].get<std::string>(), "VLAN9", "VLAN2 name=VLAN9");

    Check(vlans[3]["vlan_id"].get<int>() == 99, "VLAN3 id=99");
    CheckEq(vlans[3]["name"].get<std::string>(), "TRANSIT", "VLAN3 name=TRANSIT");

    Check(json["vlan_count"].get<std::size_t>() == 4, "vlan_count=4");
    Check(json["parsed"].get<bool>(), "vlan 문법 오류 없음");
}

// ---------------------------------------------------------------------------
//  ARP / 이웃 테이블 — 세 벤더 형식
// ---------------------------------------------------------------------------
const char* kArpLinuxSample =
    "10.0.9.1 dev ens3 lladdr 0c:2d:07:65:99:f3 REACHABLE\n"
    "10.0.9.100 dev ens3 lladdr 0c:ae:dc:fd:00:00 STALE\n";

const char* kArpAristaSample =
    "Address         Age (sec)  Hardware Addr   Interface\n"
    "172.18.10.1       2:31:51  0cae.21dd.0001  Ethernet1\n"
    "10.0.8.100        2:25:33  0c87.2f1f.0000  Vlan8, Ethernet2\n"
    "10.0.9.100        1:35:06  0cae.dcfd.0000  Vlan9, Ethernet3\n";

const char* kArpCiscoSample =
    "Protocol  Address    Age (min)  Hardware Addr   Type   Interface\n"
    "Internet  10.20.0.4  -          0c2d.0765.99f3  ARPA   GigabitEthernet4\n"
    "Internet  10.20.0.1  5          0c3f.5d52.0003  ARPA   GigabitEthernet1\n";

void TestArpTables()
{
    std::cout << "\n--- ARP 테이블 (Linux / Arista / Cisco) ---\n";

    // Linux `ip neigh show`
    {
        const auto json = cli_parser::ParseArpTable(kArpLinuxSample, Vendor::kUbuntu);
        const auto& entries = json["entries"];
        Check(entries.is_array() && entries.size() == 2, "Linux ARP 2건 (got=" +
                                                             std::to_string(entries.size()) + ")");
        CheckEq(entries[0]["address"].get<std::string>(), "10.0.9.1", "Linux ARP 주소");
        CheckEq(entries[0]["mac"].get<std::string>(), "0c:2d:07:65:99:f3", "Linux ARP MAC");
        CheckEq(entries[0]["interface"].get<std::string>(), "ens3", "Linux ARP 인터페이스(dev)");
        CheckEq(entries[0]["state"].get<std::string>(), "REACHABLE", "Linux ARP 상태");
        CheckEq(entries[1]["state"].get<std::string>(), "STALE", "Linux ARP STALE 상태");
        Check(json["parsed"].get<bool>(), "Linux ARP 문법 오류 없음");
    }

    // Arista `show arp` — 헤더 제외, 점 표기 MAC 정규화, 쉼표 인터페이스
    {
        const auto json = cli_parser::ParseArpTable(kArpAristaSample, Vendor::kArista);
        const auto& entries = json["entries"];
        Check(entries.is_array() && entries.size() == 3,
              "Arista ARP 3건(헤더 제외) (got=" + std::to_string(entries.size()) + ")");
        CheckEq(entries[0]["address"].get<std::string>(), "172.18.10.1", "Arista ARP 주소");
        CheckEq(entries[0]["mac"].get<std::string>(), "0c:ae:21:dd:00:01",
                "Arista 점 표기 MAC 정규화");
        CheckEq(entries[0]["age"].get<std::string>(), "2:31:51", "Arista ARP age");
        CheckEq(entries[0]["interface"].get<std::string>(), "Ethernet1", "Arista ARP 인터페이스");
        Check(entries[1]["interfaces"].size() == 2, "Arista 복수 인터페이스(Vlan8, Ethernet2)");
        CheckEq(entries[1]["interfaces"][1].get<std::string>(), "Ethernet2",
                "Arista 두번째 인터페이스");
        Check(json["parsed"].get<bool>(), "Arista ARP 문법 오류 없음");
    }

    // Cisco `show ip arp` — Protocol 컬럼 건너뛰기
    {
        const auto json = cli_parser::ParseArpTable(kArpCiscoSample, Vendor::kCisco);
        const auto& entries = json["entries"];
        Check(entries.is_array() && entries.size() == 2,
              "Cisco ARP 2건 (got=" + std::to_string(entries.size()) + ")");
        CheckEq(entries[0]["address"].get<std::string>(), "10.20.0.4",
                "Cisco ARP 주소(Protocol 컬럼 이후)");
        CheckEq(entries[0]["mac"].get<std::string>(), "0c:2d:07:65:99:f3",
                "Cisco 점 표기 MAC 정규화");
        CheckEq(entries[0]["type"].get<std::string>(), "ARPA", "Cisco ARP Type");
        CheckEq(entries[0]["interface"].get<std::string>(), "GigabitEthernet4",
                "Cisco ARP 인터페이스");
        CheckEq(entries[0]["age"].get<std::string>(), "-", "Cisco ARP age=-");
        Check(json["parsed"].get<bool>(), "Cisco ARP 문법 오류 없음");
    }
}

// ---------------------------------------------------------------------------
//  Par브 — 빈 입력 / 이상 입력에서도 크래시하지 않아야 한다
// ---------------------------------------------------------------------------
void TestRobustness()
{
    std::cout << "\n--- 견고성 (빈 입력/이상 입력) ---\n";

    const auto empty = cli_parser::ParseNicStatus("");
    Check(empty.contains("parsed"), "빈 입력에도 parsed 필드 존재");

    const auto garbage = cli_parser::ParseRouteStatus("@@@###\n%%%\n", Vendor::kFrr);
    Check(garbage.contains("parsed"), "이상 입력에도 parsed 필드 존재");

    const auto partial = cli_parser::ParseOvsTopology("    Bridge\n");
    Check(partial.contains("parsed"), "불완전 OVS 입력에도 parsed 필드 존재");

    const auto no_newline = cli_parser::ParseNicStatus("1: lo: <UP> mtu 1500 state UNKNOWN");
    Check(no_newline["parsed"].get<bool>(), "개행 없는 입력도 보정되어 파싱 성공");

    const auto big = cli_parser::ParseFirewallRules("table inet t {\n chain c {\n");
    Check(big.contains("parse_error") || big.contains("parsed"),
          "중괄호 불일치 입력도 결과 반환");
}

}  // namespace

int main()
{
    std::cout << "=== ANTLR CLI 출력 파서 검증 ===\n";

    TestIpAddr();
    TestOvsShow();
    TestOvsListPort();
    TestFrrRoute();
    TestCiscoRoute();
    TestIfaceBrief();
    TestNftRuleset();
    TestNftDropRule();
    TestAristaVlan();
    TestArpTables();
    TestRobustness();

    std::cout << "\n=== 결과: " << (g_checks - g_failures) << "/" << g_checks
              << " 통과, 실패 " << g_failures << " ===\n";
    return g_failures == 0 ? 0 : 1;
}
