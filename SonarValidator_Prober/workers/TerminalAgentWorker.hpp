#ifndef SONAR_VALIDATOR_PROBER_TERMINAL_AGENT_WORKER_HPP_
#define SONAR_VALIDATOR_PROBER_TERMINAL_AGENT_WORKER_HPP_

#include <stop_token>

#include "module/configuration_module/prober_config.hpp"

void TerminalAgentWorker(std::stop_token stop_token, const ProberConfig& config);

#endif // SONAR_VALIDATOR_PROBER_TERMINAL_AGENT_WORKER_HPP_
