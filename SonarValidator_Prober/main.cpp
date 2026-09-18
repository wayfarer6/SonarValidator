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
                     DatabaseQueue &database_queue)
{
    // 장치 조회 명령 실행에 쓰는 서비스입니다.
    // 관리 스레드와 정책 적용은 각자 별도 인스턴스를 씁니다(영속 CLI 세션 공유 방지).
    ManagementService management_service(
        config.GetServerIpv4(),
        static_cast<int>(config.GetServerPort()),
        "/api/v1/management");

    TelemetryMonitor monitor;
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

int main()
{
    // SIGINT(Ctrl+C)/SIGTERM을 잡아 graceful shutdown을 시작합니다.
    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);

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

    // 병렬로 돌아갈 스레드들을 생성합니다.
    DatabaseQueue database_queue;  // DB 큐(뮤텍스 + 조건 변수)
    std::jthread telemetry_thread(TelemetryWorker, std::ref(config), std::ref(database_queue));
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
