#include "module/configuration_module/prober_config.hpp"
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <sys/utsname.h>
#include <sys/sysinfo.h>
#include <memory>
#include <cstdlib>
#include <filesystem>
#include "components/backend_communication/network.hpp"
namespace
{
// 기본 설정 파일 경로.
//  root 로 설치한 경우 /etc/... 를 쓰지만, root 가 아닌 환경(vEOS bash,
//  사용자 홈 배포 등)에서는 SONAR_CONFIG_PATH 로 지정할 수 있게 한다.
constexpr const char* kDefaultConfigPath =
    "/etc/sonar_validator_prober/default.conf"; // 하드코딩이 위험함 아마 최초 설치시 환경변수에 지정된 경로에 붙게 고처야 

const char* ResolveConfigPath()
{
    if (const char* from_env = std::getenv("SONAR_CONFIG_PATH")) // 쉘의 기본 환경 변수를 가져옴
    {
        if (from_env[0] != '\0')
        {
            return from_env;
        }
    }
    return kDefaultConfigPath;
}

std::string RemoveQuotes(std::string value) // 유틸함수 
{
    if (value.size() >= 2 && value.front() == '"' && value.back() == '"')
    {
        value = value.substr(1, value.size() - 2);
    }
    return value;
}

struct DConfHandler // 핸들러.
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
    DConfFile default_config(std::fopen(ResolveConfigPath(), "r"));
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
        { // 읽을때 tab, 줄바꿈 공백 문자의 경우에는 무시 하는 기능
            value.pop_back();
        }
        return value;
    }
    return {};
}

// 주어진 실행 파일이 PATH에 존재하는지 확인합니다. (제품군 탐지용)
bool CommandExists(const char* command)
{ // 내가 벡엔드에서 제품마다 agent가 사전 설정된 걸 다운받으라고 하기에 이러는 거임 ()
    const std::string query = std::string("command -v ") + command + " >/dev/null 2>&1";
    return std::system(query.c_str()) == 0;
}
}



// 모든 필드를 받아 초기화합니다. 문자열은 std::move로 복사 없이 옮깁니다.
ProberConfig::ProberConfig(
    std::string agent_id,
    std::string agent_name,
    std::string kernel_name,
    std::string distribution_name,
    DeviceType device_type,
    std::string product_name,
    std::uint64_t memory_size_bytes,
    std::string server_ipv4,
    std::uint16_t server_port)
        :  agent_id_(std::move(agent_id)),
            agent_name_(std::move(agent_name)),
            kernel_name_(std::move(kernel_name)),
      distribution_name_(std::move(distribution_name)),
      device_type_(device_type),
      product_name_(std::move(product_name)),
      memory_size_bytes_(memory_size_bytes),
      server_ipv4_(std::move(server_ipv4)),
      server_port_(server_port)
{
}

const std::string& ProberConfig::GetAgentId() const { return agent_id_; }

const std::string& ProberConfig::GetAgentName() const { return agent_name_; }

const std::string& ProberConfig::GetKernelName() const { return kernel_name_; }

const std::string& ProberConfig::GetProductName() const { return product_name_; }



const std::string& ProberConfig::GetDistributionName() const
{
    return distribution_name_;
}

DeviceType ProberConfig::GetDeviceType() const { return device_type_; }

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

// 설치 시 작성된 기본 설정(default.conf)에서 서버 IP를 읽어옵니다.
void ProberConfig::DetectServerIpv4()
{
    server_ipv4_ = ReadDefaultValue("SERVER_IP");
}

// 기본 설정에서 서버 포트를 읽고 1~65535 범위인지 검증합니다.
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

// 기본 설정의 AGENT_NAME 을 읽어옵니다.
// 배포 스크립트가 이름을 써 두면 그 이름을 그대로 쓰고, 없으면 빈 문자열을
// 돌려줍니다(호출자가 자동 생성 이름으로 대체).
std::string ProberConfig::DetectAgentName()
{
    return RemoveQuotes(ReadDefaultValue("AGENT_NAME"));
}

// 기본 설정의 NODE_TYPE을 장치 유형으로 변환합니다.
// 유효하지 않은 값이면 false를 반환합니다.
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

// uname()으로 커널 버전을 읽어옵니다.
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
        /* 참고
        PRETTY_NAME은 리눅스 운영 체제에서 사용자에게 친숙하게 보여주기 
        위한 전체 운영 체제 이름과 버전 정보를 담고 있는 os-release 파일의 표준 변수
        출처 : Freedesktop
        */
        if (line.rfind(kNamePrefix, 0) == 0)
        {
            distribution_name_ = RemoveQuotes(line.substr(std::strlen(kNamePrefix)));
            return;
        }
    }

    // ⚠️ 배포판 이름을 못 읽어도 <b>치명적이지 않습니다.</b>
    //
    // 여기서 값을 비워 두면 PrepareRuntime 의 필수값 검증에 걸려 프로버가
    // 아예 기동하지 못합니다. 그런데 배포판 이름은 "표시용 정보" 일 뿐이고,
    // 실제 동작(장치 유형·제품명·서버 접속)은 다른 값으로 결정됩니다.
    //
    // 실측: Open vSwitch 스위치 컨테이너에는 /etc/os-release 가 없습니다.
    //   (Alpine 계열이지만 파일이 없는 이미지)
    //   그 결과 스위치 5대가 전부
    //   "Runtime initialization failed" 로 죽어 서버에 나타나지 않았습니다.
    //
    // 그래서 커널 이름을 대신 넣습니다. 커널은 uname() 으로 항상 얻을 수 있고,
    // 화면에서 "이 장치가 무엇인지" 를 구분하는 데 충분합니다.
    if (distribution_name_.empty() && !kernel_name_.empty())
    {
        distribution_name_ = "Unknown (" + kernel_name_ + ")";
    }

    std::cerr << "Unable to read distribution name from /etc/os-release"
                 "; falling back to \"" << distribution_name_ << "\"\n";
}

// sysinfo()로 총 메모리 크기를 읽어옵니다.
void ProberConfig::DetectMemorySizeBytes()
{
    struct sysinfo memory_info;
    if (sysinfo(&memory_info) != 0)
    {
        std::cerr << "sysinfo failed: " << std::strerror(errno) << '\n';
        return;
    }

    // 운영체제 배울때 기본 페이지 크기는 4kb지만 리눅스 설정마다 다를수 있다고 함. (memory_info.mem_unit 사용하는 이유)
    memory_size_bytes_ = static_cast<std::uint64_t>(memory_info.totalram) *
                         memory_info.mem_unit;
}

// uname()으로 CPU 아키텍처를 읽어옵니다.
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

void ProberConfig::SetProduct(std::string product_name)
{
    product_name_ = std::move(product_name);
}

void ProberConfig::DetectProductName()
{
    if (CommandExists("dohost"))
    {
        product_name_ = "Cisco 8000v";
        return;
    }
    if (CommandExists("ovs-vsctl"))
    {
        product_name_ = "OpenVSwitch";
        return;
    }
    if (CommandExists("FastCli"))
    {
        product_name_ = "Arista";
        return;
    }
    if (CommandExists("vtysh"))
    {
        product_name_ = "FRR";
        return;
    }
    if (distribution_name_.find("Ubuntu") != std::string::npos)
    {
        product_name_ = "Ubuntu";
        return;
    }
    if (CommandExists("nft"))
    {
        product_name_ = "nftables";
        return;
    }
    product_name_ = "Unknown";
}