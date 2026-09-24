#include "components/device/vm/ubuntu/vm_service.hpp"

#include <array>
#include <cstdio>
#include <fstream>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <system_error>
#include <vector>

#include <filesystem>

#include "components/network_object/nic.hpp"

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

int ReadFileInt(const std::filesystem::path& path)
{
    const std::string line = ReadFileLine(path);
    if (line.empty())
    {
        return 0;
    }
    try
    {
        return std::stoi(line);
    }
    catch (...)
    {
        return 0;
    }
}

std::vector<std::string> SplitWhitespace(const std::string& text)
{
    std::vector<std::string> tokens;
    std::string current;
    for (const char ch : text)
    {
        if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n')
        {
            if (!current.empty())
            {
                tokens.push_back(current);
                current.clear();
            }
            continue;
        }
        current.push_back(ch);
    }
    if (!current.empty())
    {
        tokens.push_back(current);
    }
    return tokens;
}

// `ip -br addr show` 한 줄에서 주소(CIDR) 토큰을 Nic::Address 로 변환합니다.
void AppendAddressesFromBriefLine(const std::string& line, std::vector<Nic::Address>& out)
{
    const std::vector<std::string> tokens = SplitWhitespace(line);
    // 형식: <name> <state> <addr/prefix> <addr/prefix> ...
    for (std::size_t i = 2; i < tokens.size(); ++i)
    {
        const std::string& cidr = tokens[i];
        const std::size_t slash = cidr.find('/');
        if (slash == std::string::npos)
        {
            continue;
        }

        Nic::Address address;
        address.address = cidr.substr(0, slash);
        address.prefix_len = cidr.substr(slash + 1);
        address.family = (address.address.find(':') != std::string::npos) ? "inet6" : "inet";
        out.push_back(std::move(address));
    }
}

} // namespace

nlohmann::json VmService::CollectNicStatus()
{
    using nlohmann::json;

    // `ip -br addr show` 결과를 인터페이스 이름별 주소로 미리 모읍니다.
    const std::string ip_brief = RunReadOnlyCommand("ip -br addr show");
    std::map<std::string, std::vector<Nic::Address>> addresses_by_name;
    {
        std::istringstream stream(ip_brief);
        std::string line;
        while (std::getline(stream, line))
        {
            const std::vector<std::string> tokens = SplitWhitespace(line);
            if (tokens.empty())
            {
                continue;
            }
            AppendAddressesFromBriefLine(line, addresses_by_name[tokens.front()]);
        }
    }

    // 커널이 노출하는 /sys/class/net 을 공통 Nic 객체로 옮깁니다.
    std::vector<Nic> nics;
    std::error_code error;
    for (const auto& entry : std::filesystem::directory_iterator("/sys/class/net", error))
    {
        const std::filesystem::path iface_path = entry.path();
        std::string name = iface_path.filename().string();
        if (name.empty())
        {
            continue;
        }

        Nic nic(std::move(name));
        nic.state = ReadFileLine(iface_path / "operstate");
        nic.mtu = ReadFileLine(iface_path / "mtu");
        nic.mac = ReadFileLine(iface_path / "address");
        nic.index = ReadFileInt(iface_path / "ifindex");
        if (const auto found = addresses_by_name.find(nic.name); found != addresses_by_name.end())
        {
            nic.addresses = found->second;
        }
        nics.push_back(std::move(nic));
    }

    json nic_status;
    json interfaces = json::array();
    for (const Nic& nic : nics)
    {
        interfaces.push_back(nic.ToJson());
    }
    nic_status["interfaces"] = interfaces;
    nic_status["ip_addr"] = ip_brief;
    nic_status["connections"] = RunReadOnlyCommand("ss -tn state established");
    return nic_status;
}
