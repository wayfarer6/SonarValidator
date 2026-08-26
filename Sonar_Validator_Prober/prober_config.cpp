#include "prober_config.hpp"
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <iostream>
#include <sys/utsname.h>
#include <sys/sysinfo.h>
#include <memory>
#include <filesystem>

namespace
{
constexpr const char* kDefaultConfigPath =
    "/etc/sonar_validator_prober/default.conf";

std::string RemoveQuotes(std::string value)
{
    if (value.size() >= 2 && value.front() == '"' && value.back() == '"')
    {
        value = value.substr(1, value.size() - 2);
    }
    return value;
}

struct DConfHandler
{
    void operator()(FILE* file) const
    {
        if (file != nullptr)
        {
            std::fclose(file);
        }
    }

};

using DConfFile = std::unique_ptr<FILE, DConfHandler>;

std::string ReadDefaultValue(const char* key)
{
    DConfFile default_config(std::fopen(kDefaultConfigPath, "r"));
    if (!default_config)
    {
        return {};
    }

    char buffer[256];
    while (std::fgets(buffer, sizeof(buffer), default_config.get()) != nullptr)
    {
        std::string line(buffer);
        const std::string prefix = std::string(key) + "=";
        if (line.rfind(prefix, 0) != 0)
        {
            continue;
        }

        std::string value = line.substr(prefix.size());
        while (!value.empty() &&
               (value.back() == '\n' || value.back() == '\r' || value.back() == ';' ||
                value.back() == ' ' || value.back() == '\t'))
        {
            value.pop_back();
        }
        return value;
    }
    return {};
}
}



ProberConfig::ProberConfig(
    std::string agent_name,
    std::string kernel_name,
    std::string distribution_name,
    DeviceType device_type,
    std::uint64_t memory_size_bytes,
    std::string server_ipv4,
    std::uint16_t server_port)
        : agent_name_(std::move(agent_name)),
            kernel_name_(std::move(kernel_name)),
      distribution_name_(std::move(distribution_name)),
      device_type_(device_type),
      memory_size_bytes_(memory_size_bytes),
      server_ipv4_(std::move(server_ipv4)),
      server_port_(server_port)
{
}

const std::string& ProberConfig::GetAgentName() const { return agent_name_; }

const std::string& ProberConfig::GetKernelName() const { return kernel_name_; }

const std::string& ProberConfig::GetDistributionName() const
{
    return distribution_name_;
}

ProberConfig::DeviceType ProberConfig::GetDeviceType() const { return device_type_; }

std::uint64_t ProberConfig::GetMemorySizeBytes() const { return memory_size_bytes_; }

const std::string& ProberConfig::GetServerIpv4() const { return server_ipv4_; }

std::uint16_t ProberConfig::GetServerPort() const { return server_port_; }

const std::string& ProberConfig::GetArchitecture() const { return architecture_; }

void ProberConfig::SetAgentName(std::string agent_name)
{
    agent_name_ = std::move(agent_name);
}

void ProberConfig::SetKernelName(std::string kernel_name)
{
    kernel_name_ = std::move(kernel_name);
}

void ProberConfig::SetDistributionName(std::string distribution_name)
{
    distribution_name_ = std::move(distribution_name);
}

void ProberConfig::SetDeviceType(DeviceType device_type) { device_type_ = device_type; }

void ProberConfig::SetMemorySizeBytes(std::uint64_t memory_size_bytes)
{
    memory_size_bytes_ = memory_size_bytes;
}

void ProberConfig::SetServerIpv4(std::string server_ipv4)
{
    server_ipv4_ = std::move(server_ipv4);
}

void ProberConfig::DetectServerIpv4()
{
    server_ipv4_ = ReadDefaultValue("SERVER_IP");
}

void ProberConfig::DetectServerPort()
{
    const std::string port = ReadDefaultValue("SERVER_PORT");
    try
    {
        const unsigned long value = std::stoul(port);
        if (value > 0 && value <= 65535)
        {
            server_port_ = static_cast<std::uint16_t>(value);
        }
    }
    catch (const std::exception&)
    {
        std::cerr << "Invalid SERVER_PORT in default configuration\n";
    }
}

bool ProberConfig::DetectDeviceType()
{
    const std::string node_type = ReadDefaultValue("NODE_TYPE");
    if (node_type == "Router")
    {
        device_type_ = DeviceType::kRouter;
    }
    else if (node_type == "Switch")
    {
        device_type_ = DeviceType::kSwitch;
    }
    else if (node_type == "VM")
    {
        device_type_ = DeviceType::kVirtualMachine;
    }
    else if (node_type == "Firewall")
    {
        device_type_ = DeviceType::kFirewall;
    }
    else
    {
        std::cerr << "Invalid NODE_TYPE in default configuration\n";
        return false;
    }
    return true;
}

void ProberConfig::SetServerPort(std::uint16_t server_port) { server_port_ = server_port; }

void ProberConfig::SetArchitecture(std::string architecture)
{
    architecture_ = std::move(architecture);
}

void ProberConfig::DetectKernelName()
{
    struct utsname system_info;
    if (uname(&system_info) != 0)
    {
        std::cerr << "uname failed: " << std::strerror(errno) << '\n';
        return;
    }

    kernel_name_ = system_info.release;
}

void ProberConfig::DetectDistributionName()
{
    std::ifstream os_release_file("/etc/os-release");
    std::string line;
    while (std::getline(os_release_file, line))
    {
        constexpr const char *kNamePrefix = "PRETTY_NAME=";
        if (line.rfind(kNamePrefix, 0) == 0)
        {
            distribution_name_ = RemoveQuotes(line.substr(std::strlen(kNamePrefix)));
            return;
        }
    }

    std::cerr << "Unable to read distribution name from /etc/os-release\n";
}

void ProberConfig::DetectMemorySizeBytes()
{
    struct sysinfo memory_info;
    if (sysinfo(&memory_info) != 0)
    {
        std::cerr << "sysinfo failed: " << std::strerror(errno) << '\n';
        return;
    }

    memory_size_bytes_ = static_cast<std::uint64_t>(memory_info.totalram) *
                         memory_info.mem_unit;
}

void ProberConfig::DetectArchitecture()
{
    struct utsname system_info;
    if (uname(&system_info) != 0)
    {
        std::cerr << "uname failed: " << std::strerror(errno) << '\n';
        return;
    }
    architecture_ = system_info.machine;
}