#include <iostream>
#include <string>

#include "components/backend_communication/envelope.hpp"
#include "components/policy/policy_json.hpp"

// 네트워크 없이 봉투 생성/파싱 계약만 검증합니다.
// 서버(Java Envelope.java)와 필드명이 어긋나면 이 테스트가 먼저 깨집니다.

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
}

int main()
{
    using Json = nlohmann::json;

    std::cout << "envelope_test\n";

    // 1) 모든 봉투는 서버가 요구하는 6개 필드를 갖습니다.
    {
        const Json request = envelope::PolicyRequest("sw-01", DeviceType::kSwitch, "switch-3");

        Expect(request.contains("type"), "policy-request has type");
        Expect(request.contains("agent_id"), "policy-request has agent_id");
        Expect(request.contains("device_type"), "policy-request has device_type");
        Expect(request.contains("correlation_id"), "policy-request has correlation_id");
        Expect(request.contains("payload"), "policy-request has payload");

        Expect(request["type"] == "policy-request", "type is policy-request");
        Expect(request["agent_id"] == "sw-01", "agent_id preserved");
        Expect(request["device_type"] == "SWITCH", "device_type is SWITCH token");
        Expect(request["payload"]["device_id"] == "switch-3", "payload carries device_id");
        Expect(request["correlation_id"].get<std::string>().rfind("c-", 0) == 0,
               "correlation_id uses c- prefix");
    }

    // 2) correlation_id 는 호출마다 달라야 합니다. (요청-응답 짝 맞추기의 전제)
    {
        const std::string first = envelope::NextCorrelationId();
        const std::string second = envelope::NextCorrelationId();
        Expect(first != second, "correlation_id is unique per call");
    }

    // 3) enum class 는 그대로 직렬화할 수 없으므로 문자열 변환을 반드시 거칩니다.
    {
        Expect(envelope::DeviceTypeToString(DeviceType::kSwitch) == "SWITCH", "kSwitch -> SWITCH");
        Expect(envelope::DeviceTypeToString(DeviceType::kVirtualMachine) == "VM", "kVirtualMachine -> VM");
        Expect(envelope::DeviceTypeToString(DeviceType::kFirewall) == "FIREWALL", "kFirewall -> FIREWALL");
        Expect(envelope::DeviceTypeToString(DeviceType::kRouter) == "ROUTER", "kRouter -> ROUTER");
    }

    // 4) payload 가 null 이어도 서버 계약(항상 객체)을 지켜야 합니다.
    {
        const Json message = envelope::Make("ack", "vm-01", "VM", "c-99", Json());
        Expect(message["payload"].is_object(), "null payload normalized to object");
        Expect(envelope::Payload(message).is_object(), "Payload() returns object");
    }

    // 5) 수신 봉투 파싱 헬퍼는 결측 필드에 안전해야 합니다.
    {
        Json reply = Json::parse(R"({"type":"policy-response","correlation_id":"c-7","payload":{}})");
        Expect(envelope::Type(reply) == "policy-response", "Type parsed");
        Expect(envelope::CorrelationId(reply) == "c-7", "CorrelationId parsed");
        Expect(envelope::IsType(reply, envelope::kPolicyResponse), "IsType matches");
        Expect(!envelope::IsType(reply, envelope::kError), "IsType rejects mismatch");

        const Json malformed = Json::parse(R"({"type":123})");
        Expect(envelope::Type(malformed).empty(), "non-string type yields empty");
        Expect(envelope::CorrelationId(malformed).empty(), "missing correlation_id yields empty");
        Expect(envelope::Payload(malformed).is_object(), "missing payload yields empty object");
        Expect(envelope::ErrorText(malformed).empty(), "missing error yields empty string");

        Json failure = Json::parse(R"({"type":"error","error":"unsupported message type: x"})");
        Expect(envelope::ErrorText(failure) == "unsupported message type: x", "error text parsed");
    }

    // 6) 문서 스키마는 스칼라를 배열로 감쌉니다. policy_json 이 양쪽을 모두 읽어야 합니다.
    {
        const Json wrapped = Json::parse(R"({"command":["on"],"interface":["ens33"]})");
        const Json scalar = Json::parse(R"({"command":"on","interface":"ens33"})");

        Expect(policy_json::AsString(wrapped, "command") == "on", "array-wrapped scalar read");
        Expect(policy_json::AsString(scalar, "command") == "on", "plain scalar read");
        Expect(policy_json::AsString(wrapped, "missing", "dflt") == "dflt", "missing key falls back");
    }

    // 7) 봉투 전체를 문자열로 왕복해도 계약이 유지됩니다. (실제 전송 경로와 동일)
    {
        const Json telemetry = envelope::Telemetry(
            "vm-01", DeviceType::kVirtualMachine, Json{{"agent", "vm-01"}, {"kernel", "6.8"}});

        const std::string wire = telemetry.dump();
        const Json restored = Json::parse(wire);

        Expect(restored == telemetry, "dump/parse round trip is lossless");
        Expect(envelope::Payload(restored)["kernel"] == "6.8", "nested payload survives round trip");
    }

    if (failures > 0)
    {
        std::cerr << "envelope_test FAILED (" << failures << ")\n";
        return 1;
    }

    std::cout << "envelope_test passed\n";
    return 0;
}
