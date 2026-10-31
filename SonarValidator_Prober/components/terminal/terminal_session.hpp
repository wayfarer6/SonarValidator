#ifndef SONAR_VALIDATOR_PROBER_TERMINAL_SESSION_HPP_
#define SONAR_VALIDATOR_PROBER_TERMINAL_SESSION_HPP_

#include <chrono>
#include <string>
#include <vector>

#include <sys/types.h>

class TerminalSession
{
public:
    TerminalSession() = default;
    ~TerminalSession();

    TerminalSession(const TerminalSession&) = delete;
    TerminalSession& operator=(const TerminalSession&) = delete;
    TerminalSession(TerminalSession&& other) noexcept;
    TerminalSession& operator=(TerminalSession&& other) noexcept;

    bool Open(const std::vector<std::string>& argv);
    void Close();
    bool IsOpen() const { return master_fd_ >= 0 && !ended_; }

    bool Write(const std::string& data);

    bool WriteRaw(const std::string& data);
    bool Resize(unsigned short columns, unsigned short rows);

    std::string ReadAvailable(std::chrono::milliseconds timeout,
                              std::size_t max_output_bytes = 64 * 1024);

    std::string ReadUntil(const std::string& prompt, std::chrono::milliseconds timeout);

private:
    int master_fd_ = -1;
    pid_t child_pid_ = -1;
    bool ended_ = false;
};

#endif
