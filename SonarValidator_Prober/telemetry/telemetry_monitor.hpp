#ifndef SONAR_VALIDATOR_PROBER_TELEMETRY_MONITOR_HPP_
#define SONAR_VALIDATOR_PROBER_TELEMETRY_MONITOR_HPP_

#include <atomic>
#include <chrono>
#include <stop_token>

class DatabaseQueue;
class ManagementService;
class ProberConfig;

// 텔레메트리 모니터링 루프입니다.
// - 기본 30초 간격으로 NIC/라우팅/VLAN/트렁크/ARP 상태를 수집·전송합니다.
// - 서버 지시(monitor_interval)에 따라 간격을 동적으로 제어할 수 있습니다.
// - 수집은 collector::CollectState(config, management_service) 가 담당합니다.
// - 수집된 상태는 WebSocket으로 서버에 전송하고, 동시에 데이터베이스 큐에 저장 태스크를 전달합니다.
class TelemetryMonitor
{
public:
    TelemetryMonitor() = default;

    void SetMonitorInterval(std::chrono::seconds interval);
    std::chrono::seconds GetMonitorInterval() const;

    // management_service 는 장치 조회 명령(ip ..., show ..., FastCli) 실행에 사용합니다.
    // 복사할 수 없는 객체이므로 참조로 받습니다.
    void Run(std::stop_token stop_token,
             const ProberConfig& config,
             DatabaseQueue& database_queue,
             ManagementService& management_service);

private:
    std::atomic<int> interval_seconds_{30};
};

#endif // SONAR_VALIDATOR_PROBER_TELEMETRY_MONITOR_HPP_
