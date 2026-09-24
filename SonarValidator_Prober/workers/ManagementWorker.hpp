#ifndef SONAR_VALIDATOR_PROBER_WORKERS_MANAGEMENT_WORKER_HPP_
#define SONAR_VALIDATOR_PROBER_WORKERS_MANAGEMENT_WORKER_HPP_

#include <chrono>
#include <iostream>
#include <stop_token>
#include <string>
#include <thread>

#include "components/policy/policy_receiver.hpp"
#include "module/configuration_module/prober_config.hpp"
#include "module/management_module/management_service.hpp"

// 관리 스레드: 서버로부터 정책을 받아 장치에 적용합니다.


void ManagementWorker(std::stop_token stop_token, const ProberConfig &config)
{
    ManagementService management_service(
        config.GetServerIpv4(),
        static_cast<int>(config.GetServerPort()),
        "/api/v1/management");

    // 에이전트 식별자: 설정의 AGENT_NAME을 우선 사용합니다.
    // (AGENT_ID 는 아직 설정에 없으므로 비어 있을 수 있습니다.)
    const std::string agent_id =
        config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();
    management_service.SetAgentId(agent_id);

    while (!stop_token.stop_requested())
    {
        // 서버에 policy-request 봉투를 보내고 같은 correlation_id 의 응답을 기다립니다.
        const Json policy = management_service.fetchPolicy(config.GetDeviceType(), agent_id);

        if (policy.is_null() || policy.is_boolean())
        {
            std::this_thread::sleep_for(std::chrono::seconds(3));
            continue;
        }

        std::cout << "[INFO] Management worker received policy: " << policy.dump() << '\n';

        // 장치 유형(DeviceType)별로 정책 처리 함수를 분기합니다.
        ReceivePolicy(config, management_service, policy);

        std::this_thread::sleep_for(std::chrono::seconds(3));
    }
}

#endif // SONAR_VALIDATOR_PROBER_WORKERS_MANAGEMENT_WORKER_HPP_
