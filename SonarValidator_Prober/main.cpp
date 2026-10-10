#include <atomic>
#include <chrono>
#include <csignal>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <optional>
#include <thread>
#include <vector>

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
#include "workers/TerminalAgentWorker.hpp"
#include "workers/TelemetryWorker.hpp"

namespace fs = std::filesystem;
using Json = nlohmann::json;

// 프로세스 전체의 실행 플래그입니다. SIGINT/SIGTERM이 오면 false로 바뀝니다.
std::atomic<bool> g_running{true};

#ifdef SONAR_DEBUG_BUILD
std::optional<fs::path> FindDebugProfile()
{
    std::vector<fs::path> search_paths;
    std::error_code error;
    search_paths.push_back(fs::current_path(error));
    if (error)
    {
        error.clear();
    }

    const fs::path executable = fs::read_symlink("/proc/self/exe", error);
    if (!error)
    {
        search_paths.push_back(executable.parent_path());
    }

    for (fs::path directory : search_paths)
    {
        while (!directory.empty())
        {
            const fs::path profile = directory / ".vscode" / "debug-default.conf";
            std::ifstream input(profile);
            std::string first_line;
            if (input && std::getline(input, first_line) && first_line == "#DEBUG")
            {
                return profile;
            }

            const fs::path parent = directory.parent_path();
            if (parent == directory)
            {
                break;
            }
            directory = parent;
        }
    }
    return std::nullopt;
}

bool SetDebugEnvironmentDefault(const char* name, const fs::path& value)
{
    if (std::getenv(name) != nullptr)
    {
        return true;
    }
    if (setenv(name, value.c_str(), 0) == 0)
    {
        return true;
    }

    std::cerr << "[ERROR] Debug 환경변수 설정 실패: " << name << '\n';
    return false;
}

bool ApplyDebugDefaults()
{
    const std::optional<fs::path> profile = FindDebugProfile();
    if (!profile)
    {
        return true;
    }

    const fs::path repository_root = profile->parent_path().parent_path();
    const fs::path executable = fs::read_symlink("/proc/self/exe");
    const fs::path data_directory = executable.parent_path() / "data";
    const fs::path template_path =
        repository_root / "SonarValidator_Prober/Installer/default_template.sqlite";

    if (!SetDebugEnvironmentDefault("SONAR_CONFIG_PATH", *profile) ||
        !SetDebugEnvironmentDefault("SONAR_DATA_DIR", data_directory) ||
        !SetDebugEnvironmentDefault("SONAR_TEMPLATE_PATH", template_path))
    {
        return false;
    }

    std::cout << "[DEBUG] #DEBUG profile: " << *profile << '\n';
    return true;
}
#endif

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

#ifdef SONAR_DEBUG_BUILD
    if (!ApplyDebugDefaults())
    {
        return 1;
    }
#endif

    // -------------------------------------------------- 경로/설정/DB 준비 --
    const fs::path data_directory = PathManager::ResolveDataDirectory();
    const fs::path config_file_path = data_directory / "settings.conf";
    const fs::path sqlite_db_path = data_directory / "prober_db.sqlite";
    const fs::path sqlite_template_path = PathManager::ResolveTemplatePath();

    // 오프라인 export 경로.
    //   --export-dir > SONAR_OFFLINE_DIR > --export-offline 의 현재 작업 디렉터리
    //   > <데이터 디렉터리>/offline (기존 --export-once 기본값)
    const fs::path offline_directory =
        (options.offline_only || options.export_once)
            ? fs::path(offline::ResolveExportDirectory(data_directory.string(),
                                                       options.export_dir,
                                                       options.offline_only
                                                           ? fs::current_path().string()
                                                           : ""))
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
        std::cerr << "  (SONAR_CONFIG_PATH / SONAR_DATA_DIR / SONAR_TEMPLATE_PATH 로 경로를 지정할 수 있습니다)\n";
        return 1;
    }

    std::cerr << "[CONFIG] agent=" << config.GetAgentName()
              << " server=" << config.GetServerIpv4() << ':' << config.GetServerPort()
              << " product=" << config.GetProductName() << '\n';

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
    //   2) jthread 4개   (큐/DB/설정을 std::ref 로 넘긴다)
    DatabaseQueue database_queue;  // DB 큐(뮤텍스 + 조건 변수)

    std::jthread telemetry_thread(TelemetryWorker,
                                  std::ref(config),
                                  std::ref(database_queue),
                                  std::cref(offline_directory),
                                  options.offline_only);
    std::jthread management_thread(ManagementWorker, std::ref(config));
    std::jthread terminal_thread(TerminalAgentWorker, std::ref(config));
    std::jthread database_thread(DatabaseWorker,
                                 std::ref(database),
                                 std::ref(database_queue));

    while (g_running.load())
    {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    telemetry_thread.request_stop();
    management_thread.request_stop();
    terminal_thread.request_stop();

    // ----------------------------------------------------- 종료 예산(watchdog) --
    // 각 워커는 stop_token 을 존중하지만, 하위 프로세스(dohost/FastCli)나
    // 소켓이 비정상 상태면 join 이 오래 걸릴 수 있습니다. 그동안 main 은
    // join 에서 멈춰 있어 systemd 는 "종료되지 않는" 것으로 보고
    // TimeoutStopSec 후 SIGKILL 합니다. 그래서 예산을 두고, 넘기면 강제로
    // 종료합니다. (_Exit 는 정적 소멸자를 건너뛰어 그쪽 블록도 피합니다.)
    std::atomic<bool> shutdown_complete{false};
    std::thread shutdown_watchdog([&shutdown_complete]() {
        // 정지 요청 후 이 시간이 지나면 강제 종료합니다. 서비스 파일의
        // TimeoutStopSec(15s)보다 짧아야 SIGKILL 을 피합니다.
        static constexpr std::chrono::seconds kBudget{8};
        const auto deadline = std::chrono::steady_clock::now() + kBudget;
        while (std::chrono::steady_clock::now() < deadline)
        {
            if (shutdown_complete.load())
            {
                return;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
        if (!shutdown_complete.load())
        {
            std::cerr << "[SHUTDOWN] worker did not stop within " << kBudget.count()
                      << "s; forcing exit\n";
            std::cerr.flush();
            std::_Exit(0);
        }
    });

    telemetry_thread.join();
    management_thread.join();
    terminal_thread.join();
    database_queue.Close();
    database_thread.join();

    shutdown_complete.store(true);
    if (shutdown_watchdog.joinable())
    {
        shutdown_watchdog.join();
    }

    std::cout << "[INFO] Clean shutdown complete.\n";
    return 0;
}
