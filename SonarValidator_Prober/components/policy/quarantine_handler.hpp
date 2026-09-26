#ifndef SONAR_VALIDATOR_PROBER_QUARANTINE_HANDLER_HPP_
#define SONAR_VALIDATOR_PROBER_QUARANTINE_HANDLER_HPP_

#include <string>
#include <vector>

#include <nlohmann/json.hpp>

class ManagementService;
class ProberConfig;

// 서버가 보낸 격리(quarantine) / 해제(release) 명령을 장치에 적용합니다.
//
// ─────────────────────────────────────────────────────────────────────────
//  격리가 무엇을 하는가
//
//  운영자가 "이 장치를 격리해라" 라고 누르면 서버는 command 봉투를 보냅니다.
//  Agent는 **관리 경로를 제외한 모든 네트워크 인터페이스를 내립니다.**
//
//      eth0  172.16.255.2  → 관리 경로  → 유지  (해제 명령이 들어올 길)
//      eth1  10.10.0.1      → 데이터    → down
//      eth2  10.20.0.1      → 데이터    → down
//
//  ⚠️ 관리 인터페이스는 절대 내리지 않습니다.
//  내리면 서버로 나가는 길이 사라져 **해제 명령이 도달할 수 없습니다.**
//  그러면 운영자는 장치를 되살릴 방법이 없어 콘솔로 들어가야 합니다.
//  그래서 "무엇이 관리 경로인가" 를 최우선으로 판정합니다.
//
// ─────────────────────────────────────────────────────────────────────────
//  왜 인터페이스를 내리는가 (nftables 규칙 추가가 아니라)
//
//  규칙(rule)은 지우면 되돌아가지만, 그 규칙이 실제로 트래픽을 막는지는
//  벤더 구현에 의존합니다. 인터페이스를 내리면 그 위의 모든 트래픽이
//  구조적으로 끊깁니다 — 우회할 규칙도, 순서도 없습니다.
//  격리는 "확실히 끊는 것" 이 목적이므로 이쪽이 정직합니다.
//
//  그리고 서버는 격리된 장치에 **차단본 정책**도 함께 내려줍니다
//  (PolicyRegistryService.quarantineOverride). 재부팅 후 재접속하면
//  명령이 아니라 정책으로 다시 격리됩니다 — 두 겹의 안전장치입니다.
namespace quarantine
{

// 명령 payload 의 action 값입니다. envelope.hpp 의 kActionQuarantine/kActionRelease,
// 서버 QuarantineService.ACTION_QUARANTINE/ACTION_RELEASE 와 정확히 같아야 합니다.
inline constexpr const char* kIsolate = "quarantine";
inline constexpr const char* kRelease = "release";

// 관리망 대역입니다. PoC 네트워크 문서에 정의된 고정 대역이며,
// 서버가 localhost 로 설정된 경우에도 이 대역은 반드시 살려 둡니다.
inline constexpr const char* kManagementPrefix = "172.16.255.0/24";

// 적용 결과입니다. 서버 ack 로 그대로 보고됩니다.
struct Outcome
{
    bool ok{false};                          // 적용 성공 여부
    std::string action{};                    // "quarantine" | "release" | ""
    std::vector<std::string> affected{};     // down/up 시킨 인터페이스
    std::vector<std::string> preserved{};    // 관리 경로라 유지한 인터페이스
    std::string detail{};                    // 사람이 읽는 사유 (실패 시 오류)
};

// 수신 봉투가 격리/해제 명령인지 확인합니다.
// (type == "command" && payload.action == "quarantine" | "release")
bool IsQuarantineCommand(const nlohmann::json& message);

// 격리를 적용합니다. 관리 경로를 뺀 모든 인터페이스를 내립니다.
Outcome Isolate(const ProberConfig& config, ManagementService& mgmt);

// 격리를 해제합니다. 주소를 가진 모든 인터페이스를 올립니다.
//
// 격리 때 내린 목록을 기억하지 않고 <b>전부</b> 올리는 이유는, 프로세스가
// 그 사이 재시작되었을 수 있기 때문입니다. 올리는 방향은 멱등이라
// (이미 올라간 인터페이스에 up 을 다시 걸어도 무해합니다) 안전합니다.
Outcome Release(const ProberConfig& config, ManagementService& mgmt);

// 봉투 하나를 받아 격리/해제를 처리하고, 결과를 ack 로 서버에 보고합니다.
//
// @return 처리했으면 true (격리 명령이 아니면 false)
bool HandleCommand(const ProberConfig& config,
                   ManagementService& mgmt,
                   const nlohmann::json& message);

} // namespace quarantine

#endif // SONAR_VALIDATOR_PROBER_QUARANTINE_HANDLER_HPP_