#ifndef SONAR_VALIDATOR_PROBER_WORKERS_MANAGEMENT_WORKER_HPP_
#define SONAR_VALIDATOR_PROBER_WORKERS_MANAGEMENT_WORKER_HPP_

#include <chrono>
#include <iostream>
#include <stop_token>
#include <string>
#include <thread>

#include "components/policy/policy_receiver.hpp"
#include "components/policy/quarantine_handler.hpp"
#include "module/configuration_module/prober_config.hpp"
#include "module/management_module/management_service.hpp"

// 관리 스레드: 서버로부터 정책과 명령을 받아 장치에 적용합니다.


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
        const Json policy =
            management_service.fetchPolicy(config.GetDeviceType(), agent_id, stop_token);

        if (policy.is_null() || policy.is_boolean())
        {
            std::this_thread::sleep_for(std::chrono::seconds(3));
            continue;
        }

        std::cout << "[INFO] Management worker received policy: " << policy.dump() << '\n';

        // 장치 유형(DeviceType)별로 정책 처리 함수를 분기합니다.
        ReceivePolicy(config, management_service, policy);

        // 정책 적용 직후 잠깐 수신 창을 엽니다.
        //
        // 서버는 격리 명령을 command 봉투로 **비동기**로 보냅니다. fetchPolicy 의
        // 대기 루프는 correlation_id 가 다른 봉투를 버리므로, 여기서 받지 않으면
        // 다음 정책 요청까지 최대 3초 동안 아무도 명령을 처리하지 않습니다.
        // (격리는 "즉시" 가 생명이므로 이 창이 필요합니다.)
        const auto command_deadline = std::chrono::steady_clock::now() + std::chrono::seconds(1);
        while (!stop_token.stop_requested() &&
               std::chrono::steady_clock::now() < command_deadline)
        {
            std::string raw;
            if (!management_service.TryReceive(raw, std::chrono::milliseconds(200)))
            {
                continue;
            }

            Json message;
            try
            {
                message = Json::parse(raw);
            }
            catch (const std::exception&)
            {
                std::cerr << "[MGMT] dropped non-JSON frame: " << raw << '\n';
                continue;
            }

            // 격리/해제 명령이면 적용하고 ack 로 결과를 보고합니다.
            if (quarantine::HandleCommand(config, management_service, message))
            {
                continue;
            }

            std::cout << "[MGMT] received server push: " << envelope::Type(message) << '\n';
        }

        std::this_thread::sleep_for(std::chrono::seconds(3));
    }
}

#endif // SONAR_VALIDATOR_PROBER_WORKERS_MANAGEMENT_WORKER_HPP_
