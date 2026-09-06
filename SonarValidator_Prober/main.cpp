#include <atomic>
#include <chrono>
#include <csignal>
#include <cstdio>
#include <ctime>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <memory>
#include <random>
#include <sstream>
#include <thread>

#include <sqlite3.h>

#include "database/database_service.hpp"
#include "init.hpp"
#include "management_service.hpp"
#include "policy/policy_receiver.hpp"
#include "prober_config.hpp"
#include "telemetry/telemetry_service.hpp"
#include "vm/vm_service.hpp"
#include <nlohmann/json.hpp>

namespace fs = std::filesystem;

namespace
{
    const fs::path kDataDirectory = "/var/lib/sonar_validator_prober";
}

std::atomic<bool> g_running{true};

void signalHandler(int signum)
{
    (void)signum;
    g_running = false;
}

void TelemetryWorker(std::stop_token stop_token, const ProberConfig &config)
{
    /*
    try
    {
        TelemetryService telemetry_service(
            config.GetServerIpv4(),
            static_cast<int>(config.GetServerPort()),
            "/api/telemetry");

        while (!stop_token.stop_requested())
        {
            std::string request =
                "{\"agent\":\"" + config.GetAgentName() +
                "\",\"kernel\":\"" + config.GetKernelName() +
                "\"}";
            std::string target = "/api/telemetry";
            telemetry_service.sendRequest(request, target);
            std::this_thread::sleep_for(std::chrono::seconds(5));
        }
    }
    catch (const std::exception &ex)
    {
        std::cerr << "[WARN] Telemetry worker exception: " << ex.what() << '\n';
    }

    */

    try
    {
        TelemetryService telemetry_service(
            config.GetServerIpv4(),
            static_cast<int>(config.GetServerPort()), // server port 3000 (test)
            "/api/v1/telemetry");
        while (!stop_token.stop_requested())
        {
            Json telemetry;
            telemetry["agent"] = config.GetAgentName();
            telemetry["kernel"] = config.GetKernelName();

            // VM은 NIC/연결 상태를 함께 전송합니다. (제어는 스위치/라우터 담당)
            if (config.GetDeviceType() == DeviceType::kVirtualMachine)
            {
                telemetry["nic_status"] = VmService::CollectNicStatus();
            }

            std::string request = telemetry.dump();
            std::string target = "/api/telemetry";
            telemetry_service.sendRequest(request, target);
            std::this_thread::sleep_for(std::chrono::seconds(5));
        }
    }
    catch (const std::exception &ex)
    {
        std::cerr << "[WARN] Telemetry worker exception: " << ex.what() << '\n';
    }
}

void ManagementWorker(std::stop_token stop_token, const ProberConfig &config)
{
    ManagementService management_service(
        config.GetServerIpv4(),
        static_cast<int>(config.GetServerPort()),
        "/api/v1/management");

    while (!stop_token.stop_requested())
    {
        // 서버와 연결 후 정책을 받아옵니다.
        const Json policy =
            management_service.fetchPolicy(config.GetDeviceType(), config.GetAgentId());

        if (policy.is_null() || policy.is_boolean())
        {
            std::this_thread::sleep_for(std::chrono::seconds(3));
            continue;
        }

        std::cout << "[INFO] Management worker received policy: " << policy.dump() << '\n';

        // 장치 유형(DeviceType)별로 정책 처리 함수를 분기합니다.
        ReceivePolicy(config, management_service, policy);

        std::this_thread::sleep_for(std::chrono::seconds(3));
    }
}

void MonitorWorker(std::stop_token stop_token, DatabaseQueue &database_queue)
{
    (void)database_queue;
    while (!stop_token.stop_requested())
    {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}

void DatabaseWorker(
    std::stop_token stop_token,
    DbHandle &db_handle,
    DatabaseQueue &database_queue)
{
    sqlite3 *database = db_handle.get();
    if (database == nullptr)
    {
        return;
    }

    DatabaseTask task;
    while (database_queue.Pop(stop_token, task))
    {
        try
        {
            task.result.set_value(task.execute(database));
        }
        catch (...)
        {
            task.result.set_exception(std::current_exception());
        }
    }
}

int main()
{
    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);

    const fs::path config_file_path =
        kDataDirectory / "settings.conf";
    const fs::path sqlite_db_path =
        kDataDirectory / "prober_db.sqlite";

    const fs::path sqlite_template_path =
        "/etc/sonar_validator_prober/sqlite_template.sqlite";

    ProberConfig config(
        "","","","",DeviceType::kSwitch,"",
        0, "", 0);

    DbHandle database(nullptr);
    if (!AppInitializer::PrepareRuntime(
            kDataDirectory,
            config_file_path,
            sqlite_db_path,
            sqlite_template_path,
            config,
            database))
    {
        std::cerr << "Runtime initialization failed\n";
        return 1;
    }

    // 스레드 생성
    DatabaseQueue database_queue;
    std::jthread telemetry_thread(TelemetryWorker, std::ref(config));
    std::jthread management_thread(ManagementWorker, std::ref(config));
    std::jthread database_thread(DatabaseWorker, std::ref(database), std::ref(database_queue));

    while (g_running.load())
    {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    database_queue.Close();
    telemetry_thread.request_stop();
    management_thread.request_stop();
    database_thread.request_stop();

    std::cout << "[INFO] Clean shutdown complete.\n";
    return 0;
}
