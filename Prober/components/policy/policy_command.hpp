#ifndef SONAR_VALIDATOR_PROBER_POLICY_COMMAND_HPP_
#define SONAR_VALIDATOR_PROBER_POLICY_COMMAND_HPP_

#include <optional>
#include <string>

#include <nlohmann/json.hpp>

#include "components/device/device_type.hpp"

enum class PolicyAction
{
    kOn,
    kOff,
    kCreate,
    kRemove,
    kGet,

    // "이 ACL 을 이 내용으로 맞춰라" (선언적).
    //
    // ⚠️ create 와 무엇이 다른가
    //   create 는 규칙을 <b>더하는</b> 명령입니다. 그래서 "규칙 하나를
    //   지운다" 를 표현할 수 없습니다 — 서버가 "이 규칙만 빼고 다시
    //   맞춰라" 라고 말할 방법이 없습니다.
    //   apply 는 목록 전체를 주고 장치가 알아서 맞추게 합니다.
    //   (IOS 확장 ACL 처럼 이름으로 통째로 다시 쓰는 장치에 필요)
    kApply
};

struct PolicyCommand
{
    DeviceType device_type;
    std::string product;
    PolicyAction action;
    nlohmann::json payload;

    static std::optional<PolicyCommand> Parse(DeviceType device_type,
                                              std::string product,
                                              const nlohmann::json& policy,
                                              std::string* error = nullptr);

    static const char* ActionName(PolicyAction action);
};

#endif // SONAR_VALIDATOR_PROBER_POLICY_COMMAND_HPP_
