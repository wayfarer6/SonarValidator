#include "policy_receiver.hpp"

#include <iostream>

#include "device_type.hpp"
#include "management_service.hpp"
#include "policy_json.hpp"
#include "prober_config.hpp"

namespace
{

// 1차 분기: DeviceType (config.GetDeviceType)
// 2차 분기: 제품군 (config.GetProductName)
// 3차 분기: 실제 명령 실행은 ManagementService의 벤더별 메서드에 위임합니다.

void ReceiveSwitchPolicy(const ProberConfig& config,
                         ManagementService& mgmt,
                         const nlohmann::json& policy)
{
    const std::string product = config.GetProductName();

    if (product == "OpenVSwitch")
    {
        mgmt.ApplyOpenVSwitchPolicy(policy);
    }
    else if (product == "Arista")
    {
        mgmt.ApplyAristaSwitchPolicy(policy);
    }
    else if (product == "Cisco")
    {
        mgmt.ApplyCiscoSwitchPolicy(policy);
    }
    else
    {
        std::cerr << "[POLICY] Unsupported switch product: " << product << '\n';
    }
}

void ReceiveRouterPolicy(const ProberConfig& config,
                         ManagementService& mgmt,
                         const nlohmann::json& policy)
{
    const std::string product = config.GetProductName();

    if (product == "FRR")
    {
        mgmt.ApplyFrrRouterPolicy(policy);
    }
    else if (product == "Cisco 8000v" || product == "Cisco IOS XE" || product == "Cisco")
    {
        mgmt.ApplyCiscoRouterPolicy(policy);
    }
    else
    {
        std::cerr << "[POLICY] Unsupported router product: " << product << '\n';
    }
}

void ReceiveFirewallPolicy(const ProberConfig& config,
                           ManagementService& mgmt,
                           const nlohmann::json& policy)
{
    const std::string product = config.GetProductName();

    if (product == "nftables")
    {
        mgmt.ApplyNftablesPolicy(policy);
    }
    else
    {
        std::cerr << "[POLICY] Unsupported firewall product: " << product << '\n';
    }
}

} // namespace

void ReceivePolicy(const ProberConfig& config,
                   ManagementService& management_service,
                   const nlohmann::json& policy)
{
    // 배치 형식({"policies": [...]})이면 각 정책을 순회하며 처리합니다.
    const auto policies_it = policy.find("policies");
    if (policies_it != policy.end() && policies_it->is_array())
    {
        for (const auto& item : *policies_it)
        {
            ReceivePolicy(config, management_service, item);
        }
        return;
    }

    switch (config.GetDeviceType())
    {
    case DeviceType::kSwitch:
        ReceiveSwitchPolicy(config, management_service, policy);
        break;
    case DeviceType::kRouter:
        ReceiveRouterPolicy(config, management_service, policy);
        break;
    case DeviceType::kFirewall:
        ReceiveFirewallPolicy(config, management_service, policy);
        break;
    }
}
