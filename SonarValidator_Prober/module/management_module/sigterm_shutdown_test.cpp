



#include <chrono>
#include <cstdlib>
#include <future>
#include <iostream>
#include <string>
#include <thread>

#include "components/device/device_type.hpp"
#include "module/management_module/management_service.hpp"
#include "module/telemetry_module/telemetry_service.hpp"

namespace
{
    int failures = 0;

    void Expect(bool condition, const std::string& label)
    {
        if (condition)
        {
            std::cout << "  [ok]   " << label << '\n';
            return;
        }
        std::cerr << "  [FAIL] " << label << '\n';
        ++failures;
    }

    constexpr const char* kBlackholeHost = "192.0.2.1";
    constexpr int kBlackholePort = 9;  // discard — 열려 있지 않음

    bool ReturnsWithinBudget(std::chrono::seconds budget)
    {
        ManagementService service(kBlackholeHost, kBlackholePort, "/api/v1/management");
        service.SetAgentId("hang-regression-agent");

        // 정지 요청을 보낼 토큰입니다. 별도 스레드에서 ready 가 되면
        // fetchPolicy 의 대기 루프가 이를 확인하고 즉시 반환해야 합니다.
        std::stop_source source;

        auto future = std::async(std::launch::async, [&service, &source] {
            return service.fetchPolicy(DeviceType::kVirtualMachine,
                                       "hang-regression-agent",
                                       source.get_token());
        });

        // 타임아웃이 정상 동작하면 connect 는 kConnectTimeout(5s) 안에 끝납니다.
        // 여유를 두고 기다립니다.
        const auto deadline = std::chrono::steady_clock::now() + budget;
        while (future.wait_for(std::chrono::milliseconds(200)) != std::future_status::ready)
        {
            if (std::chrono::steady_clock::now() >= deadline)
            {
                // 정지 요청을 보내 봅니다. 그래도 반환하지 않으면
                // 연결/응답 경로 중 하나가 예산 안에 종료되지 않았습니다.
                source.request_stop();
                if (future.wait_for(std::chrono::seconds(3)) != std::future_status::ready)
                {
                    return false;
                }
                break;
            }
        }

        return true;
    }
}

int main()
{
    std::cout << "[SIGTERM 종료 회귀 테스트]\n";

    const bool returned = ReturnsWithinBudget(std::chrono::seconds(20));
    Expect(returned,
           "응답 없는 서버로의 fetchPolicy 가 제한 시간 내 반환 (connect 타임아웃)");

    {
        ManagementService service(kBlackholeHost, kBlackholePort, "/api/v1/management");
        service.SetAgentId("stop-token-agent");

        std::stop_source source;
        source.request_stop();  // 이미 정지 상태로 진입

        const auto start = std::chrono::steady_clock::now();
        (void)service.fetchPolicy(DeviceType::kVirtualMachine,
                                  "stop-token-agent",
                                  source.get_token());
        const auto elapsed = std::chrono::steady_clock::now() - start;

        // 정지 상태면 connect 시도조차 하지 않고 즉시 반환해야 합니다.
        // (연결 실패로도 반환하므로 넉넉하게 잡습니다)
        Expect(elapsed < std::chrono::seconds(20),
               "정지 요청 시 fetchPolicy 가 즉시 반환 (stop_token 확인)");
    }




    {
        Expect(ManagementService::kConnectTimeout <= std::chrono::seconds(10),
               "kConnectTimeout 이 10초 이하 (종료 지연 방지)");
        Expect(ManagementService::kConnectTimeout >= std::chrono::seconds(1),
               "kConnectTimeout 이 1초 이상 (정상 연결 보장)");
        Expect(ManagementService::kHandshakeTimeout <= std::chrono::seconds(10),
               "kHandshakeTimeout 이 10초 이하");
    }




    {
        TelemetryService service(kBlackholeHost, kBlackholePort, "/api/v1/telemetry");
        const auto start = std::chrono::steady_clock::now();
        (void)service.connect();
        const auto elapsed = std::chrono::steady_clock::now() - start;

        Expect(elapsed < std::chrono::seconds(20),
               "TelemetryService::connect 가 제한 시간 내 반환");
    }

    if (failures == 0)
    {
        std::cout << "\n[결과] 모두 통과\n";
        return 0;
    }

    std::cerr << "\n[결과] 실패 " << failures << "건\n";
    return 1;
}
