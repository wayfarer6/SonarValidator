#include <iostream>
#include <string>

#include "components/backend_communication/envelope.hpp"
#include "components/policy/quarantine_handler.hpp"
#include "module/configuration_module/prober_config.hpp"

// 격리 명령의 "수신 판정" 계약만 검증합니다. (네트워크/장치 조작 없음)
//
// ─────────────────────────────────────────────────────────────────────────
//  왜 이 테스트가 필요한가
//
//  격리 문자열은 세 곳에 각각 존재합니다.
//
//    서버 Java   QuarantineService.ACTION_QUARANTINE = "quarantine"
//    이쪽 상수   envelope::kActionQuarantine        = "quarantine"
//    판정 상수   quarantine::kIsolate               = "quarantine"
//
//  셋 중 하나만 오타가 나면 컴파일은 통과하고, 격리 버튼은 "명령을 보냈는데
//  아무 일도 안 일어나는" 상태가 됩니다. 화면은 delivered=true 로 표시되므로
//  운영자는 격리된 줄 알고, 실제로는 장치가 그대로 살아 있습니다.
//  **가장 위험한 실패 모드**이므로 여기서 못을 박습니다.
//
//  Isolate()/Release() 자체는 실제 `ip link set ... down` 을 실행하므로
//  테스트에서 호출하지 않습니다. (돌리면 테스트 머신의 인터페이스가 내려갑니다)
// ─────────────────────────────────────────────────────────────────────────

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

    // 5) 관리망 대역은 이제 <b>설정</b>입니다. (하드코딩 상수 아님)
    //    랩/프로젝트마다 다를 수 있고 서버가 프로젝트별로 지정할 수 있습니다.
    //    설정이 비어 있을 때만 안전한 기본값으로 폴백합니다 —
    //    관리 대역을 모르면 격리가 관리 경로를 내려 해제 명령이 도달하지 못합니다.
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