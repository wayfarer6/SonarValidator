#include "components/policy/policy_command.hpp"

#include <utility>

#include "components/policy/policy_json.hpp"

const char* PolicyCommand::ActionName(PolicyAction action)
{
    switch (action)
    {
    case PolicyAction::kOn: return "on";
    case PolicyAction::kOff: return "off";
    case PolicyAction::kCreate: return "create";
    case PolicyAction::kRemove: return "remove";
    case PolicyAction::kGet: return "get";
    case PolicyAction::kApply: return "apply";
    }
    return "";
}

std::optional<PolicyCommand> PolicyCommand::Parse(DeviceType device_type,
                                                  std::string product,
                                                  const nlohmann::json& policy,
                                                  std::string* error)
{
    auto fail = [error](const char* reason) -> std::optional<PolicyCommand> {
        if (error != nullptr)
        {
            *error = reason;
        }
        return std::nullopt;
    };

    if (!policy.is_object())
    {
        return fail("policy must be an object");
    }

    const auto command = policy.find("command");
    if (command == policy.end() || command->is_null() ||
        (!command->is_string() &&
         !(command->is_array() && !command->empty() && command->front().is_string())))
    {
        return fail("policy command must be a string");
    }

    const std::string name = policy_json::AsString(policy, "command");
    PolicyAction action;
    if (name == "on")
    {
        action = PolicyAction::kOn;
    }
    else if (name == "off")
    {
        action = PolicyAction::kOff;
    }
    else if (name == "create")
    {
        action = PolicyAction::kCreate;
    }
    else if (name == "remove")
    {
        action = PolicyAction::kRemove;
    }
    else if (name == "get")
    {
        action = PolicyAction::kGet;
    }
    else if (name == "apply")
    {
        action = PolicyAction::kApply;
    }
    else
    {
        return fail("unsupported policy command");
    }

    if (error != nullptr)
    {
        error->clear();
    }
    return PolicyCommand{device_type, std::move(product), action, policy};
}
