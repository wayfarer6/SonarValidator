#include "components/policy/policy_executor.hpp"
#include "components/telemetry/telemetry_events.hpp"
#include "module/configuration_module/prober_config.hpp"
#include <iostream>
#include <stdexcept>

void Require(bool value, const char* message) { if (!value) throw std::runtime_error(message); }
class RecordingDevice final : public DeviceCommandExecutor {
public:
    std::vector<std::string> calls;
    bool RunCommand(const std::string& command) override { calls.push_back(command); return true; }
    std::string RunCommandOutput(const std::string& command) override { calls.push_back(command); return "custom-output"; }
    std::string ExecuteIosCli(const std::vector<std::string>& commands) override { calls.insert(calls.end(), commands.begin(), commands.end()); return {}; }
    std::string QueryAristaCli(const std::string& command) override { calls.push_back(command); return {}; }
};
int main() {
    RecordingDevice device;
    collector::TelemetryStrategyRegistry collectors;
    collectors.Register(std::make_shared<collector::FunctionalTelemetryStrategy>("custom",
        [](DeviceType type, const std::string& product) { return type == DeviceType::kRouter && product == "custom"; },
        std::vector<collector::DeviceQuery>{{collector::QueryChannel::Shell, "show custom"}},
        [](DeviceType, const std::string&, const collector::Outputs& outputs) {
            collector::CollectedState state;
            state.snapshot = {{"source", outputs.at("show custom")}};
            state.any_success = true;
            return state;
        }));
    ProberConfig config("id", "router", "Linux", "Linux", DeviceType::kRouter, "custom", 0, "127.0.0.1", 3000);
    auto state = collector::CollectState(config, device, collectors);
    Require(state.any_success && state.snapshot["source"] == "custom-output", "custom collector without central edits");
    Require(device.calls == std::vector<std::string>{"show custom"}, "registered query only");
    Require(collectors.Select(DeviceType::kRouter, "unknown") == nullptr, "unknown collector does not execute");
    const auto* cisco = collector::DefaultTelemetryStrategies().Select(DeviceType::kRouter, "Cisco 8000v");
    Require(cisco && cisco->Queries().size() == 3, "Cisco EEM query contract");
    for (const auto& query : cisco->Queries()) Require(query.channel == collector::QueryChannel::Ios, "Cisco cannot fall through to Linux queries");
    const auto* frr = collector::DefaultTelemetryStrategies().Select(DeviceType::kRouter, "FRR Linux");
    Require(frr && frr->Id() == "frr", "specific collector overrides generic Linux");
    PolicyCommand command{DeviceType::kRouter, "custom", PolicyAction::kOn, {{"command", "off"}}};
    auto policies = DefaultPolicyExecutors();
    device.calls.clear();
    Require(!policies.Apply(device, command) && device.calls.empty(), "unsupported policy cannot run commands");
    policies.Register(std::make_shared<FunctionalPolicyExecutor>("custom",
        [](const PolicyCommand& value) { return value.product == "custom"; },
        [](DeviceCommandExecutor& target, const PolicyCommand& value) { return target.RunCommand(value.payload.at("command")); }));
    Require(policies.Apply(device, command), "custom policy executor");
    Require(device.calls == std::vector<std::string>{"on"}, "validated action used");
    policies.Register(std::make_shared<FunctionalPolicyExecutor>("ambiguous",
        [](const PolicyCommand& value) { return value.product == "custom"; },
        [](DeviceCommandExecutor&, const PolicyCommand&) { throw std::runtime_error("must not execute"); return true; }));
    Require(!policies.Apply(device, command) && device.calls.size() == 1, "ambiguous policy cannot execute");
    std::vector<int> seen;
    FunctionalTelemetryObserver first([&](const TelemetryEvent&) { seen.push_back(1); throw std::runtime_error("test listener failure"); });
    FunctionalTelemetryObserver second([&](const TelemetryEvent& event) { Require(event.agent_id == "router", "observer event metadata"); seen.push_back(2); });
    TelemetryEvents events; events.Subscribe(first); events.Subscribe(second);
    events.Publish(TelemetryEvent{"router", "router", "now", {}, {}});
    Require(seen == std::vector<int>({1, 2}), "listener failure is isolated");
    std::cout << "Device strategy and telemetry observer contracts PASS\n";
}
