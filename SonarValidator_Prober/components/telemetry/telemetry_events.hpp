#include "utils/log.hpp"
#ifndef SONAR_TELEMETRY_EVENTS_HPP_
#define SONAR_TELEMETRY_EVENTS_HPP_
#include <functional>
#include <iostream>
#include <string>
#include <vector>
#include "module/telemetry_module/command_collector.hpp"

struct TelemetryEvent {
    std::string agent_id;
    std::string agent_name;
    std::string collected_at;
    collector::CollectedState state;
    nlohmann::json payload;
};
class TelemetryObserver {
public:
    virtual ~TelemetryObserver() = default;
    virtual void OnTelemetry(const TelemetryEvent& event) = 0;
};
class FunctionalTelemetryObserver final : public TelemetryObserver {
public:
    explicit FunctionalTelemetryObserver(std::function<void(const TelemetryEvent&)> handler) : handler_(std::move(handler)) {}
    void OnTelemetry(const TelemetryEvent& event) override { handler_(event); }
private:
    std::function<void(const TelemetryEvent&)> handler_;
};
class TelemetryEvents {
public:
    void Subscribe(TelemetryObserver& observer) { observers_.push_back(&observer); }
    void Publish(const TelemetryEvent& event) const {
        for (auto* observer : observers_) {
            try { observer->OnTelemetry(event); }
            catch (const std::exception& error) { SV_LOG_WARN << "[TELEMETRY] observer failed: " << error.what() << '\n'; }
            catch (...) { SV_LOG_WARN << "[TELEMETRY] observer failed\n"; }
        }
    }
private:
    std::vector<TelemetryObserver*> observers_;
};
#endif
