#include "components/terminal/terminal_session.hpp"

#include <algorithm>
#include <cerrno>
#include <cstdlib>
#include <thread>
#include <utility>
#include <dirent.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <spawn.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

extern char** environ;

namespace
{
void SignalSession(pid_t leader, int signal)
{
    // Job control gives foreground/background jobs their own process groups.
    if (DIR* processes = opendir("/proc"))
    {
        while (dirent* entry = readdir(processes))
        {
            char* end = nullptr;
            const long value = strtol(entry->d_name, &end, 10);
            if (*entry->d_name && *end == '\0' && value > 1 &&
                getsid(static_cast<pid_t>(value)) == leader)
            {
                kill(static_cast<pid_t>(value), signal);
            }
        }
        closedir(processes);
    }
    kill(-leader, signal);
    kill(leader, signal);
}
}

TerminalSession::~TerminalSession() { Close(); }

TerminalSession::TerminalSession(TerminalSession&& other) noexcept
    : master_fd_(std::exchange(other.master_fd_, -1)),
      child_pid_(std::exchange(other.child_pid_, -1)),
      ended_(std::exchange(other.ended_, false)) {}

TerminalSession& TerminalSession::operator=(TerminalSession&& other) noexcept
{
    if (this != &other)
    {
        Close();
        master_fd_ = std::exchange(other.master_fd_, -1);
        child_pid_ = std::exchange(other.child_pid_, -1);
        ended_ = std::exchange(other.ended_, false);
    }
    return *this;
}

bool TerminalSession::Open(const std::vector<std::string>& argv)
{
    Close();
    if (argv.empty() || argv.front().empty()) return false;

    master_fd_ = posix_openpt(O_RDWR | O_NOCTTY | O_NONBLOCK | O_CLOEXEC);
    char slave_name[256];
    if (master_fd_ < 0 || grantpt(master_fd_) != 0 || unlockpt(master_fd_) != 0 ||
        ptsname_r(master_fd_, slave_name, sizeof(slave_name)) != 0)
    {
        Close();
        return false;
    }

    std::vector<char*> args;
    for (const auto& arg : argv) args.push_back(const_cast<char*>(arg.c_str()));
    args.push_back(nullptr);
    posix_spawn_file_actions_t actions;
    posix_spawnattr_t attributes;
    if (posix_spawn_file_actions_init(&actions) != 0)
    {
        Close();
        return false;
    }
    if (posix_spawnattr_init(&attributes) != 0)
    {
        posix_spawn_file_actions_destroy(&actions);
        Close();
        return false;
    }
    sigset_t defaults, mask;
    sigemptyset(&defaults);
    sigemptyset(&mask);
    for (const int signal : {SIGINT, SIGQUIT, SIGTERM, SIGHUP, SIGPIPE, SIGTSTP, SIGTTIN, SIGTTOU})
        sigaddset(&defaults, signal);

    const int error =
        posix_spawnattr_setflags(&attributes,
            POSIX_SPAWN_SETSID | POSIX_SPAWN_SETSIGDEF | POSIX_SPAWN_SETSIGMASK) ||
        posix_spawnattr_setsigdefault(&attributes, &defaults) ||
        posix_spawnattr_setsigmask(&attributes, &mask) ||
        posix_spawn_file_actions_addopen(&actions, STDIN_FILENO, slave_name, O_RDWR, 0) ||
        posix_spawn_file_actions_adddup2(&actions, STDIN_FILENO, STDOUT_FILENO) ||
        posix_spawn_file_actions_adddup2(&actions, STDIN_FILENO, STDERR_FILENO) ||
        // A shell must never retain Prober sockets, SQLite files, or its launcher lock.
        posix_spawn_file_actions_addclosefrom_np(&actions, STDERR_FILENO + 1) ||
        posix_spawnp(&child_pid_, args[0], &actions, &attributes, args.data(), environ);
    posix_spawn_file_actions_destroy(&actions);
    posix_spawnattr_destroy(&attributes);
    if (error)
    {
        child_pid_ = -1;
        Close();
        return false;
    }
    return true;
}

