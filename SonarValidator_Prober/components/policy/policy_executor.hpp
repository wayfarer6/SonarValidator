#ifndef SONAR_POLICY_EXECUTOR_HPP_
#define SONAR_POLICY_EXECUTOR_HPP_
#include <functional>
#include <memory>
#include <string>
#include <vector>
#include "components/device/command_executor.hpp"
#include "components/policy/policy_command.hpp"

class PolicyExecutor {
public:
    virtual ~PolicyExecutor() = default;
    virtual std::string Id() const = 0;
    virtual int Priority() const { return 0; }
    virtual bool Supports(const PolicyCommand& command) const = 0;
    virtual bool Apply(DeviceCommandExecutor& device, const PolicyCommand& command) const = 0;
};

class FunctionalPolicyExecutor final : public PolicyExecutor {
public:
    using Match = std::function<bool(const PolicyCommand&)>;
    using ApplyFunction = std::function<bool(DeviceCommandExecutor&, const PolicyCommand&)>;
    FunctionalPolicyExecutor(std::string id, Match match, ApplyFunction apply, int priority = 0)
        : id_(std::move(id)), match_(std::move(match)), apply_(std::move(apply)), priority_(priority) {}
    std::string Id() const override { return id_; }
    int Priority() const override { return priority_; }
    bool Supports(const PolicyCommand& command) const override { return match_(command); }
    bool Apply(DeviceCommandExecutor& device, const PolicyCommand& command) const override { return apply_(device, command); }
private:
    std::string id_;
    Match match_;
    ApplyFunction apply_;
    int priority_;
};

class PolicyExecutorRegistry {
public:
    void Register(std::shared_ptr<const PolicyExecutor> strategy);
    bool Apply(DeviceCommandExecutor& device, const PolicyCommand& command) const;
private:
    std::vector<std::shared_ptr<const PolicyExecutor>> strategies_;
};
PolicyExecutorRegistry DefaultPolicyExecutors();
#endif
