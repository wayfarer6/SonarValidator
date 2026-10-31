#ifndef SONAR_VALIDATOR_PROBER_IOS_SNAPSHOT_HPP_
#define SONAR_VALIDATOR_PROBER_IOS_SNAPSHOT_HPP_

#include "components/terminal/ios_cli.hpp"
#include <cerrno>
#include <ctime>
#include <sstream>
#include <string>
#include <string_view>
#include <utility>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

namespace ios_snapshot
{
inline constexpr std::size_t kMaxBytes = 4 * 1024 * 1024;
inline constexpr int kMaxAgeSeconds = 300;

inline std::string_view FileName(const std::string& command)
{
    const auto normalized = ios_cli::Normalize(command);
    if (normalized == "show ip interface brief") return "show_ip_interface_brief.out";
    if (normalized == "show ip route") return "show_ip_route.out";
    if (normalized == "show ip arp") return "show_ip_arp.out";
    return {};
}

inline bool HasHeader(const std::string& command, const std::string& content)
{
    const auto normalized = ios_cli::Normalize(command);
    std::istringstream lines(content);
    std::string line;
    while (std::getline(lines, line))
    {
        std::istringstream fields(ios_cli::Normalize(line));
        std::string first, second;
        fields >> first >> second;
        if (normalized == "show ip interface brief" && first == "interface" && second == "ip-address") return true;
        if (normalized == "show ip route" && first == "codes:") return true;
        if (normalized == "show ip arp" && first == "protocol" && second == "address") return true;
    }
    return false;
}

inline std::string Read(const std::string& data_directory, const std::string& command,
                        std::time_t now = std::time(nullptr))
{
    const auto name = FileName(command);
    if (name.empty()) return {};
    const auto path = data_directory + "/ios_cli/" + std::string(name);
    const int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NONBLOCK | O_NOFOLLOW);
    if (fd < 0) return {};
    struct Descriptor { int fd; ~Descriptor() { close(fd); } } descriptor{fd};
    struct stat before{}, after{};
    if (fstat(fd, &before) != 0 || !S_ISREG(before.st_mode) || before.st_size <= 0 ||
        static_cast<unsigned long long>(before.st_size) > kMaxBytes ||
        now < before.st_mtime || std::difftime(now, before.st_mtime) > kMaxAgeSeconds)
        return {};

    std::string content(static_cast<std::size_t>(before.st_size), '\0');
    std::size_t offset = 0;
    while (offset < content.size())
    {
        const auto count = read(fd, content.data() + offset, content.size() - offset);
        if (count > 0) { offset += count; continue; }
        if (count < 0 && errno == EINTR) continue;
        return {};
    }
    if (fstat(fd, &after) != 0 || before.st_size != after.st_size ||
        before.st_mtim.tv_sec != after.st_mtim.tv_sec ||
        before.st_mtim.tv_nsec != after.st_mtim.tv_nsec ||
        before.st_ctim.tv_sec != after.st_ctim.tv_sec ||
        before.st_ctim.tv_nsec != after.st_ctim.tv_nsec ||
        content.back() != '\n' || content.find('\0') != std::string::npos ||
        !HasHeader(command, content)) return {};

    // IOS redirect writes the real table header on its first line.
    return ios_cli::CheckOutput(content).text;
}

template<class Execute>
ios_cli::OutputCheck ReadOrExecute(const std::string& directory, const std::string& script,
                                   Execute&& execute)
{
    if (auto cached = Read(directory, script); !cached.empty())
        return {std::move(cached), false, false};
    return ios_cli::CheckOutput(execute());
}
}
#endif
