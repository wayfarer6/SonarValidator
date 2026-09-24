#ifndef SONAR_VALIDATOR_PROBER_WORKERS_MONITOR_WORKER_HPP_
#define SONAR_VALIDATOR_PROBER_WORKERS_MONITOR_WORKER_HPP_

#include <chrono>
#include <stop_token>
#include <thread>

#include "database/database_service.hpp"

// (현재 미사용) 주기적인 상태 모니터링 스레드 자리입니다.
void MonitorWorker(std::stop_token stop_token, DatabaseQueue &database_queue)
{
    (void)database_queue;
    while (!stop_token.stop_requested())
    {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}

#endif // SONAR_VALIDATOR_PROBER_WORKERS_MONITOR_WORKER_HPP_
