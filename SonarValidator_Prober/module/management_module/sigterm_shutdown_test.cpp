// ⚠️ 상태: 미완 (WIP) — SONAR-25
//
// 이 테스트는 **아직 CI 에 걸지 않았습니다.**
//
// ## 왜 미완인가
//
// 응답하지 않는 주소(192.0.2.1)로 connect 를 걸고 제한 시간 안에
// 반환하는지 보려 했는데, **테스트 자체가 hang** 합니다.
//
// 확인한 사실:
//   - 소켓이 SYN-SENT 에 남는다
//   - 주 스레드가 sigsuspend 에 머문다
//   - ConnectWithTimeout(non-blocking + poll) 을 적용해도 동일하다
//   - expires_after() 는 비동기 전용이라 동기 connect 에는 효과가 없다
//     (Beast 문서로 확인) — 이건 유효한 발견이다
//
// 아직 가리지 못한 것:
//   - hang 이 ConnectWithTimeout 안에서인지, 그 밖(예: 서비스 객체
//     생성자의 resolver/DNS) 에서인지 확인 필요
//   - 테스트를 gdb 로 잡을 때 ptrace_scope 제한으로 스택을 못 봤다
//     (/proc/sys/kernel/yama/ptrace_scope 완화 필요)
//
// ## 그래서 남긴 결론
//
// 이 테스트는 **주소를 블랙홀로 두고 hang 을 기대하는** 방식이 불안정하다.
// 대신 **실제 서버를 띄우고 SIGTERM 으로 종료되는지** 보는 통합 테스트가
// 더 현실적이다 (management_service_integration_test 가 이미 그 구조를 갖고 있다).
//
// 참고: docs/Agent/Appendix_SIGTERM_Hang_Analysis.md

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

    /**
     * 응답하지 않는 주소입니다. RFC 5737 의 TEST-NET-1 로,
     * 라우팅되지 않으므로 connect 가 즉시 실패하거나 타임아웃까지 대기합니다.
     */
    constexpr const char* kBlackholeHost = "192.0.2.1";
    constexpr int kBlackholePort = 9;  // discard — 열려 있지 않음

    /**
     * fetchPolicy 가 제한 시간 안에 반환하는지 확인합니다.
     *
     * 타임아웃(expires_after)이 없으면 이 future 는 영원히 ready 가 되지
     * 않습니다. 그 경우를 "미종료" 로 판정합니다.
     *
     * @param budget 허용 대기 시간 (연결 타임아웃 + 정지 확인 여유)
     * @return 제한 시간 내 반환했으면 true
     */
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
                // expires_after() 가 빠진 것입니다.
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

    // 1) 연결 타임아웃이 있으면 제한 시간 내 반환합니다.
    //
    //    수정 전: connect 에 타임아웃이 없어 이 항목이 실패(hang)했습니다.
    //    수정 후: kConnectTimeout(5s) 안에 반환합니다.
    const bool returned = ReturnsWithinBudget(std::chrono::seconds(20));
    Expect(returned,
           "응답 없는 서버로의 fetchPolicy 가 제한 시간 내 반환 (connect 타임아웃)");

    // 2) 정지 요청이 대기 루프에서 확인되는지 확인합니다.
    //
    //    fetchPolicy 는 stop_token 을 받아 루프마다 stop_requested() 를 봅니다.
    //    연결이 이미 실패한 뒤이므로 즉시 반환해야 합니다.
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

    // 3) 타임아웃 상수가 합리적 범위인지 확인합니다.
    //
    //    너무 크면 종료가 느려지고, 너무 작으면 정상 연결을 놓칩니다.
    {
        Expect(ManagementService::kConnectTimeout <= std::chrono::seconds(10),
               "kConnectTimeout 이 10초 이하 (종료 지연 방지)");
        Expect(ManagementService::kConnectTimeout >= std::chrono::seconds(1),
               "kConnectTimeout 이 1초 이상 (정상 연결 보장)");
        Expect(ManagementService::kHandshakeTimeout <= std::chrono::seconds(10),
               "kHandshakeTimeout 이 10초 이하");
    }

    // 4) 텔레메트리 서비스도 같은 보호를 갖는지 확인합니다.
    //
    //    두 서비스가 각각 별도 연결을 만들므로 양쪽 다 필요합니다.
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