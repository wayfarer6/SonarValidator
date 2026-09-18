#ifndef SONAR_VALIDATOR_PROBER_DATABASE_TELEMETRY_STORE_HPP_
#define SONAR_VALIDATOR_PROBER_DATABASE_TELEMETRY_STORE_HPP_

#include <string>

#include <nlohmann/json.hpp>

#include "database/database_service.hpp"

// 수집한 네트워크 상태(라우팅/NIC/VLAN/트렁크/ARP)를 DB 큐에 넣는 헬퍼 모음입니다.
//
// 설계 원칙
//  - 기존 EnqueueNicStatusSave 와 같은 패턴: 태스크(람다)만 만들어 큐에 넘기고
//    실제 SQL 은 DB 워커 스레드가 실행합니다. 여기서는 sqlite3 를 직접 만지지 않습니다.
//  - 한 번의 수집(스냅샷) = 태스크 하나 = 트랜잭션 하나. 행이 수천 개여도
//    커밋이 한 번이라 빠르고, 중간 실패 시 롤백되어 반쪽 스냅샷이 남지 않습니다.
//  - JSON 키가 없거나 형식이 다르면 그 행은 건너뜁니다(예외를 밖으로 던지지 않음).
//    수집 루프가 파싱 실패로 멈추면 안 되기 때문입니다.
//
// 입력 JSON 은 parser/cli_output_parser.hpp 의 결과를 그대로 받습니다.
//  - NIC    : {"interfaces":[{"index","name","parent","mac","mtu","state",
//                            "flags":[],"link_type",
//                            "addresses":[{"family","address","prefix_len",
//                                          "scope","interface"}]}]}
//  - Route  : {"routes":[{"protocol","selected","fib","prefix","metric",
//                         "next_hop","interface_name","connected"}]}
//  - VLAN   : {"vlans":[{"vlan_id","name","status","ports":[]}]}
//  - Switch : {"ports":[{"name","mode","access_vlan","trunk_vlans":[],
//                        "vlan_mode","admin_enabled"}]}
//  - ARP    : {"entries":[{"address","mac","interface","state","age","type",
//                          "interfaces":[]}]}
namespace telemetry_store
{

// 현재 시각을 UTC "YYYY-MM-DDTHH:MM:SSZ" 문자열로 만듭니다.
// 한 번의 수집에서 나온 여러 테이블 행이 같은 시각을 갖도록
// 호출부에서 한 번만 만들어 collected_at 인자로 넘기는 것을 권장합니다.
std::string CurrentUtcTimestamp();

// 반환값 공통 규칙
//   true  : 저장 태스크를 큐에 넣었거나, 저장할 행이 없어 아무 일도 하지 않음.
//   false : 큐가 닫혀 있거나 입력 처리 중 문제가 있었음.
// collected_at 이 비어 있으면 CurrentUtcTimestamp() 를 사용합니다.

// route_table 저장. (JSON key: "routes")
bool EnqueueRouteStatusSave(DatabaseQueue& database_queue,
                            const std::string& agent,
                            const nlohmann::json& route_status,
                            const std::string& collected_at = {});

// nic_info + nic_address 저장. (JSON key: "interfaces", 주소는 "addresses")
// 주소(nic_address) 행은 부모 인터페이스와 같은 collected_at 을 공유합니다.
bool EnqueueNicInfoSave(DatabaseQueue& database_queue,
                        const std::string& agent,
                        const nlohmann::json& nic_status,
                        const std::string& collected_at = {});

// 기존 설계용 vlan_table 과 별개인 런타임 테이블(vlan_status)에 저장.
// (JSON key: "vlans")
bool EnqueueVlanStatusSave(DatabaseQueue& database_queue,
                           const std::string& agent,
                           const nlohmann::json& vlan_status,
                           const std::string& collected_at = {});

// trunk_status 저장. (JSON key: "ports")
bool EnqueueTrunkStatusSave(DatabaseQueue& database_queue,
                            const std::string& agent,
                            const nlohmann::json& trunk_status,
                            const std::string& collected_at = {});

// arp_table 저장. (JSON key: "entries")
// 주의: 현재 파서는 아직 "entries" 를 만들지 않습니다(추가 예정).
// 키가 없으면 조용히 0행으로 처리합니다.
bool EnqueueArpTableSave(DatabaseQueue& database_queue,
                         const std::string& agent,
                         const nlohmann::json& arp_table,
                         const std::string& collected_at = {});

} // namespace telemetry_store

#endif // SONAR_VALIDATOR_PROBER_DATABASE_TELEMETRY_STORE_HPP_
