#include "module/initializing_module/init.hpp"
#include <iostream>
#include <chrono>
#include <ctime>
#include <fstream>
#include <iomanip>
#include <random>
#include <sstream>
#include <system_error>
#include "database/schema.hpp"
#include "components/backend_communication/network.hpp"
namespace fs = std::filesystem;

namespace
{

    // 문자열 양 끝의 공백과 세미콜론을 제거합니다. (설정 파일 파싱용)
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

    // "agent-YYYYMMDD-HHMMSS-mmm-난수" 형식의 고유 에이전트 이름을 생성합니다.
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

    // KEY=VALUE 형식의 설정 파일을 읽어 config에 채웁니다.
    // 모든 필수 항목이 존재해야 true를 반환합니다.
    bool LoadConfig(const fs::path &path, ProberConfig &config)
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
                        config.SetDeviceType(DeviceType::kSwitch);
                    }
                    else if (value == "VM")
                    {
                        config.SetDeviceType(DeviceType::kVirtualMachine);
                    }
                    else if (value == "Firewall")
                    {
                        config.SetDeviceType(DeviceType::kFirewall);
                    }
                    else if (value == "Router")
                    {
                        config.SetDeviceType(DeviceType::kRouter);
                    }
                    else
                    {
                        return false;
                    }
                    has_device_type = true;
                }
            }
            catch (const std::exception &)
            {
                return false;
            }
        }

        return has_agent_name && has_kernel_name && has_distribution_name &&
               has_device_type && has_memory_size && has_server_ip &&
               has_server_port && has_architecture;
    }

    // config 내용을 임시 파일에 쓴 뒤 원자적으로(rename) 저장합니다.
    bool SaveConfig(const fs::path &path, const ProberConfig &config)
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
        case DeviceType::kSwitch:
            node_type = "Switch";
            break;
        case DeviceType::kVirtualMachine:
            node_type = "VM";
            break;
        case DeviceType::kFirewall:
            node_type = "Firewall";
            break;
        case DeviceType::kRouter:
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

} // namespace

// 데이터 디렉터리를 생성합니다. 이미 존재하면 성공으로 처리합니다.
bool AppInitializer::EnsureDataDirectory(const fs::path &path)
{
    std::error_code error;
    fs::create_directories(path, error);
    if (error)
    {
        // 권한 부족 등으로 만들지 못한 경우, 원인을 남겨 진단을 돕습니다.
        std::cerr << "Data directory unavailable: " << path
                  << " (" << error.message() << ")" << '\n';
        return false;
    }
    std::cout << "Data directory ready: " << path << '\n';
    return true;
}

// 설정 파일을 로드하고, 없으면 시스템 정보를 탐지해 새로 생성·저장합니다.
bool AppInitializer::InitializeConfig(const fs::path &path, ProberConfig &config)
{

    try
    {
        if (fs::exists(path) && LoadConfig(path, config))
        {
            config.DetectProductName();  // 제품군은 매번 재탐지합니다.
            return true;
        }
    }
    catch (const std::exception &e)
    {
        std::cout << " Cant create config directories because of authority" << '\n';
        std::cout << "Error: " << e.what() << '\n';
        return 1;
    }

    // 에이전트 이름은 한 번만 생성해 agent_id 와 agent_name 에 함께 넣습니다.
    //  agent_name 이 비어 있으면 LoadConfig() 가 실패해
    //  매 기동마다 설정이 새로 생성되고(에이전트 ID 변경),
    //  텔레메트리/DB 의 agent 컬럼도 빈 값이 됩니다.
    const std::string generated_agent_name = GenerateAgentName();
    ProberConfig initial_config(
        generated_agent_name, generated_agent_name, "", "", DeviceType::kSwitch,
        "", 0, "", 0);
    initial_config.DetectKernelName();
    initial_config.DetectDistributionName();
    initial_config.DetectMemorySizeBytes();
    initial_config.DetectArchitecture();
    initial_config.DetectServerIpv4();
    initial_config.DetectServerPort();
    const bool has_valid_device_type = initial_config.DetectDeviceType();
    initial_config.DetectProductName();

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

// SQLite 핸들이 유효한지 확인하고 필요한 테이블을 생성합니다.
bool AppInitializer::InitializeDatabase(
    const fs::path &database_path,
    const ProberConfig &config,
    DbHandle &database_handle)
{
    (void)config;
    if (database_path.empty() || database_handle == nullptr)
    {
        return false;
    }

    // 텔레메트리/NIC 상태, key-value 설정, 수집 네트워크 상태(라우팅/NIC/VLAN/
    // 트렁크/ARP) 테이블을 보장합니다.
    // DDL 은 database/schema.cpp 한 곳에만 두고 여기서는 실행만 합니다(멱등).
    const std::string& create_tables_sql = database_schema::CreateTablesSql();

    char *error_message = nullptr;
    const int result = sqlite3_exec(
        database_handle.get(), create_tables_sql.c_str(), nullptr, nullptr, &error_message);
    if (result != SQLITE_OK)
    {
        if (error_message != nullptr)
        {
            sqlite3_free(error_message);
        }
        return false;
    }

    return true;
}

// 런타임 준비 전체 흐름: 디렉터리 → 설정 → DB 템플릿 복사 → SQLite 열기 → 스키마 생성.
bool AppInitializer::PrepareRuntime(
    const fs::path &data_directory,
    const fs::path &config_file_path,
    const fs::path &sqlite_db_path,
    const fs::path &sqlite_template_path,
    ProberConfig &config,
    DbHandle &database_handle)
{
    if (!EnsureDataDirectory(data_directory))
    {
        return false;
    }

    if (!InitializeConfig(config_file_path, config))
    {
        return false;
    }

    bool should_copy_database_template = !fs::exists(sqlite_db_path);
    if (!should_copy_database_template)
    {
        std::error_code file_size_error;
        should_copy_database_template =
            fs::file_size(sqlite_db_path, file_size_error) == 0 &&
            !file_size_error;
    }

    if (should_copy_database_template && fs::exists(sqlite_template_path))
    {
        std::error_code remove_error;
        fs::remove(sqlite_db_path, remove_error);
        if (remove_error)
        {
            return false;
        }

        std::error_code copy_error;
        fs::copy_file(sqlite_template_path, sqlite_db_path, fs::copy_options::none, copy_error);
        if (copy_error)
        {
            return false;
        }
    }

    sqlite3 *raw_database = nullptr;
    const int open_result = sqlite3_open(sqlite_db_path.c_str(), &raw_database);
    database_handle.reset(raw_database);

    if (open_result != SQLITE_OK)
    {
        if (raw_database != nullptr)
        {
            sqlite3_close(raw_database);
        }
        database_handle.reset();
        return false;
    }

    return InitializeDatabase(sqlite_db_path, config, database_handle);
}
