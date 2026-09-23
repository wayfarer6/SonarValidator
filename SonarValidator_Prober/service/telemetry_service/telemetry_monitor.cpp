#include "telemetry_monitor.hpp"
#include <chrono>
#include <iostream>
#include <thread>
#include <nlohmann/json.hpp>
#include "collector/command_collector.hpp"
#include "database/database_service.hpp"
#include "database/telemetry_store.hpp"
#include "device_type.hpp"
#include "envelope.hpp"
#include "management_service.hpp"
#include "offline/offline_export.hpp"
#include "prober_config.hpp"
#include "telemetry/telemetry_service.hpp"

using Json = nlohmann::json;

void TelemetryMonitor::SetMonitorInterval(std::chrono::seconds interval)
{
    const int seconds = static_cast<int>(interval.count());
    if (seconds > 0)
    {
        interval_seconds_.store(seconds);
    }
}

std::chrono::seconds TelemetryMonitor::GetMonitorInterval() const // 기본값이 30초
{
    return std::chrono::seconds(interval_seconds_.load());
}

void TelemetryMonitor::SetOfflineExportDirectory(std::string directory)
{
    offline_directory_ = std::move(directory);
}

void TelemetryMonitor::SetOfflineOnly(bool offline_only)
{
    offline_only_.store(offline_only);
}

