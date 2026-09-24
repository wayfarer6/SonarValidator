#ifndef SONAR_VALIDATOR_PROBER_ENVELOPE_HPP_
#define SONAR_VALIDATOR_PROBER_ENVELOPE_HPP_

#include <atomic>
#include <cstdint>
#include <string>

#include <nlohmann/json.hpp>

#include "components/device/device_type.hpp"

// 중앙 서버(Spring Boot)와 주고받는 공통 메시지 봉투입니다.
//
// STOMP를 쓰지 않고 순수 WebSocket 텍스트 프레임 위에서
// "type" 필드로 메시지 종류를 구분합니다. 요청-응답 짝은 서버가 아니라
// 애플리케이션 계층에서 correlation_id로 맞춥니다.
//
// 서버 DTO(Envelope.java)와 필드명이 정확히 일치해야 합니다(모두 snake_case):
//   type, agent_id, device_type, correlation_id, payload, error
//
// 예시:
//   {"type":"policy-request","agent_id":"sw-01","device_type":"SWITCH",
//    "correlation_id":"c-7","payload":{"device_id":"sw-01"}}
namespace envelope
{

// 서버 Envelope.Types 와 동일한 문자열 상수입니다.
inline constexpr const char* kHello = "hello";
inline constexpr const char* kPolicyRequest = "policy-request";
inline constexpr const char* kPolicyResponse = "policy-response";
inline constexpr const char* kTelemetry = "telemetry";
inline constexpr const char* kCommand = "command";
inline constexpr const char* kAck = "ack";
inline constexpr const char* kError = "error";

using Json = nlohmann::json;

// 장치 유형을 서버가 이해하는 대문자 토큰으로 변환합니다.
// (enum class 는 그대로 JSON 으로 직렬화할 수 없으므로 반드시 이 함수를 거칩니다.)
inline std::string DeviceTypeToString(DeviceType device_type)
{
    switch (device_type)
    {
    case DeviceType::kSwitch:
        return "SWITCH";
    case DeviceType::kVirtualMachine:
        return "VM";
    case DeviceType::kFirewall:
        return "FIREWALL";
    case DeviceType::kRouter:
        return "ROUTER";
    }
    return "VM";
}

// 프로세스 내에서 유일한 상관관계 ID를 만듭니다. (예: c-1, c-2, ...)
inline std::string NextCorrelationId()
{
    static std::atomic<std::uint64_t> counter{0};
    return "c-" + std::to_string(counter.fetch_add(1, std::memory_order_relaxed) + 1);
}

// 공통 필드를 채운 봉투를 만듭니다. payload 가 null 이면 빈 객체로 대체합니다.
inline Json Make(const std::string& type,
                 const std::string& agent_id,
                 const std::string& device_type,
                 const std::string& correlation_id,
                 Json payload = Json::object())
{
    Json message;
    message["type"] = type;
    message["agent_id"] = agent_id;
    message["device_type"] = device_type;
    message["correlation_id"] = correlation_id;
    message["payload"] = payload.is_null() ? Json::object() : std::move(payload);
    return message;
}

// 연결 직후 보내는 인사 봉투입니다. 서버는 ack 로 응답하며 세션을 등록합니다.
inline Json Hello(const std::string& agent_id, DeviceType device_type)
{
    Json payload;
    payload["agent_name"] = agent_id;
    payload["device_type"] = DeviceTypeToString(device_type);
    return Make(kHello, agent_id, DeviceTypeToString(device_type),
                NextCorrelationId(), std::move(payload));
}

// 정책을 요청하는 봉투입니다. 서버는 같은 correlation_id 로 policy-response 를 돌려줍니다.
inline Json PolicyRequest(const std::string& agent_id,
                          DeviceType device_type,
                          const std::string& device_id)
{
    Json payload;
    payload["device_id"] = device_id;
    return Make(kPolicyRequest, agent_id, DeviceTypeToString(device_type),
                NextCorrelationId(), std::move(payload));
}

// 상태 보고용 일방향 봉투입니다. 서버는 응답을 보내지 않습니다.
inline Json Telemetry(const std::string& agent_id,
                      DeviceType device_type,
                      Json payload)
{
    return Make(kTelemetry, agent_id, DeviceTypeToString(device_type),
                NextCorrelationId(), std::move(payload));
}

// 수신 봉투의 type 을 반환합니다. 없으면 빈 문자열입니다.
inline std::string Type(const Json& message)
{
    const auto it = message.find("type");
    if (it == message.end() || !it->is_string())
    {
        return {};
    }
    return it->get<std::string>();
}

// 수신 봉투의 correlation_id 를 반환합니다. 없으면 빈 문자열입니다.
inline std::string CorrelationId(const Json& message)
{
    const auto it = message.find("correlation_id");
    if (it == message.end() || !it->is_string())
    {
        return {};
    }
    return it->get<std::string>();
}

// type 이 기대값과 같은지 확인합니다.
inline bool IsType(const Json& message, const char* expected)
{
    return Type(message) == expected;
}

// payload 를 참조로 꺼냅니다. 없거나 객체가 아니면 빈 객체를 가리킵니다.
inline const Json& Payload(const Json& message)
{
    static const Json kEmpty = Json::object();
    const auto it = message.find("payload");
    if (it == message.end() || !it->is_object())
    {
        return kEmpty;
    }
    return *it;
}

// 서버가 보낸 error 문자열을 꺼냅니다.
inline std::string ErrorText(const Json& message)
{
    const auto it = message.find("error");
    if (it == message.end() || !it->is_string())
    {
        return {};
    }
    return it->get<std::string>();
}

} // namespace envelope

#endif // SONAR_VALIDATOR_PROBER_ENVELOPE_HPP_
