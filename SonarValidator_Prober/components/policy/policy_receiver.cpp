#include "components/policy/policy_receiver.hpp"

#include <iostream>
#include "components/policy/quarantine_handler.hpp"

#include "components/device/device_type.hpp"
#include "module/management_module/management_service.hpp"
#include "components/policy/policy_command.hpp"
#include "module/configuration_module/prober_config.hpp"

bool ReceivePolicy(const ProberConfig& config,
                   ManagementService& management_service,
                   const nlohmann::json& policy)
{
    // 배치 형식({"policies": [...]})이면 각 정책을 순회하며 처리합니다.
    const auto policies_it = policy.find("policies");
    if (policies_it != policy.end() && policies_it->is_array())
    {
        bool applied = true;

        // 차단 ACL 은 규칙 목록보다 <b>먼저</b> 맞춥니다.
        //
        // 왜 먼저인가
        //   acl_apply 는 "지금 남아 있어야 하는 차단 규칙 전체" 입니다.
        //   운영자가 금지 연결을 지우면 남은 규칙만 오는데, 이 노드가
        //   이전 규칙을 장치에서 걷어냅니다. 규칙 목록을 나중에 적용해도
        //   결과는 같지만, 차단이 잠깐 풀린 상태로 남는 창(window)을
        //   없애려면 먼저 처리하는 편이 안전합니다.
        //
        // ⚠️ acl_apply 가 실패해도 규칙 목록은 계속 적용합니다.
        //    한 장치의 ACL 실패가 나머지 정책(주소 선언·경로)을 막으면
        //    장치가 이전 상태로 남습니다.
        const auto acl_apply = policy.find("acl_apply");
        if (acl_apply != policy.end() && acl_apply->is_object())
        {
            std::string acl_error;
            const auto acl_command = PolicyCommand::Parse(config.GetDeviceType(),
                                                          config.GetProductName(),
                                                          *acl_apply,
                                                          &acl_error);
            if (!acl_command)
            {
                std::cerr << "[POLICY] Rejected ACL apply: " << acl_error << '\n';
                applied = false;
            }
            else if (!management_service.ApplyPolicyCommand(*acl_command))
            {
                std::cerr << "[POLICY] ACL apply failed for product "
                          << acl_command->product << '\n';
                applied = false;
            }
        }

        if (!policy.contains("subnet_quarantine") ||
            !quarantine::ReconcileSubnets(config, management_service, policy.at("subnet_quarantine")))
        {
            applied = false;
        }

        for (const auto& item : *policies_it)
        {
            if (!ReceivePolicy(config, management_service, item))
            {
                applied = false;
            }
        }
        return applied;
    }

    std::string error;
    auto command = PolicyCommand::Parse(config.GetDeviceType(),
                                        config.GetProductName(),
                                        policy,
                                        &error);
    if (!command)
    {
        std::cerr << "[POLICY] Rejected policy: " << error << '\n';
        return false;
    }
    const bool applied = management_service.ApplyPolicyCommand(*command);
    if (!applied)
    {
        std::cerr << "[POLICY] Policy command failed for product "
                  << command->product << '\n';
    }
    return applied;
}