namespace
{

// 수집 결과(CollectedState)를 서버 payload 로 조립합니다.
//
// 서버의 벤더별 설정 파서가 이 키 이름들을 그대로 읽습니다. 따라서
// 온라인 전송과 오프라인 스냅샷이 **같은 함수**를 써야 두 경로가 어긋나지
// 않습니다. (과거에 payload 에 키를 빠뜨려 방화벽 규칙/OVS 토폴로지가
// 서버에서 전부 빈 값이 된 적이 있습니다.)
Json BuildTelemetryPayload(const ProberConfig& config, const collector::CollectedState& collected)
{
    Json body;
    body["agent"] = config.GetAgentName();
    body["kernel"] = config.GetKernelName();

    // 서버가 벤더별 설정 파서를 고를 수 있도록 제품/장치 유형을 함께 보냅니다.
    // (예: "OpenVSwitch" -> OpenVSwitchConfigParser, "FRR" -> FrrRouterConfigParser)
    // 이 값이 없으면 서버는 어떤 파서를 쓸지 알 수 없어 설정 변환을 건너뜁니다.
    const std::string product_name = config.GetProductName();
    if (!product_name.empty())
    {
        body["product"] = product_name;
        body["vendor"] = product_name;
    }
    body["device_type"] = envelope::DeviceTypeToString(config.GetDeviceType());

    // 수집된 항목만 키를 추가합니다.
    // (nic_status 는 기존 VM 경로와 같은 키를 유지하되 모든 장치 유형으로 일반화했습니다.)
    if (collected.nic.is_object())
    {
        body["nic_status"] = collected.nic;
    }
    if (collected.route.is_object())
    {
        body["route_status"] = collected.route;
    }
    if (collected.vlan.is_object())
    {
        body["vlan_status"] = collected.vlan;
    }
    if (collected.trunk.is_object())
    {
        body["trunk_status"] = collected.trunk;
    }
    if (collected.arp.is_object())
    {
        body["arp_table"] = collected.arp;
    }
    // 방화벽 규칙(nftables)과 OVS L2 토폴로지도 함께 보냅니다.
    // 이 두 항목은 장치 유형별로만 수집되므로, 빠뜨리면 서버의 벤더별
    // 설정 파서가 규칙/브리지 정보를 채우지 못해 전부 빈 값이 됩니다.
    if (collected.rules.is_object())
    {
        body["firewall_rules"] = collected.rules;
    }
    if (collected.topology.is_object())
    {
        body["ovs_topology"] = collected.topology;
    }

    return body;
}

// 수집 결과를 SQLite 큐에 저장 태스크로 넣습니다.
// (저장 실패가 수집 루프를 멈추지 않도록 예외를 흡수한다.)
void EnqueueSnapshotSaves(DatabaseQueue& database_queue,
                          const std::string& agent_name,
                          const collector::CollectedState& collected,
                          const std::string& collected_at)
{
    try
    {
        if (collected.nic.is_object())
        {
            telemetry_store::EnqueueNicInfoSave(
                database_queue, agent_name, collected.nic, collected_at);
        }
        if (collected.route.is_object())
        {
            telemetry_store::EnqueueRouteStatusSave(
                database_queue, agent_name, collected.route, collected_at);
        }
        if (collected.vlan.is_object())
        {
            telemetry_store::EnqueueVlanStatusSave(
                database_queue, agent_name, collected.vlan, collected_at);
        }
        if (collected.trunk.is_object())
        {
            telemetry_store::EnqueueTrunkStatusSave(
                database_queue, agent_name, collected.trunk, collected_at);
        }
        if (collected.arp.is_object())
        {
            telemetry_store::EnqueueArpTableSave(
                database_queue, agent_name, collected.arp, collected_at);
        }
    }
    catch (const std::exception& ex)
    {
        // 저장 큐 문제로 수집 루프가 멈추면 안 됩니다.
        std::cerr << "[TELEMETRY] persist skipped: " << ex.what() << '\n';
    }
}

// 서버 전송이 실패했을 때(또는 오프라인 전용 모드일 때) 스냅샷 파일을 남깁니다.
// 반환값은 저장 여부입니다. (실패해도 예외를 던지지 않음)
bool SaveOfflineSnapshot(const std::string& directory,
                         const ProberConfig& config,
                         const std::string& agent_id,
                         const std::string& collected_at,
                         const std::string& reason,
                         const Json& payload)
{
    if (directory.empty())
    {
        return false;
    }

    try
    {
        const Json document = offline::BuildSnapshotDocument(
            agent_id,
            config.GetAgentName(),
            envelope::DeviceTypeToString(config.GetDeviceType()),
            config.GetProductName(),
            config.GetProductName(),
            config.GetKernelName(),
            collected_at,
            reason,
            payload);

        const offline::ExportResult result =
            offline::ExportSnapshot(directory, agent_id, collected_at, document);

        if (result.saved)
        {
            std::cout << "[TELEMETRY] offline snapshot saved: " << result.path << '\n';
            return true;
        }

        // 저장할 내용이 아예 없는 경우(모든 수집 실패)는 정상적인 skip 입니다.
        std::cerr << "[TELEMETRY] offline snapshot skipped: " << result.message << '\n';
        return false;
    }
    catch (const std::exception& ex)
    {
        std::cerr << "[TELEMETRY] offline snapshot failed: " << ex.what() << '\n';
        return false;
    }
}

} // namespace

Json TelemetryMonitor::CollectSnapshotDocument(const ProberConfig& config,
                                               ManagementService& management_service)
{
    const collector::CollectedState collected =
        collector::CollectState(config, management_service);

    const std::string agent_id =
        config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();

    return offline::BuildSnapshotDocument(
        agent_id,
        config.GetAgentName(),
        envelope::DeviceTypeToString(config.GetDeviceType()),
        config.GetProductName(),
        config.GetProductName(),
        config.GetKernelName(),
        telemetry_store::CurrentUtcTimestamp(),
        offline::reason::kManualExport,
        BuildTelemetryPayload(config, collected));
}

