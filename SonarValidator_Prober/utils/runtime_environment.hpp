#ifndef SONAR_RUNTIME_ENVIRONMENT_HPP_
#define SONAR_RUNTIME_ENVIRONMENT_HPP_
#include <cstdlib>
#include <string>

namespace runtime_environment {
inline std::string DataDirectory() {
    const char* path = std::getenv("SONAR_DATA_DIR");
    return path && *path ? path : "/var/lib/sonar_validator_prober";
}
inline std::string ShellQuote(const std::string& value) {
    std::string result = "'";
    for (char ch : value) result += ch == '\'' ? "'\\''" : std::string(1, ch);
    return result + "'";
}
}
#endif
