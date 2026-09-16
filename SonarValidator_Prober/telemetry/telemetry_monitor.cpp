#include "telemetry_monitor.hpp"

#include <chrono>
#include <iostream>
#include <thread>

#include <nlohmann/json.hpp>
#include <sqlite3.h>

#include "database/database_service.hpp"
#include "device_type.hpp"
#include "envelope.hpp"
#include "prober_config.hpp"
#include "telemetry/telemetry_service.hpp"
#include "vm/vm_service.hpp"

using Json = nlohmann::json;

namespace
{

// NIC 상태를 데이터베이스 큐에 저장하는 태스크를 만들어 전달합니다.
// 실제 SQL 실행은 데이터베이스 스레드가 담당하므로 동시성 문제가 없습니다.
void EnqueueNicStatusSave(DatabaseQueue& database_queue,
                          const std::string& agent,
                          const Json& nic_status)
{
    DatabaseTask task;
    const std::string payload = nic_status.dump();

    task.execute = [agent, payload](DbHandle& handle) -> DatabaseResult {
        sqlite3* database = handle.get();
        DatabaseResult result;
        result.sql_task = "INSERT INTO nic_status (agent, payload) VALUES (?, ?)";

        if (database == nullptr)
        {
            return result;  // 핸들이 비어 있으면 빈 결과를 반환합니다.
        }

        const char* sql = "INSERT INTO nic_status (agent, payload) VALUES (?, ?);";
        sqlite3_stmt* statement = nullptr;
        if (sqlite3_prepare_v2(database, sql, -1, &statement, nullptr) != SQLITE_OK)
        {
            return result;
        }

        sqlite3_bind_text(statement, 1, agent.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(statement, 2, payload.c_str(), -1, SQLITE_TRANSIENT);

        if (sqlite3_step(statement) == SQLITE_DONE)
        {
            result.db_task_result.push_back("ok");
        }

        sqlite3_finalize(statement);
        return result;
    };

    database_queue.Push(std::move(task));
}

} // namespace

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
                           DatabaseQueue& database_queue)
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

            // VM은 NIC/연결 상태를 함께 전송하고 DB에도 저장합니다.
            if (config.GetDeviceType() == DeviceType::kVirtualMachine)
            {
                const Json nic_status = VmService::CollectNicStatus();
                body["nic_status"] = nic_status;
                EnqueueNicStatusSave(database_queue, config.GetAgentName(), nic_status);
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
