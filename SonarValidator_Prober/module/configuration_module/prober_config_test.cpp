#include <cassert>
#include <cstdint>
#include <string>

#include "module/configuration_module/prober_config.hpp"

int main()
{
    ProberConfig config(
        "",
        "",
        "",
        "",
        DeviceType::kSwitch,
        "",
        0,
        "",
        0);

    config.SetAgentName("test-agent");
    config.SetKernelName("test-kernel");
    config.SetDistributionName("test-distribution");
    config.SetDeviceType(DeviceType::kRouter);
    config.SetMemorySizeBytes(4096);
    config.SetServerIpv4("192.0.2.1");
    config.SetServerPort(8080);
    config.SetArchitecture("test-architecture");
    config.SetManagementPrefixes("172.16.255.0/24,10.0.0.0/24");

    assert(config.GetAgentName() == "test-agent");
    assert(config.GetKernelName() == "test-kernel");
    assert(config.GetDistributionName() == "test-distribution");
    assert(config.GetDeviceType() == DeviceType::kRouter);
    assert(config.GetMemorySizeBytes() == 4096);
    assert(config.GetServerIpv4() == "192.0.2.1");
    assert(config.GetServerPort() == 8080);
    assert(config.GetArchitecture() == "test-architecture");
    // ⚠️ 관리 대역은 설정값입니다(하드코딩 아님). 여러 대역을 담을 수 있습니다.
    assert(config.GetManagementPrefixes() == "172.16.255.0/24,10.0.0.0/24");

    config.DetectKernelName();
    config.DetectDistributionName();
    config.DetectMemorySizeBytes();
    config.DetectArchitecture();

    assert(!config.GetKernelName().empty());
    assert(!config.GetDistributionName().empty());
    assert(config.GetMemorySizeBytes() > 0);
    assert(!config.GetArchitecture().empty());
}
