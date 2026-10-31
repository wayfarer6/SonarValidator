#ifndef SONAR_VALIDATOR_PROBER_COMPONENTS_TERMINAL_IOS_CLI_HPP_
#define SONAR_VALIDATOR_PROBER_COMPONENTS_TERMINAL_IOS_CLI_HPP_

#include <algorithm>
#include <cstddef>
#include <cctype>
#include <string>
#include <vector>

// Cisco IOS-XE guestshell 의 `dohost` 로 IOS CLI 를 실행할 때의 공통 규칙입니다.
//

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
        "authorization failed",
        "authorization denied",
        "permission denied",
        "cli syntax error or execution failure",
    };

    OutputCheck check;
    std::size_t begin = 0;
    while (begin < raw.size())
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

    if (Normalize(check.text).empty()) check.failed = true;
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
