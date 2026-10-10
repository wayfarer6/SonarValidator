#include "module/initializing_module/init.hpp"
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <unistd.h>

void Require(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
    std::cout << "[ok] " << message << '\n';
}

int main() {
    char temp[] = "/tmp/sonar-config-test-XXXXXX";
    const char* directory = mkdtemp(temp);
    Require(directory != nullptr, "temporary directory");
    const auto root = std::filesystem::path(directory);
    struct Cleanup { std::filesystem::path path; ~Cleanup() { std::filesystem::remove_all(path); } } cleanup{root};
    const auto defaults = root / "default.conf";
    setenv("SONAR_CONFIG_PATH", defaults.c_str(), 1);
    unsetenv("SONAR_TERMINAL_SHARED_SECRET");
    auto write_config = [&](const std::string& host, const std::string& port) {
        std::ofstream out(defaults);
        out << "  SERVER_IP = " << host << "; // endpoint\n"
            << "SERVER_PORT=" << port << "; # port\n"
            << "NODE_TYPE = Router; // device\n"
            << "AGENT_NAME=\"Cisco Router\";\n"
            << "TERMINAL_SHARED_SECRET=" << std::string(64, 'a') << " ; // Korean comment: 키\n";
    };
    ProberConfig config("", "", "", "", DeviceType::kSwitch, "", 0, "", 0);
    write_config("10.20.0.3", "3000");
    Require(AppInitializer::InitializeConfig(root / "settings.conf", config), "initial configuration");
    Require(config.GetTerminalSharedSecret() == std::string(64, 'a'), "key excludes semicolon, spaces and inline comment");
    Require(config.GetAgentName() == "Cisco Router", "quoted name and spaced keys");
    write_config("192.168.122.32", "3300");
    Require(AppInitializer::InitializeConfig(root / "settings.conf", config), "load cached configuration");
    Require(config.GetServerIpv4() == "192.168.122.32" && config.GetServerPort() == 3300,
            "deployment endpoint overrides stale settings cache on restart");
    setenv("SONAR_TERMINAL_SHARED_SECRET", ("  " + std::string(64, 'b') + " \n").c_str(), 1);
    config.DetectTerminalSharedSecret();
    Require(config.GetTerminalSharedSecret() == std::string(64, 'b'), "explicit environment overrides file secret and trims surrounding whitespace");
    unsetenv("SONAR_TERMINAL_SHARED_SECRET");
    write_config("192.168.122.32", "3000invalid");
    Require(!AppInitializer::InitializeConfig(root / "settings.conf", config), "invalid port cannot retain cached port");
}
