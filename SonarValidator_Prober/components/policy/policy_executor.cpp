#include "components/policy/policy_executor.hpp"
#include <iostream>
#include <limits>
#include <stdexcept>

void PolicyExecutorRegistry::Register(std::shared_ptr<const PolicyExecutor> strategy) {
    if (!strategy || strategy->Id().empty()) throw std::invalid_argument("policy executor requires an id");
    for (const auto& current : strategies_)
        if (current->Id() == strategy->Id()) throw std::invalid_argument("duplicate policy executor id");
    strategies_.push_back(std::move(strategy));
}
bool PolicyExecutorRegistry::Apply(DeviceCommandExecutor& device, const PolicyCommand& command) const {
    const PolicyExecutor* selected = nullptr;
    bool ambiguous = false;
    for (const auto& strategy : strategies_) {
        if (!strategy->Supports(command)) continue;
        if (!selected || strategy->Priority() > selected->Priority()) {
            selected = strategy.get();
            ambiguous = false;
        } else if (strategy->Priority() == selected->Priority()) ambiguous = true;
    }
    if (!selected || ambiguous) {
        std::cerr << "[POLICY] no unambiguous executor for " << command.product << '\n';
        return false;
    }
    PolicyCommand normalized = command;
    normalized.payload["command"] = PolicyCommand::ActionName(command.action);
    return selected->Apply(device, normalized);
}
