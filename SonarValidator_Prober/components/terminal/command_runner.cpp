#include "components/terminal/command_runner.hpp"

#include <array>
#include <cerrno>
#include <chrono>
#include <utility>

#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

namespace command_runner
{

Result RunWithStatus(const std::string& command,
                     std::chrono::milliseconds timeout,
                     std::size_t max_output_bytes)
{
    int output_pipe[2];
    if (::pipe(output_pipe) != 0)
    {
        return {};
    }

    const pid_t child = ::fork();
    if (child < 0)
    {
        ::close(output_pipe[0]);
        ::close(output_pipe[1]);
        return {};
    }
    if (child == 0)
    {
        ::close(output_pipe[0]);
        (void)::setpgid(0, 0);
        if (::dup2(output_pipe[1], STDOUT_FILENO) < 0)
        {
            _exit(126);
        }
        ::close(output_pipe[1]);
        ::execl("/bin/sh", "sh", "-c", command.c_str(), static_cast<char*>(nullptr));
        _exit(127);
    }

    ::close(output_pipe[1]);
    (void)::setpgid(child, child);
    const int flags = ::fcntl(output_pipe[0], F_GETFL, 0);
    if (flags >= 0)
    {
        (void)::fcntl(output_pipe[0], F_SETFL, flags | O_NONBLOCK);
    }

    std::string result;
    std::array<char, 4096> buffer;
    const auto deadline = std::chrono::steady_clock::now() + timeout;
    bool child_exited = false;
    bool output_eof = false;
    bool output_limit_exceeded = false;
    int status = 0;

    while (!child_exited || !output_eof)
    {
        const auto now = std::chrono::steady_clock::now();
        if (now >= deadline)
        {
            break;
        }

        pollfd descriptor{output_pipe[0], POLLIN | POLLHUP, 0};
        const auto remaining = std::chrono::duration_cast<std::chrono::milliseconds>(deadline - now);
        const int ready = ::poll(&descriptor, 1, static_cast<int>(remaining.count()));
        if (ready < 0 && errno == EINTR)
        {
            continue;
        }
        if (ready < 0)
        {
            break;
        }

        if (ready > 0 && (descriptor.revents & (POLLIN | POLLHUP | POLLERR)) != 0)
        {
            while (true)
            {
                const ssize_t count = ::read(output_pipe[0], buffer.data(), buffer.size());
                if (count > 0)
                {
                    if (static_cast<std::size_t>(count) > max_output_bytes - result.size())
                    {
                        output_limit_exceeded = true;
                        break;
                    }
                    result.append(buffer.data(), static_cast<std::size_t>(count));
                    continue;
                }
                if (count == 0)
                {
                    output_eof = true;
                }
                else if (errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR)
                {
                    output_eof = true;
                }
                break;
            }
        }

        const pid_t waited = ::waitpid(child, &status, WNOHANG);
        if (waited == child || (waited < 0 && errno == ECHILD))
        {
            child_exited = true;
        }
        if (output_limit_exceeded)
        {
            break;
        }
    }

    ::close(output_pipe[0]);
    const bool timed_out = std::chrono::steady_clock::now() >= deadline;
    if (!child_exited || !output_eof || output_limit_exceeded)
    {
        (void)::kill(-child, SIGTERM);
        const auto terminate_deadline = std::chrono::steady_clock::now() +
                                        std::chrono::milliseconds(100);
        while (std::chrono::steady_clock::now() < terminate_deadline)
        {
            const pid_t waited = ::waitpid(child, &status, WNOHANG);
            if (waited == child || (waited < 0 && errno == ECHILD))
            {
                child_exited = true;
                break;
            }
            ::usleep(10000);
        }
        (void)::kill(-child, SIGKILL);
    }
    if (!child_exited)
    {
        while (::waitpid(child, &status, 0) < 0 && errno == EINTR)
        {
        }
    }

    if (timed_out || output_limit_exceeded)
    {
        return {};
    }
    return {std::move(result), WIFEXITED(status) ? WEXITSTATUS(status) : -1, true};
}

std::string Run(const std::string& command,
                std::chrono::milliseconds timeout,
                std::size_t max_output_bytes)
{
    return RunWithStatus(command, timeout, max_output_bytes).output;
}

} // namespace command_runner
