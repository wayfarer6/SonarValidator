#include "components/terminal/ios_cli.hpp"

#include <iostream>
#include <string>
#include <vector>

namespace
{

void Expect(bool condition, const char* message, int& failures)
{
    if (condition)
    {
        std::cout << "[ok] " << message << '\n';
    }
    else
    {
        std::cerr << "[FAIL] " << message << '\n';
        ++failures;
    }
}

} // namespace

int main()
{
    int failures = 0;

    // 설정 시퀀스는 dohost 한 번에 세미콜론으로 이어 붙습니다. 그래야
    // `configure terminal` 이후의 `interface ...` 가 설정 모드에서 실행됩니다.
    const std::string config_script = ios_cli::BuildScript(
        {"configure terminal", "interface GigabitEthernet4", "no shutdown"});
    Expect(config_script == "configure terminal ; interface GigabitEthernet4 ; no shutdown ; end",
           "config sequence is joined into one dohost call and closed with end", failures);

    Expect(ios_cli::BuildScript({"show ip interface brief"}) == "show ip interface brief",
           "show command is passed through unchanged", failures);

    Expect(ios_cli::BuildScript({"configure terminal", "interface Gi1", "no shut", "end"}) ==
               "configure terminal ; interface Gi1 ; no shut ; end",
           "an explicit end is not duplicated", failures);

    Expect(ios_cli::BuildScript({}) .empty(), "empty command list produces an empty script", failures);
    Expect(ios_cli::BuildScript({"   ", ""}).empty(), "blank commands are ignored", failures);

    // dohost 는 IOS 가 명령을 거부해도 종료코드 0 을 준다. `%` 오류 줄로 실패를
    // 판정하고 빈 문자열을 돌려줘야 호출자의 `!output.empty()` 오판이 사라진다.
    bool failed = false;
    const std::string rejected = ios_cli::FilterOutput(
        "interface GigabitEthernet4\n% Invalid input detected at '^' marker.\n", failed);
    Expect(failed, "IOS error line marks the command as failed", failures);
    Expect(rejected.empty(), "failed IOS output is emptied so callers stop reporting success", failures);

    const std::string ok = ios_cli::FilterOutput(
        "Interface              IP-Address      OK? Method Status                Protocol\n"
        "GigabitEthernet4       10.20.0.1       YES NVRAM  up                    up\n",
        failed);
    Expect(!failed, "clean output is not treated as an error", failures);
    Expect(ok.find("GigabitEthernet4") != std::string::npos,
           "clean output is preserved for the parser", failures);

    const std::string noise = ios_cli::FilterOutput("dohost: running\nshow version\n", failed);
    Expect(!failed && noise.find("dohost") == std::string::npos && noise.find("show version") != std::string::npos,
           "dohost noise lines are dropped but IOS output is kept", failures);

    // 실장비(10.20.0.1 guestshell) 실측: IOSP 세션이 없으면 dohost 는
    // "Unexpected Error" 를 출력하고 종료코드 0 을 돌려준다. 이 텍스트를
    // 텔레메트리 데이터로 오인하면 안 된다.
    {
        const ios_cli::OutputCheck check = ios_cli::CheckOutput("Unexpected Error\n");
        Expect(check.failed, "real-device 'Unexpected Error' is treated as failure", failures);
        Expect(check.session_unavailable,
               "'Unexpected Error' is reported as a missing guestshell↔IOS session", failures);
        Expect(check.text.empty(), "session failure yields no telemetry text", failures);
    }
    {
        const ios_cli::OutputCheck check =
            ios_cli::CheckOutput("dohost_sh: ERROR: IOSP_SESSION environment variable not set\n");
        Expect(check.failed && check.session_unavailable,
               "missing IOSP_SESSION is reported as an unavailable session", failures);
    }
    {
        const ios_cli::OutputCheck check =
            ios_cli::CheckOutput("% Invalid input detected at '^' marker.\n");
        Expect(check.failed && !check.session_unavailable,
               "IOS parser error is a command failure, not a session failure", failures);
    }

    return failures == 0 ? 0 : 1;
}
