#ifndef SONAR_VALIDATOR_PROBER_QUARANTINE_HANDLER_HPP_
#define SONAR_VALIDATOR_PROBER_QUARANTINE_HANDLER_HPP_

#include <string>
#include <functional>
#include <vector>

#include <nlohmann/json.hpp>

class ManagementService;
class ProberConfig;

















//
// ─────────────────────────────────────────────────────────────────────────










namespace quarantine
{
using SubnetExecutor = std::function<bool(const std::string&)>;
using SubnetReader = std::function<std::string(const std::string&)>;
bool ApplySubnetTargets(const nlohmann::json& targets, const std::string& management,
                        const std::string& server, const SubnetExecutor& execute,
                        const SubnetReader& read, std::string& detail);
bool ReconcileSubnets(const ProberConfig& config, ManagementService& mgmt,
                      const nlohmann::json& targets);


// 명령 payload 의 action 값입니다. envelope.hpp 의 kActionQuarantine/kActionRelease,
// 서버 QuarantineService.ACTION_QUARANTINE/ACTION_RELEASE 와 정확히 같아야 합니다.
inline constexpr const char* kIsolate = "quarantine";
inline constexpr const char* kRelease = "release";











inline constexpr const char* kDefaultManagementPrefix = "172.16.255.0/24";

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






Outcome Release(const ProberConfig& config, ManagementService& mgmt);




bool HandleCommand(const ProberConfig& config,
                   ManagementService& mgmt,
                   const nlohmann::json& message);

} // namespace quarantine

#endif // SONAR_VALIDATOR_PROBER_QUARANTINE_HANDLER_HPP_
