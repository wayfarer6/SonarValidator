#include <atomic>
#include <chrono>
#include <csignal>
#include <filesystem>
#include <iostream>
#include <thread>

#include <nlohmann/json.hpp>
#include <sqlite3.h>

#include "components/backend_communication/network.hpp"
#include "components/policy/policy_receiver.hpp"
#include "database/database_service.hpp"
#include "module/configuration_module/prober_config.hpp"
#include "module/initializing_module/init.hpp"
#include "module/management_module/management_service.hpp"
#include "module/offline_export_module/offline_export.hpp"
#include "module/telemetry_module/telemetry_monitor.hpp"
#include "module/telemetry_module/telemetry_service.hpp"
#include "utils/cli_options.hpp"
#include "utils/path_manager.hpp"

#include "workers/DatbaseWorker.hpp"
#include "workers/ManagementWorker.hpp"
#include "workers/TelemetryWorker.hpp"

namespace fs = std::filesystem;
using Json = nlohmann::json;

// 프로세스 전체의 실행 플래그입니다. SIGINT/SIGTERM이 오면 false로 바뀝니다.
std::atomic<bool> g_running{true};

// 종료 시그널 핸들러: 실행 플래그만 내리고, 각 스레드는 stop_token으로 정리됩니다.
void signalHandler(int signum)
{
    (void)signum;
    g_running = false;
}


int main(int argc, char **argv)
{
    // SIGINT(Ctrl+C)/SIGTERM을 잡아 graceful shutdown을 시작합니다.
    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);

    // ------------------------------------------------------------ CLI 옵션 --
    // 설정/DB 를 준비하기 전에 --help 로 끝낼 수 있어야 합니다.
    const CliOptions options = CliOptions{}.ParseArgs(argc, argv);
    if (options.show_help)
    {
        options.printUsage(argc > 0 ? argv[0] : nullptr);
        return 0;
    }

    // -------------------------------------------------- 경로/설정/DB 준비 --
    const fs::path data_directory = PathManager::ResolveDataDirectory();
    const fs::path config_file_path = data_directory / "settings.conf";
    const fs::path sqlite_db_path = data_directory / "prober_db.sqlite";
    const fs::path sqlite_template_path = PathManager::ResolveTemplatePath();

    // 오프라인 폴백 디렉터리.
    //   --export-dir > SONAR_OFFLINE_DIR > <데이터 디렉터리>/offline
    //   (--export-offline 또는 --export-once 일 때만 실제로 쓰입니다)
    const fs::path offline_directory =
        (options.offline_only || options.export_once)
            ? fs::path(offline::ResolveExportDirectory(data_directory.string(),
                                                       options.export_dir))
            : fs::path(options.export_dir);

    // 임시 기본값으로 config를 만든 뒤, PrepareRuntime에서 실제 값으로 채웁니다.
    ProberConfig config("", "", "", "", DeviceType::kSwitch, "", 0, "", 0);

    DbHandle database(nullptr);
    if (!AppInitializer::PrepareRuntime(
            data_directory,
            config_file_path,
            sqlite_db_path,
            sqlite_template_path,
            config,
            database))
    {
        std::cerr << "Runtime initialization failed\n";
        std::cerr << "  data dir : " << data_directory << '\n';
        std::cerr << "  template : " << sqlite_template_path << '\n';
        std::cerr << "  (SONAR_DATA_DIR / SONAR_TEMPLATE_PATH 로 경로를 지정할 수 있습니다)\n";
        return 1;
    }

    // ------------------------------------------- --export-once (한 번만) --
    // 서버가 없는 장비에서 설정만 뽑아 갈 때 쓰는 경로입니다.
    if (options.export_once)
    {
        ManagementService export_service(
            config.GetServerIpv4(),
            static_cast<int>(config.GetServerPort()),
            "/api/v1/management");

        const Json snapshot = TelemetryMonitor::CollectSnapshotDocument(config, export_service);

        const std::string agent_id =
            config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();

        return offline::ExportOnce(offline_directory, agent_id, snapshot, options.export_stdout);
    }

    // --------------------------------------------------- 워커 스레드 기동 --
    //  큐/스레드는 반드시 이 순서로 만든다.
    //   1) DB 큐         (스레드들이 참조한다)
    //   2) jthread 3개   (큐/DB/설정을 std::ref 로 넘긴다)
    DatabaseQueue database_queue;  // DB 큐(뮤텍스 + 조건 변수)

    std::jthread telemetry_thread(TelemetryWorker,
                                  std::ref(config),
                                  std::ref(database_queue),
                                  std::cref(offline_directory),
                                  options.offline_only);
    std::jthread management_thread(ManagementWorker, std::ref(config));
    std::jthread database_thread(DatabaseWorker,
                                 std::ref(database),
                                 std::ref(database_queue));

    // 종료 시그널이 올 때까지 메인 스레드는 대기합니다.
    while (g_running.load())
    {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    // graceful shutdown: 큐를 닫고 각 스레드에 정지를 요청합니다.
    database_queue.Close();
    telemetry_thread.request_stop();
    management_thread.request_stop();
    database_thread.request_stop();

    std::cout << "[INFO] Clean shutdown complete.\n";
    return 0;
}
