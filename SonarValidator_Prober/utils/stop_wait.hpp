#ifndef SONAR_STOP_WAIT_HPP_
#define SONAR_STOP_WAIT_HPP_

#include <algorithm>
#include <chrono>
#include <stop_token>
#include <thread>

// Retry delays check shutdown at least every 50 ms; no extra thread or queue.
inline bool WaitForStop(std::stop_token stop, std::chrono::milliseconds delay)
{
    const auto deadline = std::chrono::steady_clock::now() + delay;
    while (!stop.stop_requested())
    {
        const auto remaining = std::chrono::duration_cast<std::chrono::milliseconds>(
            deadline - std::chrono::steady_clock::now());
        if (remaining.count() <= 0) break;
        std::this_thread::sleep_for(std::min(remaining, std::chrono::milliseconds(50)));
    }
    return stop.stop_requested();
}

#endif
