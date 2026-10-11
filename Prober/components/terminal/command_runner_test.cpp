#include "components/terminal/command_runner.hpp"

#include <chrono>
#include <iostream>
#include <string>
#include <thread>

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
    Expect(command_runner::Run("printf 'ready'", std::chrono::seconds(1)) == "ready",
           "captures successful command output", failures);
    const auto failed_command =
        command_runner::RunWithStatus("exit 7", std::chrono::seconds(1));
    Expect(failed_command.completed && failed_command.exit_code == 7,
           "reports nonzero command exit status", failures);

    const auto started = std::chrono::steady_clock::now();
    const std::string timed_out =
        command_runner::Run("trap '' TERM; exec sleep 30", std::chrono::milliseconds(100));
    const auto elapsed = std::chrono::steady_clock::now() - started;
    Expect(timed_out.empty(), "returns empty output after command timeout", failures);
    Expect(elapsed < std::chrono::seconds(2), "terminates timed-out process group promptly", failures);

    Expect(command_runner::Run("printf '123456'", std::chrono::seconds(1), 4).empty(),
           "stops commands that exceed output limit", failures);

    std::stop_source source;
    std::jthread cancel([&] {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        source.request_stop();
    });
    const auto cancel_start = std::chrono::steady_clock::now();
    const auto cancelled = command_runner::RunWithStatus(
        "trap '' TERM; exec sleep 30", std::chrono::seconds(20), 4096, source.get_token());
    Expect(!cancelled.completed && std::chrono::steady_clock::now() - cancel_start < std::chrono::seconds(2),
           "shutdown cancels an active command and its process group", failures);
    return failures == 0 ? 0 : 1;
}
