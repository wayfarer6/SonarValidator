#include "utils/stop_wait.hpp"
#include <iostream>
#include <stdexcept>

using namespace std::chrono_literals;

int main()
{
    const auto start = std::chrono::steady_clock::now();
    if (WaitForStop({}, 40ms) || std::chrono::steady_clock::now() - start < 35ms)
        throw std::runtime_error("normal retry delay did not wait");
    std::stop_source stop;
    std::jthread cancel([&] { std::this_thread::sleep_for(50ms); stop.request_stop(); });
    const auto cancel_start = std::chrono::steady_clock::now();
    if (!WaitForStop(stop.get_token(), 5s) || std::chrono::steady_clock::now() - cancel_start > 300ms)
        throw std::runtime_error("retry delay did not respond to stop");
    std::cout << "[ok] ordinary delay and interruptible retry without a condition variable\n";
}
