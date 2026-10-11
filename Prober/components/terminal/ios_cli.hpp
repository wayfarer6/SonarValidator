#ifndef SONAR_VALIDATOR_PROBER_COMPONENTS_TERMINAL_IOS_CLI_HPP_
#define SONAR_VALIDATOR_PROBER_COMPONENTS_TERMINAL_IOS_CLI_HPP_

#include <algorithm>
#include <cstddef>
#include <cctype>
#include <string>
#include <vector>

// Cisco IOS-XE guestshell 의 `dohost` 로 IOS CLI 를 실행할 때의 공통 규칙입니다.
//
// <h2>왜 여러 명령을 한 번에 넘기는가</h2>
//
// `dohost` 한 번은 IOS 의 exec 세션 하나입니다. 그래서
//
//   dohost "configure terminal" && dohost "interface Gi4" && dohost "no shutdown"
//
// 처럼 나눠 실행하면 `configure terminal` 프로세스가 끝날 때 설정 모드도
// 함께 끝나고, 뒤이은 `interface ...` / `no shutdown` 은 exec 모드에서
// 실행되어 `% Invalid input` 으로 거부됩니다. 결과적으로 설정은 하나도
// 적용되지 않는데, 호출자는 출력이 비어 있지 않다는 이유로 성공으로
// 오판했습니다. 설정 시퀀스는 반드시 dohost 한 번에 이어 붙여야
// 설정 컨텍스트가 유지됩니다.
//
// <h2>왜 출력을 걸러야 하는가</h2>
//
// `dohost` 는 IOS 가 명령을 거부해도 종료코드 0 을 돌려줍니다. 성공/실패는
// 출력의 `%` 오류 줄로만 판정할 수 있습니다. 그래서 오류 줄을 보고 실패를
// 판정하고, 실패면 빈 문자열을 돌려줘 호출자의 `!output.empty()` 판정이
// 올바르게 동작하게 합니다.
namespace ios_cli
{

// 앞뒤 공백을 제거한 소문자 사본입니다. (명령 비교용)
inline std::string Normalize(const std::string& command)
{
    const std::size_t first = command.find_first_not_of(" \t\r\n");
    if (first == std::string::npos)
    {
        return {};
    }
    const std::size_t last = command.find_last_not_of(" \t\r\n");
    std::string trimmed = command.substr(first, last - first + 1);
    std::transform(trimmed.begin(), trimmed.end(), trimmed.begin(),
                   [](unsigned char ch) { return static_cast<char>(std::tolower(ch)); });
    return trimmed;
}

// 설정 모드로 들어가는 명령인지 확인합니다.
inline bool EntersConfigMode(const std::string& command)
{
    const std::string normalized = Normalize(command);
    return normalized == "configure terminal" || normalized == "conf t" ||
           normalized == "configure" || normalized.starts_with("configure terminal");
}

// dohost 한 번에 넣을 IOS 명령열을 만듭니다.
//
// 설정 시퀀스가 `end` 로 닫히지 않았으면 붙여 줍니다. 닫지 않으면 다음
// dohost 가 설정 모드에 갇혀 조회 명령이 오동작합니다.
inline std::string BuildScript(const std::vector<std::string>& commands)
{
    std::vector<std::string> sequence;
    bool enters_config = false;
    bool has_end = false;
    for (const std::string& command : commands)
    {
        const std::string normalized = Normalize(command);
        if (normalized.empty())
        {
            continue;
        }
        if (EntersConfigMode(command))
        {
            enters_config = true;
        }
        if (normalized == "end")
        {
            has_end = true;
        }
        sequence.push_back(command);
    }
    if (enters_config && !has_end)
    {
        sequence.push_back("end");
    }

    std::string script;
    for (std::size_t i = 0; i < sequence.size(); ++i)
    {
        if (i != 0)
        {
            script += " ; ";
        }
        script += sequence[i];
    }
    return script;
}

// dohost 출력에서 IOS CLI 오류를 판정한 결과입니다.
struct OutputCheck
{
    std::string text;              // 정상 출력만 남긴 텍스트 (실패면 빈 문자열)
    bool failed{false};            // 오류로 판정했는가
    bool session_unavailable{false}; // guestshell↔IOS 세션 자체가 없는가
};

// dohost 는 IOS 가 명령을 거부하거나 guestshell 의 IOSP 세션이 없어도
// 종료코드 0 을 돌려줍니다. 그래서 성공/실패는 출력으로만 판정할 수 있습니다.
//
// 특히 `/cisco/cisco_cli/app-session-info` 파일과 IOSP_* 환경변수가 없으면
// (IOS 가 `guestshell run` 으로 세션을 만들지 않은 경우) dohost 는
// `Unexpected Error` 만 뱉고 끝납니다. 이때는 명령 문제가 아니라 환경 문제이므로
// 따로 표시해 운영자가 guestshell 앱을 재시작하도록 안내합니다.
inline OutputCheck CheckOutput(const std::string& raw)
{
    static const char* kSessionMarkers[] = {
        "iosp_session",              // IOSP_SESSION environment variable not set
        "app-session-info",
        "no setupinprogress var",
        "unexpected error",          // IOSP 서버 연결 실패 (실장비 실측)
        "system error",
    };
    static const char* kErrorMarkers[] = {
        "% invalid input",           // IOS CLI 파서 거부
        "% incomplete command",
        "% ambiguous command",
        "% error",
        "cli syntax error or execution failure",
    };

    OutputCheck check;
    std::size_t begin = 0;
    while (begin <= raw.size())
    {
        const std::size_t end = raw.find('\n', begin);
        const std::string line = raw.substr(
            begin, end == std::string::npos ? std::string::npos : end - begin);
        const std::string normalized = Normalize(line);

        bool error_line = false;
        for (const char* marker : kErrorMarkers)
        {
            if (normalized.find(marker) != std::string::npos)
            {
                check.failed = true;
                error_line = true;
                break;
            }
        }
        if (!error_line)
        {
            for (const char* marker : kSessionMarkers)
            {
                if (normalized.find(marker) != std::string::npos)
                {
                    check.failed = true;
                    check.session_unavailable = true;
                    error_line = true;
                    break;
                }
            }
        }

        if (error_line)
        {
            // 오류 줄은 버립니다.
        }
        else if (normalized.rfind("dohost", 0) == 0)
        {
            // dohost 자체가 붙이는 잡음 줄은 버립니다.
        }
        else
        {
            check.text += line;
            check.text += '\n';
        }

        if (end == std::string::npos)
        {
            break;
        }
        begin = end + 1;
    }

    if (check.failed)
    {
        check.text.clear();
    }
    return check;
}

// 이전 호출부와의 호환을 위한 얇은 래퍼입니다.
inline std::string FilterOutput(const std::string& raw, bool& failed)
{
    const OutputCheck check = CheckOutput(raw);
    failed = check.failed;
    return check.text;
}

} // namespace ios_cli

#endif // SONAR_VALIDATOR_PROBER_COMPONENTS_TERMINAL_IOS_CLI_HPP_
