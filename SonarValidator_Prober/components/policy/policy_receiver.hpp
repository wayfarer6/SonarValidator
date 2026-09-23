#ifndef SONAR_VALIDATOR_PROBER_POLICY_RECEIVER_HPP_
#define SONAR_VALIDATOR_PROBER_POLICY_RECEIVER_HPP_

#include <nlohmann/json.hpp>

class ManagementService;
class ProberConfig;

// 서버로부터 수신한 정책을 장치 유형(DeviceType)에 따라
// 스위치/라우터/방화벽 전용 처리 함수로 분기합니다.
// (VM은 네트워크 제어 대상이 아니며, NIC/연결 상태는 텔레메트리로 전송합니다.)
void ReceivePolicy(const ProberConfig& config,
                   ManagementService& management_service,
                   const nlohmann::json& policy);

#endif // SONAR_VALIDATOR_PROBER_POLICY_RECEIVER_HPP_
