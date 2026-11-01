#ifndef SONAR_MONITOR_SCHEDULE_HPP_
#define SONAR_MONITOR_SCHEDULE_HPP_
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <optional>
#include <string>
#include <nlohmann/json.hpp>

namespace monitor_settings {
constexpr int kDefaultSeconds = 30;
constexpr int kMaxSeconds = 86400;
struct Update { int seconds; std::int64_t revision; };
inline std::optional<Update> Parse(const nlohmann::json& payload) {
    if (!payload.is_object()) return {};
    const auto& settings = payload.contains("monitor_settings") ? payload["monitor_settings"] : payload;
    if (!settings.is_object() || !settings.contains("monitor_interval_seconds") || !settings.contains("revision")) return {};
    if (!payload.contains("monitor_settings") && payload.value("action", std::string{}) != "set-monitor-interval") return {};
    if (!settings["monitor_interval_seconds"].is_number_integer() || !settings["revision"].is_number_integer()) return {};
    const auto seconds = settings["monitor_interval_seconds"].get<std::int64_t>();
    const auto revision = settings["revision"].get<std::int64_t>();
    if (seconds < 1 || seconds > kMaxSeconds || revision < 0) return {};
    return Update{static_cast<int>(seconds), revision};
}
inline int FromCache(const std::string& text) {
    try {
        std::size_t used = 0;
        const int seconds = std::stoi(text, &used);
        if (used == text.size() && seconds >= 1 && seconds <= kMaxSeconds) return seconds;
    } catch (...) {}
    return kDefaultSeconds;
}
class Schedule {
public:
    using Clock = std::chrono::steady_clock;
    using Time = Clock::time_point;
    explicit Schedule(int seconds, Time now) : seconds_(seconds), next_(now) {}
    int Seconds() const { return seconds_; }
    std::int64_t Revision() const { return revision_; }
    void Reconnected() { revision_ = -1; }
    bool Accepts(const Update& update) const {
        return update.seconds >= 1 && update.seconds <= kMaxSeconds && update.revision >= 0 &&
            (update.revision > revision_ || (update.revision == revision_ && update.seconds == seconds_));
    }
    void Apply(const Update& update, Time now) {
        seconds_ = update.seconds;
        revision_ = update.revision;
        next_ = last_finished_ ? *last_finished_ + std::chrono::seconds(seconds_) : now;
    }
    bool Due(Time now) const { return now >= next_; }
    std::chrono::milliseconds Wait(Time now) const {
        if (Due(now)) return std::chrono::milliseconds(0);
        return std::max(std::chrono::milliseconds(1), std::min(std::chrono::duration_cast<std::chrono::milliseconds>(next_ - now), std::chrono::milliseconds(200)));
    }
    void Collected(Time finished) {
        last_finished_ = finished;
        next_ = finished + std::chrono::seconds(seconds_);
    }
private:
    int seconds_;
    std::int64_t revision_ = -1;
    Time next_;
    std::optional<Time> last_finished_;
};
} // namespace monitor_settings
#endif
