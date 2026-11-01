#include "components/telemetry/monitor_schedule.hpp"
#include <iostream>
#include <stdexcept>
using namespace std::chrono_literals;
void Require(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
    std::cout << "[ok] " << message << '\n';
}
int main() {
    using namespace monitor_settings;
    const Schedule::Time start{};
    Schedule timer(30, start);
    Require(timer.Due(start), "first collection is immediately due");
    timer.Collected(start);
    Require(!timer.Due(start + 29s) && timer.Due(start + 30s), "default interval");
    timer.Apply({2, 1}, start + 10s);
    Require(timer.Due(start + 10s), "shortening interval changes the current wait");
    timer.Collected(start + 10s);
    timer.Apply({60, 2}, start + 11s);
    Require(!timer.Due(start + 12s) && timer.Due(start + 70s), "lengthening interval cancels the old deadline");
    Require(!timer.Accepts({1, 1}) && !timer.Accepts({1, 2}), "stale or conflicting same-version updates rejected");
    Require(timer.Accepts({60, 2}), "duplicate update is idempotent");
    timer.Reconnected();
    Require(timer.Accepts({30, 0}), "fresh connection accepts settings from a recreated registration");
    Require(!Parse({{"action","set-monitor-interval"},{"monitor_interval_seconds",1.5},{"revision",1}}), "fractional seconds rejected");
    Require(!Parse({{"action","set-monitor-interval"},{"monitor_interval_seconds",0},{"revision",1}}), "zero rejected");
    Require(!Parse({{"action","set-monitor-interval"},{"monitor_interval_seconds",86401},{"revision",1}}), "out-of-range rejected");
    Require(Parse({{"monitor_settings",{{"monitor_interval_seconds",86400},{"revision",0}}}}).has_value(), "hello accepts a one-day interval");
    Require(FromCache("7") == 7 && FromCache("86400") == 86400 && FromCache("bad") == 30 && FromCache("0") == 30 && FromCache("7junk") == 30,
            "durable cache is bounded and corrupt values use the default");
}
