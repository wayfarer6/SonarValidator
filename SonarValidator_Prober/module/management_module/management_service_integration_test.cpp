#include <chrono>
#include <cstdlib>
#include <iostream>
#include <string>
#include <thread>

#include "components/device/device_type.hpp"
#include "components/backend_communication/envelope.hpp"
#include "module/management_module/management_service.hpp"

// 실제 WebSocket 서버(mock 또는 Spring Boot)를 상대로 봉투 왕복을 검증합니다.
// 서버가 떠 있지 않으면 SKIP(종료 코드 0)으로 처리해 CI를 막지 않습니다.
//
// 실행 예:
//   cd Agent_Test/mock_API && npm run envelope     # 별도 터미널
//   ./management_service_integration_test
//
// 연결 유지 모드(서버 -> 에이전트 푸시 검증용):
//   ./management_service_integration_test --hold 30
//   위 실행 중에 다른 터미널에서:
//     curl -s localhost:3000/api/v1/agents
//     curl -s -X POST localhost:3000/api/v1/agents/<agent_id>/push \
//          -H 'Content-Type: application/json' -d '{"monitor_interval":15}'
//
// 환경 변수:
//   PROBER_TEST_HOST (기본 localhost)
//   PROBER_TEST_PORT (기본 3000)

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

    std::string EnvOr(const char* key, const char* fallback)
    {
        const char* value = std::getenv(key);
        return (value == nullptr || *value == '\0') ? fallback : value;
    }

    /**
     * 연결을 유지한 채 서버 푸시(command 봉투)를 기다립니다.
     * 실제 운영에서 TelemetryMonitor 가 하는 일과 같은 수신 루프입니다.
     */
    void HoldAndListen(ManagementService& service, int seconds)
    {
        const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(seconds);
        int received = 0;

        std::cout << "  [hold] listening for server push for " << seconds << "s...\n";

        while (std::chrono::steady_clock::now() < deadline)
        {
            std::string raw;
            if (!service.TryReceive(raw, std::chrono::milliseconds(500)))
            {
                continue;
            }

            Json message;
            try
            {
                message = Json::parse(raw);
            }
            catch (const std::exception&)
            {
                std::cerr << "  [hold] non-JSON frame: " << raw << '\n';
                continue;
            }

            ++received;
            std::cout << "  [hold] <- type=" << envelope::Type(message)
                      << " payload=" << envelope::Payload(message).dump() << '\n';

            if (envelope::IsType(message, envelope::kCommand))
            {
                const Json& payload = envelope::Payload(message);
                if (payload.contains("monitor_interval"))
                {
                    std::cout << "  [hold] would set monitor interval to "
                              << payload["monitor_interval"].get<int>() << "s\n";
                }
            }
        }

        std::cout << "  [hold] received " << received << " push envelope(s)\n";
    }
}

int main(int argc, char** argv)
{
    const std::string host = EnvOr("PROBER_TEST_HOST", "localhost");
    const int port = std::stoi(EnvOr("PROBER_TEST_PORT", "3000"));

    // --hold <초>: 검증 후에도 연결을 유지해 서버 푸시 경로를 수동 확인합니다.
    int hold_seconds = 0;
    for (int i = 1; i < argc; ++i)
    {
        if (std::string(argv[i]) == "--hold" && i + 1 < argc)
        {
            hold_seconds = std::stoi(argv[i + 1]);
        }
    }

    std::cout << "management_service_integration_test host=" << host << " port=" << port << '\n';


    ManagementService service(host, port, "/api/v1/management");

    if (!service.connect())
    {
        std::cout << "  [skip] server not reachable at " << host << ':' << port << '\n';
        return 0;
    }

    service.SetAgentId("integration-vm-01");

    // 1) fetchPolicy 는 hello 로 세션을 등록한 뒤 policy-request/policy-response 를 왕복합니다.
    //    내부에서 correlation_id 로 응답을 매칭하므로, 응답이 돌아왔다면 그 자체가 검증입니다.
    const Json policy = service.fetchPolicy(DeviceType::kVirtualMachine, "integration-vm-01");

    if (policy.is_null() || policy.is_boolean() || !policy.is_object())
    {
        std::cerr << "  [FAIL] policy response not received\n";
        return 1;
    }

    Expect(policy.is_object(), "policy-response payload is an object");
    Expect(policy.contains("policy_id"), "policy has policy_id");
    Expect(policy.contains("device_type"), "policy has device_type");
    Expect(policy.contains("policies"), "policy has policies array");
    Expect(policy.value("device_id", std::string()) == "integration-vm-01", "device_id echoed back");

    const std::string device_type = policy.value("device_type", std::string());
    Expect(device_type == "VM" || device_type == "SWITCH" || device_type == "ROUTER" ||
               device_type == "FIREWALL",
           "device_type is a known token");

    if (policy.contains("policies") && policy["policies"].is_array())
    {
        for (const auto& rule : policy["policies"])
        {
            // docs/Agent 규칙: 스칼라는 배열로 감쌉
            const auto command = rule.find("command");
            Expect(command == rule.end() || command->is_array(),
                   "policy rule scalar wrapped in array");
        }
    }

    // 2) ack 는 일방향이라 응답이 없습니다. 여기서는 전송 성공만 확인합니다.
    Expect(service.ReportPolicyApplied(DeviceType::kVirtualMachine, "integration-vm-01",
                                       policy.value("policy_id", std::string()), true),
           "ack envelope sent");

    // 3) 두 번째 요청도 성공해야 합니다. (연결 재사용 + correlation_id 증가)
    const Json second = service.fetchPolicy(DeviceType::kVirtualMachine, "integration-vm-01");
    Expect(second.is_object(), "second policy request reuses the connection");

    // 4) 서버 -> 에이전트 푸시 경로 확인 (수동 트리거 대기)
    if (hold_seconds > 0)
    {
        HoldAndListen(service, hold_seconds);
    }

    if (failures > 0)
    {
        std::cerr << "management_service_integration_test FAILED (" << failures << ")\n";
        return 1;
    }

    std::cout << "management_service_integration_test passed\n";
    return 0;
}
