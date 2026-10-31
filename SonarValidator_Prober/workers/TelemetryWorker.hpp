
#ifndef SONAR_VALIDATOR_PROBER_WORKERS_TELEMETRY_WORKER_HPP_
#define SONAR_VALIDATOR_PROBER_WORKERS_TELEMETRY_WORKER_HPP_

#include <filesystem>
#include <stop_token>
#include <string>

#include "database/database_service.hpp"
#include "module/configuration_module/prober_config.hpp"
#include "module/management_module/management_service.hpp"
#include "module/telemetry_module/telemetry_monitor.hpp"

void TelemetryWorker(std::stop_token stop_token,
                     const ProberConfig &config,
                     DatabaseService &database_service)
{

    ManagementService management_service(
        config.GetServerIpv4(),
        static_cast<int>(config.GetServerPort()),
        "/api/v1/management");
    management_service.SetStopToken(stop_token);

    TelemetryMonitor monitor;


    monitor.Run(stop_token, config, database_service, management_service);
}

#endif