void TelemetryMonitor::Run(std::stop_token stop_token,
                           const ProberConfig& config,
                           DatabaseQueue& database_queue,
                           ManagementService& management_service)
{
    try
    {
        TelemetryService telemetry_service(
            config.GetServerIpv4(),
            static_cast<int>(config.GetServerPort()),
            "/api/v1/telemetry");

        // 서버 봉투의 식별자 필드에 넣을 값입니다. (없으면 세션 이름을 씁니다.)
        const std::string agent_id =
            config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();

        while (!stop_token.stop_requested())
        {
            const auto interval = std::chrono::seconds(interval_seconds_.load());
            const auto deadline = std::chrono::steady_clock::now() + interval;

            // 간격 동안 대기하면서 서버 지시(command 봉투)를 수신합니다.
            while (!stop_token.stop_requested() &&
                   std::chrono::steady_clock::now() < deadline)
            {
                std::string instruction;
                if (telemetry_service.tryReceiveText(instruction, std::chrono::milliseconds(200)))
                {
                    try
                    {
                        const Json message = Json::parse(instruction);

                        // 서버는 command 봉투로 모니터링 간격을 조정합니다.
                        // payload.monitor_interval 또는 (구버전) 최상위 monitor_interval 을 모두 받습니다.
                        const Json& payload = envelope::Payload(message);
                        int seconds = 0;
                        if (payload.contains("monitor_interval"))
                        {
                            seconds = payload.at("monitor_interval").get<int>();
                        }
                        else if (message.contains("monitor_interval"))
                        {
                            seconds = message["monitor_interval"].get<int>();
                        }

                        if (seconds > 0)
                        {
                            interval_seconds_.store(seconds);
                            std::cout << "[TELEMETRY] monitor interval updated: "
                                      << seconds << "s\n";
                        }
                        else
                        {
                            std::cout << "[TELEMETRY] server command: "
                                      << envelope::Type(message) << '\n';
                        }
                    }
                    catch (const std::exception&)
                    {
                    }
                }
                else
                {
                    std::this_thread::sleep_for(std::chrono::milliseconds(200));
                }
            }

            if (stop_token.stop_requested())
            {
                break;
            }

            // 조회 명령 실행 + 파싱은 collector 가 담당합니다.
            // 명령이 실패해도 예외를 던지지 않고 성공한 항목만 담아 돌려줍니다.
            const collector::CollectedState collected =
                collector::CollectState(config, management_service);

            // 서버 payload 조립은 오프라인 스냅샷과 공유합니다.
            // (두 경로가 같은 키 집합을 써야 서버 파서가 동일하게 동작한다.)
            const Json body = BuildTelemetryPayload(config, collected);

            // 한 스냅샷에서 나온 모든 행이 같은 collected_at 을 쓰도록 한 번만 만듭니다.
            const std::string collected_at = telemetry_store::CurrentUtcTimestamp();
            const std::string agent_name = config.GetAgentName();

            EnqueueSnapshotSaves(database_queue, agent_name, collected, collected_at);

            // 오프라인 전용 모드면 전송을 시도하지 않고 파일로만 남깁니다.
            // (서버가 없는 랩에서 실행할 때 매 주기마다 5초씩 타임아웃을 기다리지 않게 한다.)
            if (offline_only_.load())
            {
                SaveOfflineSnapshot(offline_directory_, config, agent_id, collected_at,
                                    offline::reason::kForcedOffline, body);
                continue;
            }

            // 텔레메트리는 일방향이라 응답을 기다리지 않습니다.
            const Json report = envelope::Telemetry(agent_id, config.GetDeviceType(), body);
            std::string request = report.dump();

            // 전송이 실패하면(서버 미도달) 스냅샷을 남겨 나중에 업로드할 수 있게 합니다.
            // 성공 여부를 확인하는 이유: 망분리 랩에서는 "조용히 유실" 이 아니라
            // "수집은 됐지만 갈 곳이 없음" 을 운영자가 알아야 하기 때문입니다.
            const bool sent = telemetry_service.sendRequest(request, "/api/v1/telemetry");
            if (!sent)
            {
                SaveOfflineSnapshot(offline_directory_, config, agent_id, collected_at,
                                    offline::reason::kServerUnreachable, body);
            }
        }
    }
    catch (const std::exception& ex)
    {
        std::cerr << "[WARN] Telemetry monitor exception: " << ex.what() << '\n';
    }
}
