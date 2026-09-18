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

#include "collector/command_collector.hpp"

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
//  5) 실패/빈 입력에서도 예외 없이 부분 결과를 돌려준다
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