void TerminalSession::Close()
{
    const pid_t child = child_pid_;
    if (child > 0) SignalSession(child, SIGTERM);
    if (master_fd_ >= 0) ::close(std::exchange(master_fd_, -1));
    if (child > 0)
    {
        const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(200);
        // Keep the leader unreaped until all remaining jobs have been signalled.
        while (std::chrono::steady_clock::now() < deadline)
        {
            siginfo_t info{};
            const int result = waitid(P_PID, child, &info, WEXITED | WNOHANG | WNOWAIT);
            if ((result == 0 && info.si_pid == child) || (result < 0 && errno == ECHILD)) break;
            if (result < 0 && errno != EINTR) break;
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
        SignalSession(child, SIGKILL);
        while (waitpid(child, nullptr, 0) < 0 && errno == EINTR) {}
    }
    child_pid_ = -1;
    ended_ = false;
}

bool TerminalSession::Write(const std::string& data)
{
    if (data.empty()) return false;
    return WriteRaw(data.back() == '\n' ? data : data + '\n');
}

bool TerminalSession::WriteRaw(const std::string& data)
{
    if (!IsOpen()) return false;
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(250);
    std::size_t offset = 0;
    while (offset < data.size())
    {
        const ssize_t written = ::write(master_fd_, data.data() + offset, data.size() - offset);
        if (written > 0) { offset += written; continue; }
        if (written < 0 && errno == EINTR) continue;
        if (written < 0 && (errno == EAGAIN || errno == EWOULDBLOCK))
        {
            const auto left = std::chrono::duration_cast<std::chrono::milliseconds>(
                deadline - std::chrono::steady_clock::now()).count();
            if (left <= 0) return false;
            pollfd fd{master_fd_, POLLOUT, 0};
            const int ready = poll(&fd, 1, static_cast<int>(left));
            if (ready < 0 && errno == EINTR) continue;
            if (ready > 0 && (fd.revents & POLLOUT)) continue;
        }
        ended_ = true;
        return false;
    }
    return true;
}

bool TerminalSession::Resize(unsigned short columns, unsigned short rows)
{
    if (!IsOpen() || columns == 0 || rows == 0) return false;
    winsize size{};
    size.ws_col = columns;
    size.ws_row = rows;
    return ::ioctl(master_fd_, TIOCSWINSZ, &size) == 0;
}

std::string TerminalSession::ReadAvailable(std::chrono::milliseconds timeout,
                                         std::size_t max_output_bytes)
{
    std::string result;
    const auto deadline = std::chrono::steady_clock::now() + timeout;
    while (IsOpen() && result.size() < max_output_bytes)
    {
        const auto left = std::chrono::duration_cast<std::chrono::milliseconds>(
            deadline - std::chrono::steady_clock::now()).count();
        if (left <= 0) break;
        pollfd fd{master_fd_, POLLIN, 0};
        const int ready = poll(&fd, 1, static_cast<int>(left));
        if (ready < 0 && errno == EINTR) continue;
        if (ready <= 0) break;
        char buffer[4096];
        const ssize_t count = ::read(master_fd_, buffer,
            std::min(sizeof(buffer), max_output_bytes - result.size()));
        if (count > 0)
        {
            result.append(buffer, count);
            if (static_cast<std::size_t>(count) < sizeof(buffer)) break;
            continue;
        }
        if (count < 0 && errno == EINTR) continue;
        if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) break;
        ended_ = true;
    }
    if (result.empty() && child_pid_ > 0)
    {
        // Background jobs can keep the slave open after the shell leader exits.
        siginfo_t child{};
        if (waitid(P_PID, child_pid_, &child, WEXITED | WNOHANG | WNOWAIT) == 0 &&
            child.si_pid == child_pid_) ended_ = true;
    }
    return result;
}

std::string TerminalSession::ReadUntil(const std::string& prompt, std::chrono::milliseconds timeout)
{
    if (prompt.empty()) return ReadAvailable(timeout);
    std::string result;
    const auto deadline = std::chrono::steady_clock::now() + timeout;
    while (IsOpen() && std::chrono::steady_clock::now() < deadline)
    {
        const auto left = std::chrono::duration_cast<std::chrono::milliseconds>(
            deadline - std::chrono::steady_clock::now());
        result += ReadAvailable(left);
        if (result.find(prompt) != std::string::npos) break;
    }
    return result;
}
