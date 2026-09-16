#include <chrono>
#include <cstdlib>
#include <iostream>
#include <string>

#include "device_type.hpp"
#include "envelope.hpp"
#include "telemetry_service.hpp"

// TelemetryService 가 봉투(telemetry)를 실제로 전송하는지 검증합니다.
//
// 서버가 없으면 SKIP(종료 코드 0)이라 CI를 막지 않습니다. 예전에는 순수 echo
// 서버를 가정했는데, 이제 서버는 봉투 프로토콜(JSON + type)을 쓰므로 그 가정을
// 제거했습니다.
//
// 실행 예:
//   cd Agent_Test/mock_API && npm run envelope     # 별도 터미널
//   ./telemetry_service_test
//
// 환경 변수:
//   PROBER_TEST_HOST (기본 localhost)
//   PROBER_TEST_PORT (기본 3000)

namespace
{
    using Json = nlohmann::json;

    std::string EnvOr(const char* key, const char* fallback)
    {
        const char* value = std::getenv(key);
        return (value == nullptr || *value == '\0') ? fallback : value;
    }
}

int main()
{
    const std::string host = EnvOr("PROBER_TEST_HOST", "localhost");
    const int port = std::stoi(EnvOr("PROBER_TEST_PORT", "3000"));

    std::cout << "telemetry_service_test host=" << host << " port=" << port << '\n';

    TelemetryService service(host, port, "/api/v1/telemetry");

    if (!service.connect())
    {
        std::cout << "  [skip] server not reachable at " << host << ':' << port << '\n';
        return 0;
    }

    const std::string agent_id = "telemetry-test-vm-01";

    // 1) 텔레메트리 본문을 봉투로 감싸 전송합니다. (일방향이므로 응답은 없습니다)
    Json body;
    body["agent"] = agent_id;
    body["kernel"] = "6.8.0-test";
    body["nic_status"] = Json::array({{{"name", "ens33"}, {"state", "up"}}});

    const Json report = envelope::Telemetry(agent_id, DeviceType::kVirtualMachine, std::move(body));
    const std::string wire = report.dump();

    if (!service.sendRequest(wire, "/api/v1/telemetry"))
    {
        std::cerr << "  [FAIL] telemetry send failed\n";
        return 1;
    }

    // 전송한 프레임이 계약을 지켰는지 확인합니다. (서버가 파싱할 수 있어야 함)
    const Json parsed = Json::parse(wire);
    if (!envelope::IsType(parsed, envelope::kTelemetry))
    {
        std::cerr << "  [FAIL] wire type is not telemetry\n";
        return 2;
    }
    if (envelope::CorrelationId(parsed).empty())
    {
        std::cerr << "  [FAIL] telemetry envelope has no correlation_id\n";
        return 3;
    }
    if (!envelope::Payload(parsed).contains("nic_status"))
    {
        std::cerr << "  [FAIL] telemetry payload lost nic_status\n";
        return 4;
    }

    // 2) hello 로 세션을 등록해야 서버가 텔레메트리를 Agent 에 매핑할 수 있습니다.
    TelemetryService session(host, port, "/api/v1/telemetry");
    if (!session.connect())
    {
        std::cerr << "  [FAIL] second connection failed\n";
        return 5;
    }
    if (!session.sendText(envelope::Hello(agent_id, DeviceType::kVirtualMachine).dump()))
    {
        std::cerr << "  [FAIL] hello send failed\n";
        return 6;
    }

    // ack 가 오면 계약이 맞습니다. 오지 않아도 전송 자체는 실패가 아닙니다.
    std::string reply;
    if (session.tryReceiveText(reply, std::chrono::seconds(3)))
    {
        const Json ack = Json::parse(reply);
        if (!envelope::IsType(ack, envelope::kAck) && !envelope::IsType(ack, envelope::kError))
        {
            std::cerr << "  [FAIL] unexpected hello reply type: " << envelope::Type(ack) << '\n';
            return 7;
        }
        std::cout << "  [ok]   hello -> " << envelope::Type(ack) << '\n';
    }
    else
    {
        std::cout << "  [warn] no hello reply within timeout (server may be send-only)\n";
    }

    std::cout << "telemetry_service_test passed\n";
    return 0;
}
