#include "components/terminal/terminal_session.hpp"

#include <chrono>
#include <iostream>

int main()
{
    TerminalSession io_session;
    if (!io_session.Open({"/bin/sh", "-c", "read -r line; printf 'received:%s\\n' \"$line\""}))
    {
        std::cerr << "[FAIL] could not start interactive test shell\n";
        return 1;
    }
    if (!io_session.Resize(100, 30))
    {
        std::cerr << "[FAIL] could not resize PTY\n";
        return 1;
    }
    if (!io_session.WriteRaw("terminal-input\n"))
    {
        std::cerr << "[FAIL] could not write raw terminal input\n";
        return 1;
    }
    std::string response;
    const auto response_deadline = std::chrono::steady_clock::now() +
                                   std::chrono::seconds(2);
    while (std::chrono::steady_clock::now() < response_deadline &&
           response.find("received:terminal-input") == std::string::npos)
    {
        response += io_session.ReadAvailable(std::chrono::milliseconds(100));
    }
    io_session.Close();
    if (response.find("received:terminal-input") == std::string::npos)
    {
        std::cerr << "[FAIL] interactive shell did not receive raw terminal input: "
                  << response << '\n';
        return 1;
    }

    TerminalSession session;
    if (!session.Open({"sh", "-c", "trap '' TERM HUP; exec sleep 30"}))
    {
        std::cerr << "[FAIL] could not start test child\n";
        return 1;
    }

    const auto start = std::chrono::steady_clock::now();
    session.Close();
    const auto elapsed = std::chrono::steady_clock::now() - start;
    if (elapsed >= std::chrono::seconds(2))
    {
        std::cerr << "[FAIL] child shutdown exceeded bound\n";
        return 1;
    }

    std::cout << "[ok] terminal child is stopped within a bounded interval\n";
    return 0;
}
