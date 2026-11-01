#ifndef SONAR_ARISTA_CLI_HPP_
#define SONAR_ARISTA_CLI_HPP_

#include <algorithm>
#include <cctype>
#include <string>
#include <vector>
#include "utils/runtime_environment.hpp"

namespace arista_cli {
inline std::string BuildCommand(const std::vector<std::string>& commands)
{
    if (commands.empty()) return {};
    std::string script;
    for (const auto& command : commands)
    {
        if (command.empty() || command.find_first_of("\r\n") != std::string::npos ||
            command.find('\0') != std::string::npos) return {};
        script += command + '\n';
    }
    return "FastCli -p 15 -M -c " + runtime_environment::ShellQuote(script) + " 2>&1";
}

inline bool ValidName(const std::string& name)
{
    return !name.empty() && name.size() <= 64 &&
        std::isalnum(static_cast<unsigned char>(name.front())) &&
        std::all_of(name.begin(), name.end(), [](unsigned char ch) {
            return std::isalnum(ch) || ch == '_' || ch == '-' || ch == '.' || ch == '/';
        });
}

inline bool ValidVlan(const std::string& value)
{
    return !value.empty() && value.size() <= 4 &&
        std::all_of(value.begin(), value.end(), [](unsigned char ch) { return std::isdigit(ch); }) &&
        std::stoi(value) >= 1 && std::stoi(value) <= 4094;
}
} // namespace arista_cli
#endif
