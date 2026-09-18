#ifndef SONAR_VALIDATOR_PROBER_COLLECTOR_COMMAND_COLLECTOR_HPP_
#define SONAR_VALIDATOR_PROBER_COLLECTOR_COMMAND_COLLECTOR_HPP_

#include <map>
#include <string>

#include <nlohmann/json.hpp>

#include "device_type.hpp"
#include "prober_config.hpp"

class ManagementService;

// =============================================================================
//  CommandCollector — 장치에 조회 명령을 실행하고 결과를 파싱해 수집 상태를 만든다
//
//  설계 의도
//    - 프로버는 "수집 전용" 이다. 여기서는 조회(show/ip ...)만 실행하고 설정을 바꾸지 않는다.
//    - 어떤 명령을 실행할지는 docs/Agent_Command.md 의 "조회 명령어" 섹션이 단일 진실이다.
//    - 명령 실행은 ManagementService 의 공개 메서드만 사용한다.
//        · Linux/VM       : RunCommandOutput("ip ...")
//        · Cisco IOS-XE   : ExecuteIosCli({...})   (guestshell 의 dohost)
//        · Arista vEOS    : QueryAristaCli("...")  (FastCli 영속 세션)
//    - 파싱은 parser/cli_output_parser.hpp 의 ANTLR 기반 파서가 담당한다.
//      파서는 실패해도 예외를 던지지 않고 {"parsed":false,...} 를 돌려준다.
//
//  테스트 가능성
//    - BuildStateFromOutputs() 는 순수 함수다. 명령 원문(문자열)만 주면
//      CollectedState 를 만들므로 실제 장비 없이 검증할 수 있다.
//    - CollectState() 는 장치에서 출력을 모아 BuildStateFromOutputs() 에 넘기는 얇은 층이다.
// =============================================================================

namespace collector
{

// 한 번의 수집(스냅샷) 결과입니다.
// snapshot 은 서버로 보낼 전체 묶음이고, 나머지는 DB 저장용 부분 객체입니다.
// 수집하지 못한 항목은 null(nlohmann::json{}) 로 남습니다.
struct CollectedState
{
    nlohmann::json snapshot;    // 서버로 보낼 전체 스냅샷
    nlohmann::json nic;         // nic_info + nic_address 용 ("interfaces")
    nlohmann::json route;       // route_table 용 ("routes")
    nlohmann::json vlan;        // vlan_status 용 ("vlans")
    nlohmann::json trunk;       // trunk_status 용 ("ports")
    nlohmann::json arp;         // arp_table 용 ("entries")
    bool any_success = false;   // 수집에 성공한 항목이 하나라도 있는지
};

// 명령 원문(명령 → 출력)만으로 수집 상태를 만드는 순수 함수입니다.
//   - key 예: "ip a", "ip -br addr show", "ip route show", "ip neigh show",
//             "show ip interface brief", "show ip route", "show ip arp",
//             "show vlan brief", "show interfaces switchport"
//   - 값이 비어 있으면 그 항목은 건너뜁니다.
//   - 예외를 밖으로 던지지 않습니다.
CollectedState BuildStateFromOutputs(DeviceType device_type,
                                     const std::string& product_name,
                                     const std::map<std::string, std::string>& outputs);

// 장치에서 조회 명령을 실행해 수집 상태를 만듭니다.
// 명령 실행이 실패해도 예외를 던지지 않고 성공한 항목만 담아 돌려줍니다.
CollectedState CollectState(const ProberConfig& config, ManagementService& management_service);

} // namespace collector

#endif // SONAR_VALIDATOR_PROBER_COLLECTOR_COMMAND_COLLECTOR_HPP_
