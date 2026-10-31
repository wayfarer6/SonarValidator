#include <iostream>
#include <string>

#include "components/backend_communication/envelope.hpp"
#include "components/policy/quarantine_handler.hpp"
#include "module/configuration_module/prober_config.hpp"


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

    std::cout << "quarantine_handler_test\n";

    // 1) 서버와의 문자열 계약. (서버 QuarantineService 의 상수와 같아야 함)
    {
        Expect(std::string(envelope::kActionQuarantine) == "quarantine",
               "envelope::kActionQuarantine is \"quarantine\"");
        Expect(std::string(envelope::kActionRelease) == "release",
               "envelope::kActionRelease is \"release\"");
        Expect(std::string(quarantine::kIsolate) == std::string(envelope::kActionQuarantine),
               "quarantine::kIsolate matches envelope::kActionQuarantine");
        Expect(std::string(quarantine::kRelease) == std::string(envelope::kActionRelease),
               "quarantine::kRelease matches envelope::kActionRelease");
    }

    // 2) 서버가 실제로 보내는 모양(payload.action)을 격리 명령으로 인식해야 합니다.
    //    QuarantineService.sendCommand() 가 만드는 봉투와 같은 형태입니다.
    {
        const Json isolate = Json::parse(R"({
            "type": "command",
            "agent_id": "ATICS-agent",
            "device_type": "VM",
            "correlation_id": "c-11",
            "payload": {
                "action": "quarantine",
                "agent_id": "ATICS-agent",
                "project_id": "poc-dai-pbl",
                "reason": "등급 건너뛰기",
                "issued_at": "2026-09-25T23:38:57Z"
            }
        })");
        Expect(quarantine::IsQuarantineCommand(isolate), "server-shaped quarantine envelope accepted");

        const Json release = Json::parse(R"({
            "type": "command",
            "agent_id": "ATICS-agent",
            "payload": { "action": "release", "agent_id": "ATICS-agent" }
        })");
        Expect(quarantine::IsQuarantineCommand(release), "release envelope accepted");
    }

    // 3) 손으로 만든 봉투(최상위 action)도 받아야 합니다. (운영자 디버깅 편의)
    {
        const Json flat = Json::parse(R"({"type":"command","action":"quarantine","payload":{}})");
        Expect(quarantine::IsQuarantineCommand(flat), "top-level action fallback accepted");
    }

    // 4) 격리 명령이 아닌 것은 <b>절대</b> 격리로 오인하면 안 됩니다.
    //    오인이 곧 "정상 장치의 인터페이스를 내리는" 사고입니다.
    {
        const Json telemetry = Json::parse(R"({"type":"telemetry","payload":{"action":"quarantine"}})");
        Expect(!quarantine::IsQuarantineCommand(telemetry), "telemetry never treated as command");

        const Json policyResponse = Json::parse(R"({"type":"policy-response","payload":{"action":"quarantine"}})");
        Expect(!quarantine::IsQuarantineCommand(policyResponse),
               "policy-response never treated as command");

        // 알 수 없는 action (오타/미래 확장) 은 무시합니다.
        const Json unknownAction = Json::parse(R"({"type":"command","payload":{"action":"quarantin"}})");
        Expect(!quarantine::IsQuarantineCommand(unknownAction), "misspelled action rejected");

        const Json otherCommand = Json::parse(R"({"type":"command","payload":{"action":"restart"}})");
        Expect(!quarantine::IsQuarantineCommand(otherCommand), "other command action rejected");

        const Json noAction = Json::parse(R"({"type":"command","payload":{"device_id":"x"}})");
        Expect(!quarantine::IsQuarantineCommand(noAction), "command without action rejected");

        // action 이 문자열이 아니면 무시합니다. (get<std::string> 예외 방지)
        const Json numericAction = Json::parse(R"({"type":"command","payload":{"action":7}})");
        Expect(!quarantine::IsQuarantineCommand(numericAction), "non-string action rejected");
    }

    {
        Expect(std::string(quarantine::kDefaultManagementPrefix) == "172.16.255.0/24",
               "default management prefix fallback is 172.16.255.0/24");

        // ⚠️ 설정에서 관리 대역을 읽어 여러 개를 담을 수 있어야 합니다.
        ProberConfig config(
            "", "", "", "", DeviceType::kSwitch, "", 0, "", 0);
        config.SetManagementPrefixes("10.10.0.0/16,172.16.255.0/24");
        Expect(config.GetManagementPrefixes() == "10.10.0.0/16,172.16.255.0/24",
               "management prefixes come from configuration (multi-band)");

        // 설정이 비어 있으면 폴백합니다. (관리 대역을 모르는 채 격리하면 안 됩니다)
        ProberConfig empty(
            "", "", "", "", DeviceType::kSwitch, "", 0, "", 0);
        empty.DetectManagementPrefixes();
        Expect(!empty.GetManagementPrefixes().empty(),
               "empty configuration falls back to a safe default");
    }

    // 6) ack 에 실어 보낼 Outcome 기본값은 "실패" 여야 합니다.
    //    기본이 성공이면 초기화를 빠뜨렸을 때 거짓 성공을 보고합니다.
    {
        const quarantine::Outcome fresh;
        Expect(!fresh.ok, "Outcome fails closed by default");
        Expect(fresh.action.empty(), "Outcome action empty by default");
        Expect(fresh.affected.empty(), "Outcome affected empty by default");
    }

    if (failures == 0)
    {
        std::cout << "quarantine_handler_test: all checks passed\n";
        return 0;
    }

    std::cerr << "quarantine_handler_test: " << failures << " check(s) failed\n";
    return 1;
}
