#ifndef SONAR_POLICY_ACTIONS_HPP_
#define SONAR_POLICY_ACTIONS_HPP_
#include <nlohmann/json.hpp>
#include "components/device/command_executor.hpp"

class PolicyActions final {
public:
    explicit PolicyActions(DeviceCommandExecutor& device) : device_(device) {}
    bool ApplyOpenVSwitchPolicy(const nlohmann::json& policy);
    bool ApplyAristaSwitchPolicy(const nlohmann::json& policy);
    bool ApplyCiscoSwitchPolicy(const nlohmann::json& policy);
    bool ApplyCiscoRouterPolicy(const nlohmann::json& policy);
    bool ApplyFrrRouterPolicy(const nlohmann::json& policy);
    bool ApplyNftablesPolicy(const nlohmann::json& policy);
    bool ApplyVmPolicy(const nlohmann::json& policy);
private:
    DeviceCommandExecutor& device_;
    std::string primary_interface_;
    bool RunCommand(const std::string& command) { return device_.RunCommand(command); }
    std::string RunCommandOutput(const std::string& command) { return device_.RunCommandOutput(command); }
    std::string ExecuteIosCli(const std::vector<std::string>& commands) { return device_.ExecuteIosCli(commands); }
    bool ApplyCiscoRouterAcl(const nlohmann::json& policy);
    bool ApplyFrrRouterAcl(const nlohmann::json& policy);
    bool ApplyOpenVSwitchAcl(const nlohmann::json& policy);
    bool ApplyNetplanPolicy(const nlohmann::json& policy, const std::string& command);
    bool ApplyAddressesWithIp(const nlohmann::json& policy, const nlohmann::json::const_iterator& ethernets, const std::string& command);
    bool HasCommand(const std::string& program);
    std::string PrimaryInterface();
    std::string ResolveInterfaceName(const std::string& name);
};
#endif
