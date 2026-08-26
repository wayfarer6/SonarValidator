#include <atomic>
#include <chrono>
#include <csignal>
#include <cstdio>
#include <ctime>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <memory>
#include <random>
#include <sstream>
#include <thread>

#include <sqlite3.h>

#include "database_service.hpp"
#include "prober_config.hpp"

namespace fs = std::filesystem;

namespace
{
const fs::path kDataDirectory = "/var/lib/sonar_validator_prober";
}

std::atomic<bool> g_running{true};

void signalHandler(int signum)
{
    (void)signum;
    g_running = false;
}

void CommunicationWorker(std::stop_token stop_token)
{
    while (!stop_token.stop_requested())
    {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}

void ConfigWorker(std::stop_token stop_token)
{
    while (!stop_token.stop_requested())
    {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}

void MonitorWorker(std::stop_token stop_token, DatabaseQueue& database_queue)
{
    (void)database_queue;
    while (!stop_token.stop_requested())
    {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}

struct DbHandler
{
    void operator()(sqlite3* database) const
    {
        if (database != nullptr)
        {
            sqlite3_close(database);
        }
    }
};

using DbHandle = std::unique_ptr<sqlite3, DbHandler>;

void DatabaseWorker(
    std::stop_token stop_token,
    DbHandle& db_handle,
    DatabaseQueue& database_queue)
{
    sqlite3* database = db_handle.get();
    if (database == nullptr)
    {
        return;
    }

    DatabaseTask task;
    while (database_queue.Pop(stop_token, task))
    {
        task(database);
    }
}

std::string GenerateAgentName()
{
    const auto now = std::chrono::system_clock::now();
    const auto milliseconds =
        std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()) % 1000;
    const std::time_t current_time = std::chrono::system_clock::to_time_t(now);

    std::tm local_time{};
    localtime_r(&current_time, &local_time);

    std::random_device random_device;
    std::mt19937 generator(random_device());
    std::uniform_int_distribution<int> distribution(0, 99999);

    std::ostringstream agent_name;
    agent_name << "agent-"
               << std::put_time(&local_time, "%Y%m%d-%H%M%S")
               << std::setfill('0') << std::setw(3) << milliseconds.count()
               << '-'
               << std::setw(5) << distribution(generator);
    return agent_name.str();
}

std::string TrimValue(std::string value)
{
    const std::size_t first = value.find_first_not_of(" \t\r\n");
    if (first == std::string::npos)
    {
        return {};
    }
    const std::size_t last = value.find_last_not_of("; \t\r\n");
    return value.substr(first, last - first + 1);
}

bool LoadConfig(const fs::path& path, ProberConfig& config)
{
    std::ifstream input(path);
    if (!input)
    {
        return false;
    }

    bool has_agent_name = false;
    bool has_kernel_name = false;
    bool has_distribution_name = false;
    bool has_device_type = false;
    bool has_memory_size = false;
    bool has_server_ip = false;
    bool has_server_port = false;
    bool has_architecture = false;
    std::string line;

    while (std::getline(input, line))
    {
        if (line.empty() || line.front() == '#')
        {
            continue;
        }

        const std::size_t separator = line.find('=');
        if (separator == std::string::npos)
        {
            return false;
        }

        const std::string key = TrimValue(line.substr(0, separator));
        const std::string value = TrimValue(line.substr(separator + 1));
        try
        {
            if (key == "AGENT_NAME")
            {
                config.SetAgentName(value);
                has_agent_name = !value.empty();
            }
            else if (key == "KERNEL_NAME")
            {
                config.SetKernelName(value);
                has_kernel_name = !value.empty();
            }
            else if (key == "DISTRIBUTION_NAME")
            {
                config.SetDistributionName(value);
                has_distribution_name = !value.empty();
            }
            else if (key == "MEMORY_SIZE_BYTES")
            {
                const unsigned long long memory_size = std::stoull(value);
                config.SetMemorySizeBytes(memory_size);
                has_memory_size = memory_size > 0;
            }
            else if (key == "SERVER_IP")
            {
                config.SetServerIpv4(value);
                has_server_ip = !value.empty();
            }
            else if (key == "SERVER_PORT")
            {
                const unsigned long port = std::stoul(value);
                if (port > 0 && port <= 65535)
                {
                    config.SetServerPort(static_cast<std::uint16_t>(port));
                    has_server_port = true;
                }
            }
            else if (key == "ARCHITECTURE")
            {
                config.SetArchitecture(value);
                has_architecture = !value.empty();
            }
            else if (key == "NODE_TYPE")
            {
                if (value == "Switch")
                {
                    config.SetDeviceType(ProberConfig::DeviceType::kSwitch);
                }
                else if (value == "VM")
                {
                    config.SetDeviceType(ProberConfig::DeviceType::kVirtualMachine);
                }
                else if (value == "Firewall")
                {
                    config.SetDeviceType(ProberConfig::DeviceType::kFirewall);
                }
                else if (value == "Router")
                {
                    config.SetDeviceType(ProberConfig::DeviceType::kRouter);
                }
                else
                {
                    return false;
                }
                has_device_type = true;
            }
        }
        catch (const std::exception&)
        {
            return false;
        }
    }

    return has_agent_name && has_kernel_name && has_distribution_name &&
           has_device_type && has_memory_size && has_server_ip &&
           has_server_port && has_architecture;
}

bool SaveConfig(const fs::path& path, const ProberConfig& config)
{
    const fs::path temporary_path = path.string() + ".tmp";
    std::ofstream output(temporary_path, std::ios::trunc);
    if (!output)
    {
        return false;
    }

    std::string node_type;
    switch (config.GetDeviceType())
    {
        case ProberConfig::DeviceType::kSwitch:
            node_type = "Switch";
            break;
        case ProberConfig::DeviceType::kVirtualMachine:
            node_type = "VM";
            break;
        case ProberConfig::DeviceType::kFirewall:
            node_type = "Firewall";
            break;
        case ProberConfig::DeviceType::kRouter:
            node_type = "Router";
            break;
    }

    output << "AGENT_NAME=" << config.GetAgentName() << '\n'
           << "KERNEL_NAME=" << config.GetKernelName() << '\n'
           << "DISTRIBUTION_NAME=" << config.GetDistributionName() << '\n'
           << "NODE_TYPE=" << node_type << '\n'
           << "MEMORY_SIZE_BYTES=" << config.GetMemorySizeBytes() << '\n'
           << "SERVER_IP=" << config.GetServerIpv4() << '\n'
           << "SERVER_PORT=" << config.GetServerPort() << '\n'
           << "ARCHITECTURE=" << config.GetArchitecture() << '\n';
    output.close();
    if (!output)
    {
        fs::remove(temporary_path);
        return false;
    }

    std::error_code rename_error;
    fs::rename(temporary_path, path, rename_error);
    if (rename_error)
    {
        fs::remove(temporary_path);
        return false;
    }
    return true;
}

bool InitializeConfig(const fs::path& path, ProberConfig& config)
{
    if (fs::exists(path) && LoadConfig(path, config))
    {
        return true;
    }

    ProberConfig initial_config(
        GenerateAgentName(), "", "", ProberConfig::DeviceType::kSwitch,
        0, "", 0);
    initial_config.DetectKernelName();
    initial_config.DetectDistributionName();
    initial_config.DetectMemorySizeBytes();
    initial_config.DetectArchitecture();
    initial_config.DetectServerIpv4();
    initial_config.DetectServerPort();
    const bool has_valid_device_type = initial_config.DetectDeviceType();

    if (initial_config.GetKernelName().empty() ||
        initial_config.GetDistributionName().empty() ||
        initial_config.GetMemorySizeBytes() == 0 ||
        initial_config.GetServerIpv4().empty() ||
        initial_config.GetServerPort() == 0 ||
        initial_config.GetArchitecture().empty() ||
        !has_valid_device_type)
    {
        return false;
    }

    config = initial_config;
    return SaveConfig(path, config);
}

int main()
{
    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);

    const fs::path config_file_path =
        kDataDirectory / "settings.conf";
    const fs::path sqlite_db_path =
        kDataDirectory / "prober_db.sqlite";

    std::error_code directory_error;
    fs::create_directories(kDataDirectory, directory_error);
    if (directory_error)
    {
        std::cerr << "Can't create application directory: "
                  << directory_error.message() << '\n';
        return 1;
    }

    ProberConfig config(
        GenerateAgentName(), "", "", ProberConfig::DeviceType::kSwitch,
        0, "", 0);
    if (!InitializeConfig(config_file_path, config))
    {
        std::cerr << "Configuration initialization failed\n";
        return 1;
    }

    sqlite3* raw_database = nullptr;
    const int db_result = sqlite3_open(sqlite_db_path.c_str(), &raw_database);
    DbHandle database(raw_database);

    if (db_result != SQLITE_OK)
    {
        std::cerr << "DB init failed: " << sqlite3_errmsg(raw_database) << '\n';
        return 1;
    }

    DatabaseQueue database_queue;
    std::jthread communication_thread(CommunicationWorker);
    std::jthread config_thread(ConfigWorker);
    std::jthread database_thread(
        DatabaseWorker, std::ref(database), std::ref(database_queue));
    std::jthread monitor_thread(MonitorWorker, std::ref(database_queue));

    while (g_running.load())
    {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    database_queue.Close();
    communication_thread.request_stop();
    config_thread.request_stop();
    database_thread.request_stop();
    monitor_thread.request_stop();

    std::cout << "[INFO] Clean shutdown complete.\n";
    return 0;
}
