#include "terminal_session.hpp"

#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <iostream>

#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

TerminalSession::~TerminalSession()
{
    Close();
}

TerminalSession::TerminalSession(TerminalSession&& other) noexcept
    : master_fd_(other.master_fd_),
      child_pid_(other.child_pid_)
{
    other.master_fd_ = -1;
    other.child_pid_ = -1;
}

TerminalSession& TerminalSession::operator=(TerminalSession&& other) noexcept
{
    if (this != &other)
    {
        Close();
        master_fd_ = other.master_fd_;
        child_pid_ = other.child_pid_;
        other.master_fd_ = -1;
        other.child_pid_ = -1;
    }
    return *this;
}

bool TerminalSession::Open(const std::vector<std::string>& argv)
{
    Close();

    if (argv.empty())
    {
        return false;
    }

    master_fd_ = posix_openpt(O_RDWR | O_NOCTTY);
    if (master_fd_ < 0)
    {
        return false;
    }
    if (grantpt(master_fd_) != 0 || unlockpt(master_fd_) != 0)
    {
        ::close(master_fd_);
        master_fd_ = -1;
        return false;
    }

    const char* slave_name = ptsname(master_fd_);
    if (slave_name == nullptr)
    {
        ::close(master_fd_);
        master_fd_ = -1;
        return false;
    }

    const pid_t pid = fork();
    if (pid < 0)
    {
        ::close(master_fd_);
        master_fd_ = -1;
        return false;
    }

    if (pid == 0)
    {
        // 자식: 새 세션을 만들고 slave pty를 제어 터미널로 붙여 CLI를 실행합니다.
        setsid();
        const int slave_fd = open(slave_name, O_RDWR);
        if (slave_fd < 0)
        {
            _exit(127);
        }
        ioctl(slave_fd, TIOCSCTTY, 0);
        dup2(slave_fd, STDIN_FILENO);
        dup2(slave_fd, STDOUT_FILENO);
        dup2(slave_fd, STDERR_FILENO);
        if (slave_fd > STDERR_FILENO)
        {
            ::close(slave_fd);
        }

        std::vector<char*> c_argv;
        c_argv.reserve(argv.size() + 1);
        for (const auto& arg : argv)
        {
            c_argv.push_back(const_cast<char*>(arg.c_str()));
        }
        c_argv.push_back(nullptr);

        execvp(c_argv[0], c_argv.data());
        _exit(127);
    }

    child_pid_ = pid;
    return true;
}

void TerminalSession::Close()
{
    if (master_fd_ >= 0)
    {
        ::close(master_fd_);
        master_fd_ = -1;
    }
    if (child_pid_ > 0)
    {
        kill(child_pid_, SIGTERM);
        int status = 0;
        waitpid(child_pid_, &status, 0);
        child_pid_ = -1;
    }
}

bool TerminalSession::Write(const std::string& data)
{
    if (master_fd_ < 0 || data.empty())
    {
        return false;
    }

    std::string buffer = data;
    if (buffer.back() != '\n')
    {
        buffer.push_back('\n');
    }

    const ssize_t written = ::write(master_fd_, buffer.data(), buffer.size());
    return written == static_cast<ssize_t>(buffer.size());
}

std::string TerminalSession::ReadAvailable(std::chrono::milliseconds timeout)
{
    if (master_fd_ < 0)
    {
        return {};
    }

    std::string result;
    char buffer[4096];
    const auto deadline = std::chrono::steady_clock::now() + timeout;

    while (true)
    {
        const auto now = std::chrono::steady_clock::now();
        if (now >= deadline)
        {
            break;
        }
        const auto remaining =
            std::chrono::duration_cast<std::chrono::milliseconds>(deadline - now);

        pollfd pfd{master_fd_, POLLIN, 0};
        const int ready = poll(&pfd, 1, static_cast<int>(remaining.count()));
        if (ready <= 0)
        {
            break;
        }

        const ssize_t n = ::read(master_fd_, buffer, sizeof(buffer));
        if (n <= 0)
        {
            break;
        }
        result.append(buffer, static_cast<std::size_t>(n));
        if (static_cast<std::size_t>(n) < sizeof(buffer))
        {
            break;
        }
    }

    return result;
}

std::string TerminalSession::ReadUntil(const std::string& prompt,
                                       std::chrono::milliseconds timeout)
{
    if (prompt.empty())
    {
        return ReadAvailable(timeout);
    }

    if (master_fd_ < 0)
    {
        return {};
    }

    std::string result;
    char buffer[4096];
    const auto deadline = std::chrono::steady_clock::now() + timeout;

    while (true)
    {
        const auto now = std::chrono::steady_clock::now();
        if (now >= deadline)
        {
            break;
        }
        const auto remaining =
            std::chrono::duration_cast<std::chrono::milliseconds>(deadline - now);

        pollfd pfd{master_fd_, POLLIN, 0};
        const int ready = poll(&pfd, 1, static_cast<int>(remaining.count()));
        if (ready <= 0)
        {
            break;
        }

        const ssize_t n = ::read(master_fd_, buffer, sizeof(buffer));
        if (n <= 0)
        {
            break;
        }
        result.append(buffer, static_cast<std::size_t>(n));
        if (result.find(prompt) != std::string::npos)
        {
            break;
        }
    }

    return result;
}
