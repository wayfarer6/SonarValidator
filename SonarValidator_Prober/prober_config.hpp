#ifndef SONAR_VALIDATOR_PROBER_PROBER_CONFIG_HPP_
#define SONAR_VALIDATOR_PROBER_PROBER_CONFIG_HPP_

#include <cstdint>
#include <string>

class ProberConfig
{
public:
    enum class DeviceType
    {
        kSwitch,
        kVirtualMachine,
        kFirewall,
        kRouter
    };

    ProberConfig(std::string agent_name,
                 std::string kernel_name,
                 std::string distribution_name,
                 DeviceType device_type,
                 std::uint64_t memory_size_bytes,
                 std::string server_ipv4,
                 std::uint16_t server_port);


    const std::string& GetAgentName() const;
    const std::string& GetKernelName() const;
    const std::string& GetDistributionName() const;
    DeviceType GetDeviceType() const;
    std::uint64_t GetMemorySizeBytes() const;
    const std::string& GetServerIpv4() const;
    std::uint16_t GetServerPort() const;

    void SetAgentName(std::string agent_name);
    void SetKernelName(std::string kernel_name);
    void SetDistributionName(std::string distribution_name);
    void SetDeviceType(DeviceType device_type);
    void SetMemorySizeBytes(std::uint64_t memory_size_bytes);
    void SetServerIpv4(std::string server_ipv4);
    void SetServerPort(std::uint16_t server_port);

    const std::string& GetArchitecture() const;
    void SetArchitecture(std::string architecture);

    void DetectKernelName();
    void DetectDistributionName();
    void DetectMemorySizeBytes();
    void DetectArchitecture();
    void DetectServerIpv4();
    void DetectServerPort();
    bool DetectDeviceType();


private:
    std::string agent_name_;
    std::string kernel_name_;
    std::string distribution_name_;
    DeviceType device_type_;
    std::uint64_t memory_size_bytes_;
    std::string server_ipv4_;
    std::uint16_t server_port_;
    std::string architecture_;
};

#endif  // SONAR_VALIDATOR_PROBER_PROBER_CONFIG_HPP_