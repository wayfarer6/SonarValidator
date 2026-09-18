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

std::chrono::seconds TelemetryMonitor::GetMonitorInterval() const
{
    return std::chrono::seconds(interval_seconds_.load());
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

            Json body;
            body["agent"] = config.GetAgentName();
            body["kernel"] = config.GetKernelName();

            // 조회 명령 실행 + 파싱은 collector 가 담당합니다.
            // 명령이 실패해도 예외를 던지지 않고 성공한 항목만 담아 돌려줍니다.
            const collector::CollectedState collected =
                collector::CollectState(config, management_service);

            // 서버 payload: 수집된 항목만 키를 추가합니다.
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

            // 한 스냅샷에서 나온 모든 행이 같은 collected_at 을 쓰도록 한 번만 만듭니다.
            const std::string collected_at = telemetry_store::CurrentUtcTimestamp();
            const std::string agent_name = config.GetAgentName();

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

            // 텔레메트리는 일방향이라 응답을 기다리지 않습니다.
            const Json report = envelope::Telemetry(agent_id, config.GetDeviceType(), std::move(body));
            std::string request = report.dump();
            telemetry_service.sendRequest(request, "/api/v1/telemetry");
        }
    }
    catch (const std::exception& ex)
    {
        std::cerr << "[WARN] Telemetry monitor exception: " << ex.what() << '\n';
    }
}
