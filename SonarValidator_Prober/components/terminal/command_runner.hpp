#ifndef SONAR_VALIDATOR_PROBER_COMMAND_RUNNER_HPP_
#define SONAR_VALIDATOR_PROBER_COMMAND_RUNNER_HPP_

#include <chrono>
#include <cstddef>
#include <string>
#include <stop_token>

namespace command_runner
{

struct Result
{
    std::string output;
    int exit_code{-1};
    bool completed{false};
};

Result RunWithStatus(const std::string& command,
                     std::chrono::milliseconds timeout,
                     std::size_t max_output_bytes = 4 * 1024 * 1024,
                     std::stop_token stop_token = {});

std::string Run(const std::string& command,
                std::chrono::milliseconds timeout,
                std::size_t max_output_bytes = 4 * 1024 * 1024);

} // namespace command_runner

#endif // SONAR_VALIDATOR_PROBER_COMMAND_RUNNER_HPP_
