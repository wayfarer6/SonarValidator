// =============================================================================
//  command_collector_test — 수집기(collector) 검증
//
//  실제 장비/네트워크가 필요 없다. BuildStateFromOutputs() 에
//  docs/Agent_Command.md 의 실제 조회 출력 샘플을 그대로 넣고
//  수집 결과(CollectedState)가 기대대로 만들어지는지 확인한다.
//
//  주의: 표준 assert() 를 쓰지 않는다. 기본 빌드가 Release(-DNDEBUG) 라서
//  assert 는 통째로 제거되고 테스트가 아무것도 검사하지 않게 된다.
//  (repository memory 의 database_service_test / telemetry_store_test 함정)
// =============================================================================

#include <iostream>
#include <map>
#include <string>

#include "module/telemetry_module/command_collector.hpp"

#undef assert
#define assert(condition)                                                        \
    do                                                                           \
    {                                                                            \
        ++g_checks;                                                              \
        if (!(condition))                                                        \
        {                                                                        \
            std::cerr << "  [FAIL] " << #condition << " at line " << __LINE__    \
                      << '\n';                                                   \
            ++g_failures;                                                        \
        }                                                                        \
    } while (false)

namespace
{

int g_failures = 0;
int g_checks = 0;

using Json = nlohmann::json;

// 배열 키의 항목 수(키가 없거나 배열이 아니면 0).
std::size_t Count(const Json& object, const char* key)
{
    if (!object.is_object())
    {
        return 0;
    }
    const auto iterator = object.find(key);
    if (iterator == object.end() || !iterator->is_array())
    {
        return 0;
    }
    return iterator->size();
}

// ---------------------------------------------------------------------------
//  docs/Agent_Command.md — Linux VM (Ubuntu) 샘플
// ---------------------------------------------------------------------------
const char* kIpAddrSample =
    "1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN group default qlen 1000\n"
    "    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00\n"
    "    inet 127.0.0.1/8 scope host lo\n"
    "       valid_lft forever preferred_lft forever\n"
    "114: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UNKNOWN "
    "group default qlen 1000\n"
    "    link/ether 02:42:2f:7e:d6:00 brd ff:ff:ff:ff:ff:ff\n"
    "    inet 10.40.121.10/24 scope global eth0\n"
    "       valid_lft forever preferred_lft forever\n";

const char* kIpRouteSample =
    "default via 10.40.121.1 dev eth0 proto dhcp src 10.40.121.10 metric 100\n"
    "10.40.121.0/24 dev eth0 proto kernel scope link src 10.40.121.10 metric 100\n";

const char* kIpNeighSample =
    "10.0.9.1 dev ens3 lladdr 0c:2d:07:65:99:f3 REACHABLE\n"
    "10.0.9.100 dev ens3 lladdr 0c:ae:dc:fd:00:00 STALE\n";

// ---------------------------------------------------------------------------
//  docs/Agent_Command.md — Cisco 8000v 샘플
// ---------------------------------------------------------------------------
const char* kCiscoIfaceBriefSample =
    "Interface              IP-Address      OK? Method Status                Protocol\n"
    "GigabitEthernet1       192.168.122.254 YES NVRAM  up                    up      \n"
    "GigabitEthernet2       172.128.0.1     YES NVRAM  up                    up      \n"
    "GigabitEthernet3       unassigned      YES NVRAM  down                  down    \n"
    "GigabitEthernet4       10.20.0.1       YES NVRAM  up                    up      \n";

const char* kCiscoRouteSample =
    "Codes: L - local, C - connected, S - static, R - RIP, M - mobile, B - BGP\n"
    "       D - EIGRP, EX - EIGRP external, O - OSPF, IA - OSPF inter area\n"
    "\n"
    "Gateway of last resort is 192.168.122.1 to network 0.0.0.0\n"
    "\n"
    "S*    0.0.0.0/0 [1/0] via 192.168.122.1\n"
    "      10.0.0.0/8 is variably subnetted, 2 subnets, 2 masks\n"
    "C        10.20.0.0/24 is directly connected, GigabitEthernet4\n"
    "L        10.20.0.1/32 is directly connected, GigabitEthernet4\n";

const char* kCiscoArpSample =
    "Protocol  Address    Age (min)  Hardware Addr   Type   Interface\n"
    "Internet  10.20.0.4  -          0c2d.0765.99f3  ARPA   GigabitEthernet4\n";

// ---------------------------------------------------------------------------
//  Arista vEOS 샘플 (vlan brief / ip interface brief / switchport / arp)
// ---------------------------------------------------------------------------
const char* kAristaVlanSample =
    "VLAN  Name                             Status    Ports\n"
    "----- -------------------------------- --------- -------------------------------\n"
    "1     default                          active    Et4, Et5, Et6, Et7, Et8, Et9\n"
    "                                                 Et10, Et11, Et12\n"
    "8     VLAN8                            active    Cpu, Et2\n"
    "9     VLAN9                            active    Cpu, Et3\n"
    "99    TRANSIT                          active    \n";

const char* kAristaIfaceBriefSample =
    "                                                                        Address\n"
    "Interface       IP Address          Status      Protocol         MTU    Owner  \n"
    "--------------- ------------------- ----------- ------------- --------- -------\n"
    "Ethernet1       172.18.10.2/24      up          up              1500           \n"
    "Management1     10.20.0.4/24        up          up              1500           \n"
    "Vlan8           10.0.8.1/24         up          up              1500           \n"
    "Vlan9           10.0.9.1/24         up          up              1500           \n";

const char* kAristaSwitchportSample =
    "Name: Ethernet1\n"
    "Switchport: Enabled\n"
    "Administrative Mode: trunk\n"
    "Access Mode VLAN: 99 (TRANSIT)\n"
    "Trunking VLANs Enabled: 111,112\n"
    "Name: Ethernet2\n"
    "Switchport: Enabled\n"
    "Administrative Mode: static access\n"
    "Access Mode VLAN: 8 (VLAN8)\n"
    "Trunking VLANs Enabled: ALL\n";

const char* kAristaArpSample =
    "Address         Age (sec)  Hardware Addr   Interface\n"
    "172.18.10.1       2:31:51  0cae.21dd.0001  Ethernet1\n"
    "10.0.8.100        2:25:33  0c87.2f1f.0000  Vlan8, Ethernet2\n";

// ---------------------------------------------------------------------------
//  1) Linux VM
// ---------------------------------------------------------------------------
void TestLinuxVm()
{
    std::cout << "\n--- Linux VM (Ubuntu) ---\n";

    const std::map<std::string, std::string> outputs = {
        {"ip a", kIpAddrSample},
        {"ip -br addr show", ""},
        {"ip route show", kIpRouteSample},
        {"ip neigh show", kIpNeighSample},
    };

    const collector::CollectedState state =
        collector::BuildStateFromOutputs(DeviceType::kVirtualMachine, "Ubuntu", outputs);

    assert(state.any_success);
    assert(Count(state.nic, "interfaces") == 2);
    assert(Count(state.route, "routes") == 2);
    assert(Count(state.arp, "entries") == 2);

    // 서버 스냅샷에 세 항목이 모두 실린다.
    assert(state.snapshot.contains("nic_status"));
    assert(state.snapshot.contains("route_status"));
    assert(state.snapshot.contains("arp_table"));
    assert(Count(state.snapshot["nic_status"], "interfaces") == 2);
    assert(Count(state.snapshot["route_status"], "routes") == 2);
    assert(Count(state.snapshot["arp_table"], "entries") == 2);

    // 수집하지 않은 항목은 null 로 남는다.
    assert(state.vlan.is_null());
    assert(state.trunk.is_null());

    // nic_info 저장에 필요한 필드가 채워졌는지.
    const Json& eth0 = state.nic["interfaces"][1];
    assert(eth0["name"].get<std::string>() == "eth0");
    assert(eth0["mac"].get<std::string>() == "02:42:2f:7e:d6:00");
    assert(eth0["addresses"][0]["address"].get<std::string>() == "10.40.121.10");
    assert(eth0["addresses"][0]["prefix_len"].get<int>() == 24);
}

// ---------------------------------------------------------------------------
//  2) Linux VM — `ip a` 가 실패하면 `ip -br addr show` 로 폴백
// ---------------------------------------------------------------------------
void TestLinuxBriefFallback()
{
    std::cout << "\n--- Linux VM (ip -br 폴백) ---\n";

    const std::string brief =
        "lo               UNKNOWN        127.0.0.1/8 ::1/128 \n"
        "eth0             UP             10.40.121.10/24 fe80::42:2fff:fe7e:d600/64 "
        "02:42:2f:7e:d6:00 \n";

    const std::map<std::string, std::string> outputs = {
        {"ip a", ""},
        {"ip -br addr show", brief},
        {"ip route show", ""},
        {"ip neigh show", kIpNeighSample},
    };

    const collector::CollectedState state =
        collector::BuildStateFromOutputs(DeviceType::kVirtualMachine, "Ubuntu", outputs);

    assert(state.any_success);
    assert(Count(state.nic, "interfaces") == 2);
    assert(Count(state.arp, "entries") == 2);

    const Json& eth0 = state.nic["interfaces"][1];
    assert(eth0["name"].get<std::string>() == "eth0");
    assert(Count(eth0, "addresses") == 2);
    assert(eth0["addresses"][0]["address"].get<std::string>() == "10.40.121.10");
    assert(eth0["addresses"][1]["family"].get<std::string>() == "inet6");
}

// ---------------------------------------------------------------------------
//  3) Cisco 8000v
// ---------------------------------------------------------------------------
void TestCiscoRouter()
{
    std::cout << "\n--- Cisco 8000v ---\n";

    const std::map<std::string, std::string> outputs = {
        {"show ip interface brief", kCiscoIfaceBriefSample},
        {"show ip route", kCiscoRouteSample},
        {"show ip arp", kCiscoArpSample},
    };

    const collector::CollectedState state =
        collector::BuildStateFromOutputs(DeviceType::kRouter, "Cisco 8000v", outputs);

    assert(state.any_success);
    // 샘플에는 실제 경로 3줄(S*, C, L)만 있고 `is variably subnetted` 요약줄은 제외된다.
    assert(Count(state.route, "routes") == 3);
    assert(Count(state.arp, "entries") == 1);

    // `show ip interface brief` 를 nic_info 모양으로 변환했는지.
    assert(Count(state.nic, "interfaces") == 4);
    assert(state.nic["interfaces"][0]["name"].get<std::string>() == "GigabitEthernet1");
    assert(state.nic["interfaces"][0]["state"].get<std::string>() == "up");
    assert(state.nic["interfaces"][0]["addresses"][0]["address"].get<std::string>() ==
           "192.168.122.254");
    // Cisco `show ip interface brief` 에는 서브넷 마스크가 없다.
    // 따라서 prefix_len 은 없거나 null 이어야 한다(잘못된 값을 만들어내지 않는다).
    assert(!state.nic["interfaces"][0]["addresses"][0].contains("prefix_len") ||
           state.nic["interfaces"][0]["addresses"][0]["prefix_len"].is_null());

    // unassigned 인터페이스는 주소가 비어 있다.
    assert(Count(state.nic["interfaces"][2], "addresses") == 0);

    assert(state.snapshot.contains("nic_status"));
    assert(state.snapshot.contains("route_status"));
    assert(state.snapshot.contains("arp_table"));
    assert(state.vlan.is_null());
    assert(state.trunk.is_null());
}

// ---------------------------------------------------------------------------
//  4) Arista vEOS
// ---------------------------------------------------------------------------
void TestAristaSwitch()
{
    std::cout << "\n--- Arista vEOS ---\n";

    const std::map<std::string, std::string> outputs = {
        {"show vlan brief", kAristaVlanSample},
        {"show ip interface brief", kAristaIfaceBriefSample},
        {"show interfaces switchport", kAristaSwitchportSample},
        {"show arp", kAristaArpSample},
    };

    const collector::CollectedState state =
        collector::BuildStateFromOutputs(DeviceType::kSwitch, "Arista", outputs);

    assert(state.any_success);
    assert(Count(state.vlan, "vlans") == 4);
    assert(Count(state.trunk, "ports") == 2);
    assert(Count(state.arp, "entries") == 2);
    assert(Count(state.nic, "interfaces") == 4);

    // VLAN 8 의 포트 이어짐 병합
    assert(state.vlan["vlans"][1]["vlan_id"].get<int>() == 8);
    assert(Count(state.vlan["vlans"][1], "ports") == 2);

    // switchport: trunk/access 모드와 VLAN ID(괄호 앞 숫자만)
    assert(state.trunk["ports"][0]["name"].get<std::string>() == "Ethernet1");
    assert(state.trunk["ports"][0]["mode"].get<std::string>() == "trunk");
    assert(state.trunk["ports"][0]["access_vlan"].get<int>() == 99);
    assert(state.trunk["ports"][1]["access_vlan"].get<int>() == 8);

    // `show ip interface brief` -> nic_info (CIDR 주소)
    assert(state.nic["interfaces"][0]["name"].get<std::string>() == "Ethernet1");
    assert(state.nic["interfaces"][0]["addresses"][0]["address"].get<std::string>() ==
           "172.18.10.2");

    // ARP: 점 표기 MAC 정규화 + 복수 인터페이스
    assert(state.arp["entries"][0]["mac"].get<std::string>() == "0c:ae:21:dd:00:01");
    assert(Count(state.arp["entries"][1], "interfaces") == 2);

    assert(state.snapshot.contains("vlan_status"));
    assert(state.snapshot.contains("trunk_status"));
    assert(state.snapshot.contains("nic_status"));
    assert(state.snapshot.contains("arp_table"));
    assert(state.route.is_null());
}

// ---------------------------------------------------------------------------
//  5) FRR 라우터 (Alpine) — 호스트 커널 `ip ...` 로 수집
//
//  FRR 은 라우팅 데몬이고 실제 호스트 OS 는 리눅스(Alpine)다.
//  따라서 NIC/라우팅/이웃을 `ip ...` 명령으로 그대로 수집해야 한다.
//  (과거에는 ProductKind::kOther 로 분류되어 수집이 통째로 생략됐다.)
// ---------------------------------------------------------------------------
const char* kFrrKernelRoute =
    "default via 192.168.122.1 dev eth0 metric 1 \n"
    "10.10.128.0/21 nhid 30 via 10.99.10.4 dev eth1 proto ospf metric 20 \n"
    "10.99.10.0/24 dev eth1 proto kernel scope link src 10.99.10.1 \n"
    "172.16.255.0/24 dev eth7 proto kernel scope link src 172.16.255.1 \n";

void TestFrrRouter()
{
    std::cout << "\n--- FRR 라우터 (Alpine, 커널 명령) ---\n";

    const std::map<std::string, std::string> outputs = {
        {"ip a", kIpAddrSample},
        {"ip route show", kFrrKernelRoute},
        {"ip neigh show", kIpNeighSample},
    };

    const collector::CollectedState state =
        collector::BuildStateFromOutputs(DeviceType::kRouter, "FRR", outputs);

    assert(state.any_success);

    // 라우팅 — 라우트 코드가 없어도 IpAddr 경로로 파싱되어야 한다.
    assert(state.route.is_object());
    assert(state.route.contains("routes"));
    assert(state.route["routes"].size() == 4);
    assert(state.route["routes"][0]["is_default"].get<bool>());
    assert(state.route["routes"][0]["via"].get<std::string>() == "192.168.122.1");
    assert(state.route["routes"][1]["protocol"].get<std::string>() == "ospf");
    assert(state.route["routes"][1]["destination"].get<std::string>() == "10.10.128.0/21");

    // NIC / 주소 — 커널 `ip a` 결과를 그대로 쓴다.
    assert(state.nic.is_object());
    assert(state.nic.contains("interfaces"));
    assert(!state.nic["interfaces"].empty());

    assert(state.snapshot.contains("route_status"));
    assert(state.snapshot.contains("nic_status"));
    assert(state.snapshot.contains("arp_table"));
    assert(state.snapshot["vendor"].get<std::string>() == "FRR");

    std::cout << "[ ok ] FRR: 라우트 " << state.route["routes"].size() << "건 수집, "
              << "NIC " << state.nic["interfaces"].size() << "건\n";
}

// ---------------------------------------------------------------------------
//  6) nftables 방화벽 (Alpine) — 커널 명령 + nft 규칙
//
//  방화벽도 호스트 OS 는 Alpine 리눅스다. NIC/라우팅/이웃은 `ip ...` 로,
//  필터 규칙은 `nft list ruleset` 으로 수집한다.
//  (과거에는 ProductKind::kOther 로 분류되어 수집이 통째로 생략됐다.)
// ---------------------------------------------------------------------------
const char* kNftRuleset =
    "table inet filter {\n"
    "  chain input {\n"
    "    type filter hook input priority 0; policy drop;\n"
    "    iifname \"lo\" accept\n"
    "    ct state established,related accept\n"
    "    tcp dport 22 accept\n"
    "  }\n"
    "  chain forward {\n"
    "    type filter hook forward priority 0; policy accept;\n"
    "    ip saddr 10.10.131.0/24 drop\n"
    "  }\n"
    "}\n";

void TestFirewall()
{
    std::cout << "\n--- nftables 방화벽 (Alpine, 커널 명령 + nft) ---\n";

    const std::map<std::string, std::string> outputs = {
        {"ip a", kIpAddrSample},
        {"ip route show", kFrrKernelRoute},
        {"ip neigh show", kIpNeighSample},
        {"nft list ruleset", kNftRuleset},
    };

    const collector::CollectedState state =
        collector::BuildStateFromOutputs(DeviceType::kFirewall, "nftables", outputs);

    assert(state.any_success);

    // 규칙 — tables 배열이 스냅샷에 실려야 한다.
    assert(state.rules.is_object());
    assert(state.rules.contains("tables"));
    assert(!state.rules["tables"].empty());
    assert(state.snapshot.contains("firewall_rules"));

    // NIC / 라우팅 / ARP 도 함께 수집된다.
    assert(state.nic.is_object());
    assert(state.route.is_object());
    assert(state.route["routes"].size() == 4);
    assert(state.arp.is_object());
    assert(state.snapshot.contains("route_status"));
    assert(state.snapshot.contains("nic_status"));
    assert(state.snapshot["vendor"].get<std::string>() == "nftables");

    std::cout << "[ ok ] 방화벽: 규칙 테이블 " << state.rules["tables"].size()
              << "개, 라우트 " << state.route["routes"].size() << "건 수집\n";
}

// ---------------------------------------------------------------------------
//  7) Open vSwitch 스위치 (컨테이너) — ovs-vsctl 로 L2 설정 수집
//
//  VSwitch 스위치는 L2 전면이라 관리 IP/라우팅이 없다. 대신 포트의
//  tag(액세스 VLAN)/trunks(트렁크 허용 VLAN)가 L2 설정의 전부다.
//  과거에는 ProductKind::kOther 로 분류되어 수집이 통째로 생략됐다.
//  아래 샘플은 GNS3 랩의 Switch-0/ Switch-1 에서 실제로 캡처한 출력이다.
// ---------------------------------------------------------------------------
const char* kOvsShowAccessSwitch =
    "eb51961d-7e75-46bf-9811-fab71eafc5da\n"
    "    Bridge br0\n"
    "        Port eth0\n"
    "            tag: 10\n"
    "            Interface eth0\n"
    "        Port eth3\n"
    "            tag: 10\n"
    "            Interface eth3\n"
    "        Port br0\n"
    "            Interface br0\n"
    "                type: internal\n"
    "        Port eth4\n"
    "            tag: 10\n"
    "            Interface eth4\n";

// Switch-1 — 업링크가 트렁크, 나머지는 액세스
const char* kOvsShowTrunkSwitch =
    "132ed848-9126-42a3-a136-fb54dfcb07f6\n"
    "    Bridge br0\n"
    "        Port br0\n"
    "            Interface br0\n"
    "                type: internal\n"
    "        Port eth0\n"
    "            trunks: [111, 112]\n"
    "            Interface eth0\n"
    "        Port eth1\n"
    "            tag: 111\n"
    "            Interface eth1\n"
    "        Port eth2\n"
    "            tag: 112\n"
    "            Interface eth2\n";

// `ovs-vsctl list port` — 속성 레코드가 `--` 없이 빈 줄로 나뉜다.
const char* kOvsListPort =
    "_uuid               : 14c38928-d7e2-4caf-9dea-34a27eed1610\n"
    "name                : eth3\n"
    "tag                 : 10\n"
    "trunks              : []\n"
    "vlan_mode           : []\n"
    "\n"
    "_uuid               : eb41023c-e279-4bd2-9a31-1a652c82cd28\n"
    "name                : eth1\n"
    "tag                 : 111\n"
    "trunks              : []\n"
    "vlan_mode           : []\n"
    "\n"
    "_uuid               : f510417f-e978-42df-8974-90afdec15110\n"
    "name                : eth0\n"
    "tag                 : []\n"
    "trunks              : [111, 112]\n"
    "vlan_mode           : []\n";

void TestOpenVSwitchSwitch()
{
    std::cout << "\n--- Open vSwitch 스위치 (ovs-vsctl) ---\n";

    // (a) 액세스 전용 스위치 (Switch-0)
    {
        const std::map<std::string, std::string> outputs = {
            {"ovs-vsctl show", kOvsShowAccessSwitch},
            {"ovs-vsctl list port", ""},
            {"ip a", kIpAddrSample},
        };

        const collector::CollectedState state =
            collector::BuildStateFromOutputs(DeviceType::kSwitch, "OpenVSwitch", outputs);

        assert(state.any_success);
        // 모든 포트가 tag=10 하나이므로 VLAN 은 1개로 모인다.
        assert(Count(state.vlan, "vlans") == 1);
        assert(state.vlan["vlans"][0]["vlan_id"].get<int>() == 10);
        assert(Count(state.vlan["vlans"][0], "ports") == 3);

        // br0 내부 포트는 tag/trunks 가 없으므로 제외된다.
        assert(Count(state.trunk, "ports") == 3);
        assert(state.trunk["ports"][0]["mode"].get<std::string>() == "access");
        assert(state.trunk["ports"][0]["access_vlan"].get<int>() == 10);

        assert(state.snapshot.contains("vlan_status"));
        assert(state.snapshot.contains("trunk_status"));
        assert(state.snapshot.contains("ovs_topology"));
        assert(state.snapshot["vendor"].get<std::string>() == "OpenVSwitch");

        std::cout << "[ ok ] Switch-0: VLAN " << state.vlan["vlans"].size() << "개, 포트 "
                  << state.trunk["ports"].size() << "개\n";
    }

    // (b) 트렁크 + 액세스 혼합 스위치 (Switch-1)
    {
        const std::map<std::string, std::string> outputs = {
            {"ovs-vsctl show", kOvsShowTrunkSwitch},
            {"ip a", kIpAddrSample},
        };

        const collector::CollectedState state =
            collector::BuildStateFromOutputs(DeviceType::kSwitch, "OpenVSwitch", outputs);

        assert(state.any_success);
        // tag 111, 112 액세스 포트 2개 -> VLAN 2개
        assert(Count(state.vlan, "vlans") == 2);
        assert(state.vlan["vlans"][0]["vlan_id"].get<int>() == 111);
        assert(state.vlan["vlans"][1]["vlan_id"].get<int>() == 112);

        // eth0(트렁크) + eth1/eth2(액세스) = 3개. br0 는 제외.
        assert(Count(state.trunk, "ports") == 3);

        // 트렁크 포트를 찾아 허용 VLAN 을 확인한다.
        bool trunk_found = false;
        for (const Json& port : state.trunk["ports"])
        {
            if (port["name"].get<std::string>() == "eth0")
            {
                trunk_found = true;
                assert(port["mode"].get<std::string>() == "trunk");
                assert(Count(port, "trunk_vlans") == 2);
                assert(port["trunk_vlans"][0].get<int>() == 111);
                assert(port["trunk_vlans"][1].get<int>() == 112);
            }
        }
        assert(trunk_found);

        std::cout << "[ ok ] Switch-1: VLAN " << state.vlan["vlans"].size() << "개, 포트 "
                  << state.trunk["ports"].size() << "개 (트렁크 포함)\n";
    }

    // (c) `ovs-vsctl list port` 폴백 — show 가 실패한 경우
    {
        const std::map<std::string, std::string> outputs = {
            {"ovs-vsctl show", ""},
            {"ovs-vsctl list port", kOvsListPort},
        };

        const collector::CollectedState state =
            collector::BuildStateFromOutputs(DeviceType::kSwitch, "OpenVSwitch", outputs);

        assert(state.any_success);
        // eth3(tag10), eth1(tag111) -> VLAN 2개. eth0 는 트렁크라 tag 없음.
        assert(Count(state.vlan, "vlans") == 2);
        assert(Count(state.trunk, "ports") == 3);
        assert(state.snapshot.contains("ovs_ports"));

        bool trunk_found = false;
        for (const Json& port : state.trunk["ports"])
        {
            if (port["name"].get<std::string>() == "eth0")
            {
                trunk_found = true;
                assert(port["mode"].get<std::string>() == "trunk");
            }
        }
        assert(trunk_found);

        std::cout << "[ ok ] list port 폴백: VLAN " << state.vlan["vlans"].size() << "개, 포트 "
                  << state.trunk["ports"].size() << "개\n";
    }
}

// ---------------------------------------------------------------------------
//  8) 실패/빈 입력에서도 예외 없이 부분 결과를 돌려준다
// ---------------------------------------------------------------------------
void TestRobustness()
{
    std::cout << "\n--- 견고성 (빈 입력/알 수 없는 제품) ---\n";

    // 출력이 전혀 없음
    {
        const collector::CollectedState state =
            collector::BuildStateFromOutputs(DeviceType::kRouter, "Cisco 8000v", {});
        assert(!state.any_success);
        assert(state.snapshot.is_object());
        assert(!state.snapshot.contains("route_status"));
    }

    // 명령은 실행됐지만 장치가 오류만 뱉은 경우
    {
        const std::map<std::string, std::string> outputs = {
            {"show ip route", "% Invalid input detected at '^' marker.\n"},
            {"show vlan brief", ""},
        };
        const collector::CollectedState state =
            collector::BuildStateFromOutputs(DeviceType::kRouter, "Cisco 8000v", outputs);
        assert(!state.any_success);
        assert(state.route.is_null());
        assert(state.vlan.is_null());
    }

    // 제품명을 알 수 없어도 크래시하지 않는다.
    {
        const std::map<std::string, std::string> outputs = {
            {"ip a", kIpAddrSample},
        };
        const collector::CollectedState state =
            collector::BuildStateFromOutputs(DeviceType::kSwitch, "Unknown", outputs);
        assert(state.snapshot.is_object());
    }

    // 스냅샷은 항상 JSON 객체다(서버 payload 로 바로 넣을 수 있다).
    {
        const collector::CollectedState state = collector::BuildStateFromOutputs(
            DeviceType::kVirtualMachine, "Ubuntu", {{"ip a", kIpAddrSample}});
        assert(state.snapshot.is_object());
        assert(state.snapshot.contains("vendor"));
        assert(state.snapshot["vendor"].get<std::string>() == "Ubuntu");
    }
}

} // namespace

int main()
{
    std::cout << "=== 수집기(command_collector) 검증 ===\n";

    TestLinuxVm();
    TestLinuxBriefFallback();
    TestCiscoRouter();
    TestAristaSwitch();
    TestFrrRouter();
    TestFirewall();
    TestOpenVSwitchSwitch();
    TestRobustness();

    std::cout << "\n총 검사 " << g_checks << "건, 실패 " << g_failures << "건\n";
    if (g_failures != 0)
    {
        std::cerr << "command_collector_test FAILED\n";
        return 1;
    }
    std::cout << "command_collector_test PASSED\n";
    return 0;
}
