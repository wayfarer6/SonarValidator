#include "components/policy/policy_command.hpp"

#include <iostream>
#include <string>

namespace
{

void Expect(bool condition, const char* message, int& failures)
{
    if (condition)
    {
        std::cout << "[ok] " << message << '\n';
    }
    else
    {
        std::cerr << "[FAIL] " << message << '\n';
        ++failures;
    }
}

} // namespace

int main()
{
    using Json = nlohmann::json;
    int failures = 0;

    std::string error;
    const auto command = PolicyCommand::Parse(
        DeviceType::kSwitch, "OpenVSwitch",
        Json{{"command", Json::array({"on"})}, {"interface", "eth0"}}, &error);
    Expect(command.has_value(), "accepts array-wrapped server command", failures);
    if (command)
    {
        Expect(command->device_type == DeviceType::kSwitch, "retains device type", failures);
        Expect(command->product == "OpenVSwitch", "retains product for dispatch", failures);
        Expect(command->action == PolicyAction::kOn, "converts command to typed action", failures);
        Expect(command->payload["interface"] == "eth0", "preserves policy payload", failures);
    }

    Expect(!PolicyCommand::Parse(DeviceType::kRouter, "FRR", Json::array(), &error),
           "rejects non-object policy", failures);
    Expect(!error.empty(), "provides validation error", failures);
    Expect(!PolicyCommand::Parse(DeviceType::kSwitch, "Arista",
                                 Json{{"command", 4}}, &error),
           "rejects non-string command", failures);
    Expect(!PolicyCommand::Parse(DeviceType::kFirewall, "nftables",
                                 Json{{"command", "execute arbitrary"}}, &error),
           "rejects unknown action before dispatch", failures);

    return failures == 0 ? 0 : 1;
}
