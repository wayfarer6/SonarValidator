#ifndef SONAR_VALIDATOR_PROBER_PARSER_CLI_OUTPUT_PARSER_HPP_
#define SONAR_VALIDATOR_PROBER_PARSER_CLI_OUTPUT_PARSER_HPP_

#include <string>

#include <nlohmann/json.hpp>

#include "../device_type.hpp"

// =============================================================================
//  CliOutputParser — 벤더 CLI 조회 출력을 JSON 으로 변환하는 ANTLR 기반 파서
//
//  설계 의도
//    - 제어(정책 적용)는 Spring Backend 가 직접 수행한다. 프로버는 "조회 명령어"가
//      만든 출력을 파싱해 서버로 올려보내는 수집 역할에 집중한다.
//    - 따라서 이 파서는 읽기 전용이며, 부작용(명령 실행/설정 변경)이 전혀 없다.
//    - 반환값은 항상 envelope::Telemetry 의 payload 로 바로 넣을 수 있는 JSON 이다.
//
//  DeviceType/Product 별 진입점
//    ParseNicStatus      : 스위치/라우터/방화벽/VM 공통 (ip a, ip -br addr)
//    ParseRouteStatus    : 라우터 (show ip route)
//    ParseInterfaceStatus: 라우터/스위치 (show ip interface brief)
//    ParseSwitchTopology : 스위치 (ovs-vsctl show / show vlan brief / switchport)
//    ParseFirewallRules  : 방화벽 (nft list ruleset)
//
//  파싱에 실패하면 예외를 던지지 않고 "파싱 실패" 를 담은 JSON 을 돌려준다.
//  (수집 루프가 죽으면 텔레메트리 전체가 멈추므로 실패도 데이터로 취급한다.)
//    { "parsed": false, "error": "...", "raw": "<원문>" }
// =============================================================================

namespace cli_parser
{

// 대상 벤더. ProberConfig::GetProductName() 값을 그대로 쓴다.
enum class Vendor
{
    kOpenVSwitch,
    kFrr,
    kCisco,
    kArista,
    kNftables,
    kUbuntu,
    kUnknown
};

// ProberConfig::GetProductName() 문자열을 Vendor 로 변환합니다.
Vendor VendorFromProductName(const std::string& product_name);

// 문자열 이름(디버그/JSON 출력용)을 반환합니다.
std::string VendorName(Vendor vendor);

// ---------------------------------------------------------------------------
// NIC / 주소 정보  —  `ip a`, `ip addr show`, `ip -br addr show`
//   {
//     "interfaces": [
//       { "index": 2, "name": "eth1.131", "parent": "eth1",
//         "flags": ["BROADCAST","MULTICAST","UP","LOWER_UP"],
//         "mtu": "1500", "qdisc": "noqueue", "state": "UP", "qlen": "1000",
//         "mac": "02:42:7c:24:78:01",
//         "addresses": [ { "family":"inet", "address":"10.10.131.1",
//                          "prefix_len":24, "scope":"global",
//                          "interface":"eth1.131", "valid_lft":"forever" } ] }
//     ],
//     "brief": [ { "name":"eth0", "state":"UP",
//                  "addresses":["10.40.121.10/24","fe80::..."], "mac":"..." } ]
//   }
// ---------------------------------------------------------------------------
nlohmann::json ParseNicStatus(const std::string& raw_output);

// 위와 동일하지만 `ip -br addr show` 출력만 파싱합니다.
nlohmann::json ParseNicBrief(const std::string& raw_output);

// ---------------------------------------------------------------------------
// ARP / 이웃 테이블  —  `ip neigh show`, `show arp`, `show ip arp`
//   { "entries": [ { "address":"10.0.9.1", "mac":"0c:2d:07:65:99:f3",
//                    "interface":"ens3", "state":"REACHABLE",
//                    "age":"2:31:51", "type":"ARPA",
//                    "interfaces":["Vlan9","Ethernet3"] } ] }
// ---------------------------------------------------------------------------
nlohmann::json ParseArpTable(const std::string& raw_output, Vendor vendor);

// ---------------------------------------------------------------------------
// 라우팅 테이블  —  `show ip route` (FRR / Cisco 공통)
//   {
//     "protocols": ["ospf","connected"],
//     "gateway_of_last_resort": "...",
//     "routes": [ { "protocol":"ospf", "selected":true, "fib":true,
//                   "prefix":"10.20.111.0/24", "metric":"110/200",
//                   "next_hop":"10.99.10.5", "interface_name":"eth0",
//                   "via":"10.99.10.5", "connected":false, "uptime":"00:17:25" } ]
//   }
// ---------------------------------------------------------------------------
nlohmann::json ParseRouteStatus(const std::string& raw_output, Vendor vendor);

// ---------------------------------------------------------------------------
// 인터페이스 요약  —  `show ip interface brief`
//   { "interfaces": [ { "name":"eth0", "ip_address":"10.20.0.1",
//                       "method":"NVRAM", "status":"up", "protocol":"up",
//                       "unassigned":false } ] }
// ---------------------------------------------------------------------------
nlohmann::json ParseInterfaceStatus(const std::string& raw_output, Vendor vendor);

// ---------------------------------------------------------------------------
// 스위치 토폴로지
//   ParseOvsTopology   : `ovs-vsctl show`
//     { "bridges": [ { "name":"br0", "ports": [ { "name":"eth1",
//         "tag":141, "trunks":[], "vlan_mode":"",
//         "interfaces":[ {"name":"eth1","type":"system"} ] } ] } ] }
//
//   ParseSwitchVlan    : `show vlan brief`
//     { "vlans": [ { "vlan_id":8, "name":"VLAN8", "status":"active",
//                    "ports":["Cpu","Et2"] } ] }
//
//   ParseSwitchPorts   : `show interfaces switchport`
//     { "ports": [ { "name":"Ethernet1", "mode":"trunk",
//                    "access_vlan":99, "trunk_vlans":[111,112] } ] }
// ---------------------------------------------------------------------------
nlohmann::json ParseOvsTopology(const std::string& raw_output);
nlohmann::json ParseSwitchVlan(const std::string& raw_output);
nlohmann::json ParseSwitchPorts(const std::string& raw_output);

// ---------------------------------------------------------------------------
// 방화벽 룰셋  —  `nft list ruleset`
//   { "tables": [ { "family":"inet", "name":"filter",
//       "chains": [ { "name":"forward", "type":"filter", "hook":"forward",
//                     "priority":"0", "policy":"drop",
//                     "rules": [ { "raw":"ip saddr 1.2.3.0/24 ... drop",
//                                  "match":"ip saddr 1.2.3.0/24",
//                                  "match_pairs":{"ip saddr":"1.2.3.0/24"},
//                                  "action":"drop", "counter":false,
//                                  "log_prefix":"", "handle":"" } ] } ] } ] }
// ---------------------------------------------------------------------------
nlohmann::json ParseFirewallRules(const std::string& raw_output);

// ---------------------------------------------------------------------------
// 편의 진입점: Product 명에 맞는 조회 파서를 자동 선택합니다.
// (TelemetryMonitor 가 명령 실행 후 호출하는 용도)
//   target: "nic" | "brief" | "route" | "interface" | "topology" | "vlan"
//           | "switchport" | "ruleset"
// ---------------------------------------------------------------------------
nlohmann::json ParseQueryOutput(Vendor vendor,
                                const std::string& target,
                                const std::string& raw_output);

} // namespace cli_parser

#endif // SONAR_VALIDATOR_PROBER_PARSER_CLI_OUTPUT_PARSER_HPP_
