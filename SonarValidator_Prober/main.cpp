#include <atomic>
#include <chrono>
#include <csignal>
#include <cstdio>
#include <cstdlib>
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
#include "offline/offline_export.hpp"
#include "policy/policy_receiver.hpp"
#include "prober_config.hpp"
#include "telemetry/telemetry_monitor.hpp"
#include "telemetry/telemetry_service.hpp"
#include <nlohmann/json.hpp>

namespace fs = std::filesystem;

namespace
{
    // 기본 데이터 디렉터리(설치 시 systemd 로 root 권한으로 실행되는 것을 전제).
    const fs::path kDefaultDataDirectory = "/var/lib/sonar_validator_prober";

    // CLI 로 넘어온 실행 옵션입니다.
    //
    // 프로버는 기본이 "상시 실행" 이지만, 서버에 닿지 않는 장비에서는
    // 설정만 뽑아 파일로 가져가야 합니다. 그 경로를 CLI 로 노출합니다.
    //   --export-offline        서버 전송을 시도하지 않고 스냅샷 파일만 남김
    //   --export-dir <경로>      스냅샷 저장 위치 (기본: <데이터>/offline)
    //   --export-once           한 번만 수집하고 종료
    //   --export-stdout         스냅샷을 표준출력으로 인쇄 (파일 없이 복사/붙여넣기용)
    struct CliOptions
    {
        bool offline_only = false;
        bool export_once = false;
        bool export_stdout = false;
        std::string export_dir{};
        bool show_help = false;
    };

    // 기본 SQLite 템플릿 경로.
    const fs::path kDefaultTemplatePath =
        "/etc/sonar_validator_prober/sqlite_template.sqlite";

    // 데이터 디렉터리를 결정합니다.
    //  1) SONAR_DATA_DIR 환경변수
    //  2) 시스템 기본 경로(/var/lib/...)
    //  root 가 아닌 환경(예: vEOS bash, 사용자 홈 실행)에서는
    //  시스템 경로를 만들 수 없으므로 실패 시 실행 파일 옆으로 폴백합니다.
    fs::path ResolveDataDirectory()
    {
        if (const char* from_env = std::getenv("SONAR_DATA_DIR"))
        {
            if (from_env[0] != '\0')
            {
                return fs::path(from_env);
            }
        }
        return kDefaultDataDirectory;
    }

    // SQLite 템플릿 경로를 결정합니다.
    //  SONAR_TEMPLATE_PATH 가 있으면 우선 사용하고, 없으면
    //    /etc/... → 실행 파일 디렉터리 순으로 찾습니다.
    fs::path ResolveTemplatePath()
    {
        if (const char* from_env = std::getenv("SONAR_TEMPLATE_PATH"))
        {
            if (from_env[0] != '\0')
            {
                return fs::path(from_env);
            }
        }

        std::error_code error;
        if (fs::exists(kDefaultTemplatePath, error))
        {
            return kDefaultTemplatePath;
        }

        // 실행 파일 옆에 두는 배포 형태(예: /mnt/flash/sonar_validator/)를 지원합니다.
        std::error_code self_error;
        const fs::path self = fs::read_symlink("/proc/self/exe", self_error);
        if (!self_error)
        {
            const fs::path candidate = self.parent_path() / "sqlite_template.sqlite";
            if (fs::exists(candidate, error))
            {
                return candidate;
            }
        }
        return kDefaultTemplatePath;
    }

    // 사용법을 출력합니다. (오프라인 export 옵션이 추가되어 도움말이 필요해졌습니다)
    void PrintUsage(const char *program)
    {
        std::cout
            << "SonarValidator Prober\n\n"
            << "사용법: " << (program == nullptr ? "sonar_validator_prober" : program) << " [옵션]\n\n"
            << "옵션:\n"
            << "  --export-offline       서버로 보내지 않고 스냅샷 JSON 파일만 남깁니다.\n"
            << "                         (관리 서버에 연결할 수 없는 장비용)\n"
            << "  --export-dir <경로>    스냅샷 저장 위치. 기본값은 <데이터 디렉터리>/offline\n"
            << "  --export-once          한 번만 수집하고 종료합니다. (--export-offline 과 함께 쓰면\n"
            << "                         즉시 파일 하나를 만들고 끝납니다)\n"
            << "  --export-stdout        스냅샷 JSON 을 표준출력으로 인쇄합니다. (파일 없이 복사용)\n"
            << "  --help, -h             이 도움말을 출력합니다.\n\n"
            << "환경변수:\n"
            << "  SONAR_DATA_DIR         데이터(DB/설정) 디렉터리\n"
            << "  SONAR_TEMPLATE_PATH    SQLite 템플릿 경로\n"
            << "  SONAR_OFFLINE_DIR      스냅샷 저장 디렉터리 (--export-dir 보다 우선순위 낮음)\n";
    }

