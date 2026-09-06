#ifndef SONAR_VALIDATOR_PROBER_TERMINAL_SESSION_HPP_
#define SONAR_VALIDATOR_PROBER_TERMINAL_SESSION_HPP_

#include <chrono>
#include <string>
#include <vector>

#include <sys/types.h>

// pty(가상 터미널) 기반의 영속 CLI 세션입니다.
// ManagementService가 살아있는 동안(graceful shutdown 전까지) 세션을 유지해
// vtysh / FastCli / nft 같은 대화형 CLI의 상태(config 모드 등)를 보존합니다.
class TerminalSession
{
public:
    TerminalSession() = default;
    ~TerminalSession();

    TerminalSession(const TerminalSession&) = delete;
    TerminalSession& operator=(const TerminalSession&) = delete;
    TerminalSession(TerminalSession&& other) noexcept;
    TerminalSession& operator=(TerminalSession&& other) noexcept;

    // argv[0] = 실행 프로그램, 나머지는 인자입니다. (예: {"nft", "-i"})
    bool Open(const std::vector<std::string>& argv);
    void Close();
    bool IsOpen() const { return master_fd_ >= 0; }

    // 명령(한 줄 또는 '\n'으로 구분된 여러 줄)을 전송합니다.
    bool Write(const std::string& data);

    // timeout 동안 읽은 출력을 반환합니다.
    std::string ReadAvailable(std::chrono::milliseconds timeout);

    // 프롬프트 문자열이 나올 때까지 읽습니다. 프롬프트가 비어 있으면 ReadAvailable과 동일하게 동작합니다.
    std::string ReadUntil(const std::string& prompt, std::chrono::milliseconds timeout);

private:
    int master_fd_ = -1;
    pid_t child_pid_ = -1;
};

#endif // SONAR_VALIDATOR_PROBER_TERMINAL_SESSION_HPP_
