#ifndef SONAR_VALIDATOR_PROBER_POLICY_RECEIVER_HPP_
#define SONAR_VALIDATOR_PROBER_POLICY_RECEIVER_HPP_

#include <nlohmann/json.hpp>

class ManagementService;
class ProberConfig;

// 서버 정책 JSON을 검증된 PolicyCommand로 변환하고 ManagementService에 위임합니다.
// 반환값은 배치에 포함된 모든 명령이 성공적으로 적용됐는지를 나타냅니다.
bool ReceivePolicy(const ProberConfig& config,
                   ManagementService& management_service,
                   const nlohmann::json& policy);

#endif // SONAR_VALIDATOR_PROBER_POLICY_RECEIVER_HPP_
