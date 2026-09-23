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

    // 초기화 기능 호출

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
