#ifndef SONAR_VALIDATOR_PROBER_POLICY_COMMAND_HPP_
#define SONAR_VALIDATOR_PROBER_POLICY_COMMAND_HPP_

#include <optional>
#include <string>

#include <nlohmann/json.hpp>

#include "components/device/device_type.hpp"

enum class PolicyAction
{
    kOn,
    kOff,
    kCreate,
    kRemove,
    kGet
};

struct PolicyCommand
{
    DeviceType device_type;
    std::string product;
    PolicyAction action;
    nlohmann::json payload;

    static std::optional<PolicyCommand> Parse(DeviceType device_type,
                                              std::string product,
                                              const nlohmann::json& policy,
                                              std::string* error = nullptr);

    static const char* ActionName(PolicyAction action);
};

#endif // SONAR_VALIDATOR_PROBER_POLICY_COMMAND_HPP_
