#ifndef SONAR_VALIDATOR_PROBER_TELEMETRY_MONITOR_HPP_
#define SONAR_VALIDATOR_PROBER_TELEMETRY_MONITOR_HPP_

#include <atomic>
#include <chrono>
#include <stop_token>
#include <string>
#include <memory>
#include <vector>
#include "components/telemetry/telemetry_events.hpp"

#include <nlohmann/json.hpp>

class DatabaseService;
class ManagementService;
class ProberConfig;

class TelemetryMonitor
{
public:
    TelemetryMonitor() = default;

    void SetMonitorInterval(std::chrono::seconds interval);
    std::chrono::seconds GetMonitorInterval() const;



    void RegisterStrategy(std::shared_ptr<const collector::TelemetryStrategy> strategy) {
        strategies_.Register(std::move(strategy));
    }

    void AddObserver(std::shared_ptr<TelemetryObserver> observer) {
        if (!observer) throw std::invalid_argument("telemetry observer must not be null");
        observers_.push_back(std::move(observer));
    }

    void Run(std::stop_token stop_token,
             const ProberConfig& config,
             DatabaseService& database_service,
             ManagementService& management_service);

    static nlohmann::json CollectSnapshotDocument(const ProberConfig& config,
                                                 ManagementService& management_service,
                                                 const collector::TelemetryStrategyRegistry& strategies = collector::DefaultTelemetryStrategies());

private:
    std::atomic<int> interval_seconds_{30};
    std::vector<std::shared_ptr<TelemetryObserver>> observers_;
    collector::TelemetryStrategyRegistry strategies_ = collector::DefaultTelemetryStrategies();
};

#endif
