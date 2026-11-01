#include "utils/log.hpp"
#ifndef SONAR_VALIDATOR_PROBER_WORKERS_MANAGEMENT_WORKER_HPP_
#define SONAR_VALIDATOR_PROBER_WORKERS_MANAGEMENT_WORKER_HPP_

#include <chrono>
#include <iostream>
#include <stop_token>
#include <string>
#include "utils/stop_wait.hpp"

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
    management_service.SetStopToken(stop_token);

    const std::string agent_id =
        config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();
    management_service.SetAgentId(agent_id);

    std::string previous_policy;
    while (!stop_token.stop_requested())
    {
        // 서버에 policy-request 봉투를 보내고 같은 correlation_id 의 응답을 기다립니다.
        const nlohmann::json policy =
            management_service.fetchPolicy(config.GetDeviceType(), agent_id, stop_token);
        if (stop_token.stop_requested()) break;

        if (policy.is_null() || policy.is_boolean())
        {
            WaitForStop(stop_token, std::chrono::seconds(3));
            continue;
        }

        SV_LOG_DEBUG << "정책 수신: id=" << policy.value("policy_id", std::string{});

        const bool applied = ReceivePolicy(config, management_service, policy);
        const std::string policy_id = policy.value("policy_id", std::string{});
        auto stable_policy = policy;
        stable_policy.erase("valid_until");
        const auto signature = stable_policy.dump();
        if (!applied) { SV_LOG_WARN << "정책 처리 실패: id=" << policy_id; }
        else if (signature != previous_policy) {
            SV_LOG_INFO << "정책 처리 완료: id=" << policy_id;
            previous_policy = signature;
        }
        if (policy_id.empty())
        {
            SV_LOG_WARN << "[MGMT] policy response has no policy_id; result was not acknowledged\n";
        }
        else if (!management_service.ReportPolicyApplied(config.GetDeviceType(),
                                                          agent_id,
                                                          policy_id,
                                                          applied))
        {
            SV_LOG_WARN << "[MGMT] failed to report policy result: policy_id="
                      << policy_id << '\n';
        }

        const auto command_deadline = std::chrono::steady_clock::now() + std::chrono::seconds(1);
        while (!stop_token.stop_requested() &&
               std::chrono::steady_clock::now() < command_deadline)
        {
            std::string raw;
            if (!management_service.TryReceive(raw, std::chrono::milliseconds(200)))
            {
                continue;
            }

            nlohmann::json message;
            try
            {
                message = nlohmann::json::parse(raw);
            }
            catch (const std::exception&)
            {
                SV_LOG_WARN << "관리 메시지 해석 실패: JSON 형식 오류" << '\n';
                continue;
            }

            // 격리/해제 명령이면 적용하고 ack 로 결과를 보고합니다.
            if (quarantine::HandleCommand(config, management_service, message))
            {
                continue;
            }

            SV_LOG_DEBUG << "[MGMT] received server push: " << envelope::Type(message) << '\n';
        }

        WaitForStop(stop_token, std::chrono::seconds(3));
    }
}

#endif // SONAR_VALIDATOR_PROBER_WORKERS_MANAGEMENT_WORKER_HPP_
