#include "components/terminal/terminal_session.hpp"

#include <chrono>
#include <iostream>

int main()
{
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