    // 명령줄 인자를 해석합니다. 모르는 인자는 무시하고 경고만 남깁니다.
    // (설치 스크립트가 옵션을 덧붙여 실행해도 프로버가 죽지 않게 하기 위함)
    CliOptions ParseArgs(int argc, char **argv)
    {
        CliOptions options;
        for (int index = 1; index < argc; ++index)
        {
            const std::string argument = argv[index] == nullptr ? "" : argv[index];

            if (argument == "--export-offline")
            {
                options.offline_only = true;
            }
            else if (argument == "--export-once")
            {
                options.export_once = true;
            }
            else if (argument == "--export-stdout")
            {
                options.export_stdout = true;
            }
            else if (argument == "--export-dir")
            {
                if (index + 1 < argc && argv[index + 1] != nullptr)
                {
                    options.export_dir = argv[++index];
                }
                else
                {
                    std::cerr << "[WARN] --export-dir 에 경로가 없습니다. 기본 경로를 사용합니다.\n";
                }
            }
            else if (argument == "--help" || argument == "-h")
            {
                options.show_help = true;
            }
            else
            {
                std::cerr << "[WARN] 알 수 없는 인자: " << argument << '\n';
            }
        }
        return options;
    }
}

// 프로세스 전체의 실행 플래그입니다. SIGINT/SIGTERM이 오면 false로 바뀝니다.
std::atomic<bool> g_running{true};

// 종료 시그널 핸들러: 실행 플래그만 내리고, 각 스레드는 stop_token으로 정리됩니다.
void signalHandler(int signum)
{
    (void)signum;
    g_running = false;
}

// 텔레메트리 스레드 진입점입니다. 실제 루프는 TelemetryMonitor가 담당합니다.
// (조회 명령 실행 + 파싱 → 서버 전송 + DB 큐 저장, 기본 30초 간격)
void TelemetryWorker(std::stop_token stop_token,
                     const ProberConfig &config,
                     DatabaseQueue &database_queue,
                     const fs::path &offline_directory,
                     bool offline_only)
{
    // 장치 조회 명령 실행에 쓰는 서비스입니다.
    // 관리 스레드와 정책 적용은 각자 별도 인스턴스를 씁니다(영속 CLI 세션 공유 방지).
    ManagementService management_service(
        config.GetServerIpv4(),
        static_cast<int>(config.GetServerPort()),
        "/api/v1/management");

    TelemetryMonitor monitor;

    // 오프라인 폴백 설정입니다. 디렉터리가 비어 있으면 기능이 꺼집니다.
    monitor.SetOfflineExportDirectory(offline_directory.empty() ? std::string{} : offline_directory.string());
    monitor.SetOfflineOnly(offline_only);

    monitor.Run(stop_token, config, database_queue, management_service);
}

