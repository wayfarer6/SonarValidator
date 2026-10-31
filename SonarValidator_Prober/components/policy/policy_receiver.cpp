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
