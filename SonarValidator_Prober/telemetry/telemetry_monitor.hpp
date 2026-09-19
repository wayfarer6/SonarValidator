#ifndef SONAR_VALIDATOR_PROBER_TELEMETRY_MONITOR_HPP_
#define SONAR_VALIDATOR_PROBER_TELEMETRY_MONITOR_HPP_

#include <atomic>
#include <chrono>
#include <stop_token>
#include <string>

#include <nlohmann/json.hpp>

class DatabaseQueue;
class ManagementService;
class ProberConfig;

// 텔레메트리 모니터링 루프입니다.
// - 기본 30초 간격으로 NIC/라우팅/VLAN/트렁크/ARP 상태를 수집·전송합니다.
// - 서버 지시(monitor_interval)에 따라 간격을 동적으로 제어할 수 있습니다.
// - 수집은 collector::CollectState(config, management_service) 가 담당합니다.
// - 수집된 상태는 WebSocket으로 서버에 전송하고, 동시에 데이터베이스 큐에 저장 태스크를 전달합니다.
//
// 오프라인 폴백
//   서버에 연결할 수 없는 환경(망분리, 관리망 미개통)이면 전송이 실패합니다.
//   그때 수집 결과를 JSON 스냅샷 파일로 남겨 두면, 운영자가 나중에 프론트엔드에
//   업로드해 같은 파서 경로로 처리할 수 있습니다.
//   아래 두 설정으로 그 동작을 제어합니다.
//     - SetOfflineExportDirectory(): 스냅샷을 남길 디렉터리 (비어 있으면 기능 끔)
//     - SetOfflineOnly(): true 면 전송을 아예 시도하지 않고 항상 파일로만 남김
class TelemetryMonitor
{
public:
    TelemetryMonitor() = default;

    void SetMonitorInterval(std::chrono::seconds interval);
    std::chrono::seconds GetMonitorInterval() const;

    // 서버 전송이 실패했을 때 스냅샷을 저장할 디렉터리입니다.
    // 빈 문자열이면 오프라인 저장을 하지 않습니다(기존 동작 유지).
    void SetOfflineExportDirectory(std::string directory);

    // true 면 서버 전송을 시도하지 않고 항상 스냅샷 파일만 남깁니다.
    void SetOfflineOnly(bool offline_only);

    // management_service 는 장치 조회 명령(ip ..., show ..., FastCli) 실행에 사용합니다.
    // 복사할 수 없는 객체이므로 참조로 받습니다.
    void Run(std::stop_token stop_token,
             const ProberConfig& config,
             DatabaseQueue& database_queue,
             ManagementService& management_service);

    // 한 번만 수집해 스냅샷 문서를 만듭니다. (서버 불필요 — 오프라인 배포용)
    // 실패해도 예외를 던지지 않고, 수집된 항목만 담은 문서를 돌려줍니다.
    static nlohmann::json CollectSnapshotDocument(const ProberConfig& config,
                                                 ManagementService& management_service);

private:
    std::atomic<int> interval_seconds_{30};
    std::string offline_directory_{};
    std::atomic<bool> offline_only_{false};
};

#endif // SONAR_VALIDATOR_PROBER_TELEMETRY_MONITOR_HPP_