// 관리 스레드: 서버로부터 정책을 받아 장치에 적용합니다.
void ManagementWorker(std::stop_token stop_token, const ProberConfig &config)
{
    ManagementService management_service(
        config.GetServerIpv4(),
        static_cast<int>(config.GetServerPort()),
        "/api/v1/management");

    // 에이전트 식별자: 설정의 AGENT_NAME을 우선 사용합니다.
    // (AGENT_ID 는 아직 설정에 없으므로 비어 있을 수 있습니다.)
    const std::string agent_id =
        config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();
    management_service.SetAgentId(agent_id);

    while (!stop_token.stop_requested())
    {
        // 서버에 policy-request 봉투를 보내고 같은 correlation_id 의 응답을 기다립니다.
        const Json policy = management_service.fetchPolicy(config.GetDeviceType(), agent_id);

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

// (현재 미사용) 주기적인 상태 모니터링 스레드 자리입니다.
void MonitorWorker(std::stop_token stop_token, DatabaseQueue &database_queue)
{
    (void)database_queue;
    while (!stop_token.stop_requested())
    {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}

// 데이터베이스 스레드: 큐에서 태스크를 하나씩 꺼내 순차 실행합니다.
// sqlite3 핸들을 오직 이 스레드만 만지므로 동시 접근 문제가 없습니다.
void DatabaseWorker(
    std::stop_token stop_token,
    DbHandle &db_handle,
    DatabaseQueue &database_queue)
{
    if (db_handle == nullptr)
    {
        return;
    }

    DatabaseTask task;
    while (database_queue.Pop(stop_token, task))
    {
        try
        {
            // 태스크의 람다를 실행하고 결과를 promise에 담아 호출자에게 돌려줍니다.
            task.result.set_value(task.execute(db_handle));
        }
        catch (...)
        {
            // 실행 중 예외가 나면 future.get() 쪽에 예외로 전달합니다.
            task.result.set_exception(std::current_exception());
        }
    }
}

int main(int argc, char **argv)
{
    // SIGINT(Ctrl+C)/SIGTERM을 잡아 graceful shutdown을 시작합니다.
    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);

    // 오프라인 export 옵션을 먼저 해석합니다.
    // (설정/DB 를 준비하기 전에 --help 로 끝낼 수 있어야 한다)
    const CliOptions options = ParseArgs(argc, argv);
    if (options.show_help)
    {
        PrintUsage(argc > 0 ? argv[0] : nullptr);
        return 0;
    }

    // 데이터 디렉터리와 템플릿 경로를 결정합니다.
    //  환경변수로 오버라이드할 수 있어 root 가 아닌 환경(vEOS bash 등)에서도 실행됩니다.
    const fs::path data_directory = ResolveDataDirectory();
    const fs::path config_file_path = data_directory / "settings.conf";
    const fs::path sqlite_db_path = data_directory / "prober_db.sqlite";
    const fs::path sqlite_template_path = ResolveTemplatePath();

    // 임시 기본값으로 config를 만든 뒤, PrepareRuntime에서 실제 값으로 채웁니다.
    ProberConfig config(
        "","","","",DeviceType::kSwitch,"",
        0, "", 0);

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

    // 오프라인 폴백 디렉터리를 결정합니다.
    //  --export-dir > SONAR_OFFLINE_DIR > <데이터 디렉터리>/offline
    //  (--export-offline 또는 --export-once 일 때만 실제로 쓰입니다)
    const fs::path offline_directory =
        (options.offline_only || options.export_once)
            ? fs::path(offline::ResolveExportDirectory(data_directory.string(), options.export_dir))
            : fs::path(options.export_dir);

    // --export-once: 수집을 한 번만 하고 결과를 파일/표준출력으로 남긴 뒤 종료합니다.
    // 서버가 없는 장비에서 설정만 뽑아 가져갈 때 쓰는 경로입니다.
    if (options.export_once)
    {
        ManagementService export_service(
            config.GetServerIpv4(),
            static_cast<int>(config.GetServerPort()),
            "/api/v1/management");

        const Json snapshot = TelemetryMonitor::CollectSnapshotDocument(config, export_service);

        if (options.export_stdout)
        {
            // 표준출력으로만 인쇄합니다. (파일 없이 복사/붙여넣기 → 프론트엔드 업로드)
            std::cout << snapshot.dump(2) << '\n';
            return 0;
        }

        std::error_code directory_error;
        fs::create_directories(offline_directory, directory_error);

        const std::string agent_id =
            config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();
        const offline::ExportResult export_result = offline::ExportSnapshot(
            offline_directory.string(),
            agent_id,
            snapshot.value("collected_at", std::string{}),
            snapshot);

        if (!export_result.saved)
        {
            std::cerr << "[ERROR] 스냅샷을 저장하지 못했습니다: " << export_result.message << '\n';
            std::cerr << "        (--export-dir 또는 SONAR_OFFLINE_DIR 로 경로를 지정하세요)\n";
            return 1;
        }

        std::cout << "[INFO] 스냅샷을 저장했습니다: " << export_result.path << '\n';
        std::cout << "       이 파일을 SonarValidator 프론트엔드의 "
                     "'Import Offline Prober Data' 카드에 끌어다 놓으세요.\n";
        return 0;
    }

    // 병렬로 돌아갈 스레드들을 생성합니다.
    DatabaseQueue database_queue;  // DB 큐(뮤텍스 + 조건 변수)
    std::jthread telemetry_thread(TelemetryWorker, std::ref(config), std::ref(database_queue),
                                 std::cref(offline_directory), options.offline_only);
    std::jthread management_thread(ManagementWorker, std::ref(config));
    std::jthread database_thread(DatabaseWorker, std::ref(database), std::ref(database_queue));

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
