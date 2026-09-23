#include "vm_service.hpp"

#include <array>
#include <cstdio>
#include <fstream>
#include <memory>
#include <string>
#include <system_error>

#include <filesystem>

namespace
{

struct PipeCloser
{
    void operator()(FILE* file) const
    {
        if (file != nullptr)
        {
            pclose(file);
        }
    }
};

std::string RunReadOnlyCommand(const std::string& command)
{
    std::unique_ptr<FILE, PipeCloser> pipe(popen(command.c_str(), "r"));
    if (!pipe)
    {
        return {};
    }

    std::array<char, 128> buffer;
    std::string result;
    while (std::fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr)
    {
        result += buffer.data();
    }
    return result;
}

std::string ReadFileLine(const std::filesystem::path& path)
{
    std::ifstream input(path);
    if (!input)
    {
        return {};
    }
    std::string line;
    std::getline(input, line);
    return line;
}

} // namespace

nlohmann::json VmService::CollectNicStatus()
{
    using nlohmann::json;

    json nic_status;

    json interfaces = json::array();
    std::error_code error;
    for (const auto& entry : std::filesystem::directory_iterator("/sys/class/net", error))
    {
        const std::filesystem::path iface_path = entry.path();
        json iface;
        iface["name"] = iface_path.filename().string();
        iface["state"] = ReadFileLine(iface_path / "operstate");
        iface["mtu"] = ReadFileLine(iface_path / "mtu");
        iface["address"] = ReadFileLine(iface_path / "address");
        interfaces.push_back(std::move(iface));
    }
    nic_status["interfaces"] = interfaces;
    nic_status["ip_addr"] = RunReadOnlyCommand("ip -br addr show");
    nic_status["connections"] = RunReadOnlyCommand("ss -tn state established");
    return nic_status;
}
