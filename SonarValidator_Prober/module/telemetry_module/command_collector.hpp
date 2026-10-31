#ifndef SONAR_COMMAND_COLLECTOR_HPP_
#define SONAR_COMMAND_COLLECTOR_HPP_
#include <functional>
#include <map>
#include <memory>
#include <string>
#include <vector>
#include <nlohmann/json.hpp>
#include "components/device/command_executor.hpp"
#include "components/device/device_type.hpp"
class ProberConfig;

namespace collector {
struct CollectedState
{
    nlohmann::json snapshot = nlohmann::json::object();
    nlohmann::json nic;
    nlohmann::json route;
    nlohmann::json vlan;
    nlohmann::json trunk;
    nlohmann::json arp;
    nlohmann::json rules;
    nlohmann::json topology;
    bool any_success = false;
};


using Outputs = std::map<std::string, std::string>;
enum class QueryChannel { Shell, Ios, Arista };
struct DeviceQuery { QueryChannel channel; std::string command; };

class TelemetryStrategy {
public:
    virtual ~TelemetryStrategy() = default;
    virtual std::string Id() const = 0;
    virtual int Priority() const { return 0; }
    virtual bool Supports(DeviceType type, const std::string& product) const = 0;
    virtual const std::vector<DeviceQuery>& Queries() const = 0;
    virtual CollectedState Parse(DeviceType type, const std::string& product, const Outputs& outputs) const = 0;
};
class FunctionalTelemetryStrategy final : public TelemetryStrategy {
public:
    using Match = std::function<bool(DeviceType, const std::string&)>;
    using Parser = std::function<CollectedState(DeviceType, const std::string&, const Outputs&)>;
    FunctionalTelemetryStrategy(std::string id, Match match, std::vector<DeviceQuery> queries, Parser parser, int priority = 0)
        : id_(std::move(id)), match_(std::move(match)), queries_(std::move(queries)), parser_(std::move(parser)), priority_(priority) {}
    std::string Id() const override { return id_; }
    int Priority() const override { return priority_; }
    bool Supports(DeviceType type, const std::string& product) const override { return match_(type, product); }
    const std::vector<DeviceQuery>& Queries() const override { return queries_; }
    CollectedState Parse(DeviceType type, const std::string& product, const Outputs& outputs) const override { return parser_(type, product, outputs); }
private:
    std::string id_;
    Match match_;
    std::vector<DeviceQuery> queries_;
    Parser parser_;
    int priority_;
};
class TelemetryStrategyRegistry {
public:
    void Register(std::shared_ptr<const TelemetryStrategy> strategy);
    const TelemetryStrategy* Select(DeviceType type, const std::string& product) const;
private:
    std::vector<std::shared_ptr<const TelemetryStrategy>> strategies_;
};
const TelemetryStrategyRegistry& DefaultTelemetryStrategies();
CollectedState BuildStateFromOutputs(DeviceType type, const std::string& product, const Outputs& outputs,
                                    const TelemetryStrategyRegistry& strategies = DefaultTelemetryStrategies());
CollectedState CollectState(const ProberConfig& config, DeviceCommandExecutor& device,
                            const TelemetryStrategyRegistry& strategies = DefaultTelemetryStrategies());
}
#endif
