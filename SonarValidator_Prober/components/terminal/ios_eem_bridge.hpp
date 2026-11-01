#ifndef SONAR_IOS_EEM_BRIDGE_HPP_
#define SONAR_IOS_EEM_BRIDGE_HPP_
#include <chrono>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <stop_token>
#include <string>
#include <thread>
#include <vector>
#include <fcntl.h>
#include <sys/file.h>
#include <unistd.h>
#include "components/terminal/ios_cli.hpp"

namespace ios_eem_bridge {
inline std::string Directory() {
    const char* value = std::getenv("SONAR_IOS_EEM_DIR");
    return value && *value ? value : "/bootflash/guest-share/sonar-cli";
}
inline bool Available() { return std::filesystem::is_directory(Directory()); }
inline std::string Execute(const std::vector<std::string>& commands, std::stop_token stop,
                           std::chrono::milliseconds timeout = std::chrono::seconds(12)) {
    if (stop.stop_requested() || commands.empty() || commands.size() > 64 || !Available()) return {};
    for (const auto& cmd : commands)
        if (cmd.empty() || cmd.size() > 512 || cmd.find_first_of("\r\n") != std::string::npos
                || cmd.find('\0') != std::string::npos) return {};
    const auto dir = Directory();
    int lock = open((dir + "/client.lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (lock < 0) return {};
    struct Guard { int fd; ~Guard(){flock(fd, LOCK_UN); close(fd);} } guard{lock};
    const auto deadline = std::chrono::steady_clock::now() + timeout;
    while (flock(lock, LOCK_EX | LOCK_NB) != 0) {
        if (stop.stop_requested() || std::chrono::steady_clock::now() >= deadline) return {};
        std::this_thread::sleep_for(std::chrono::milliseconds(25));
    }
    const auto token = std::to_string(getpid()) + "-" + std::to_string(
            std::chrono::steady_clock::now().time_since_epoch().count());
    const auto temp = dir + "/request.tmp";
    {
        std::ofstream out(temp, std::ios::trunc);
        out << token << '\n';
        for (const auto& cmd : commands) out << cmd << '\n';
        if (std::any_of(commands.begin(), commands.end(), ios_cli::EntersConfigMode)
                && std::none_of(commands.begin(), commands.end(), [](const auto& c){return ios_cli::Normalize(c)=="end";}))
            out << "end\n";
        out.close();
        if (!out || rename(temp.c_str(), (dir + "/request").c_str()) != 0) return {};
    }
    while (!stop.stop_requested() && std::chrono::steady_clock::now() < deadline) {
        std::error_code ec;
        const auto size = std::filesystem::file_size(dir + "/response", ec);
        if (!ec && size > 0 && size <= 4 * 1024 * 1024) {
            std::ifstream in(dir + "/response");
            std::string id, status;
            std::getline(in, id); std::getline(in, status);
            if (id == token) {
                std::string output((std::istreambuf_iterator<char>(in)), {});
                const std::string end = "END " + token + "\n";
                if (output.ends_with(end)) {
                    output.resize(output.size() - end.size());
                    return status == "OK" ? ios_cli::CheckOutput(output).text : std::string{};
                }
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(25));
    }
    // Remove only our request; never consume another client's response.
    std::ifstream pending(dir + "/request"); std::string id; std::getline(pending,id);
    if (id == token) { std::error_code ec; std::filesystem::remove(dir + "/request", ec); }
    return {};
}
}
#endif
