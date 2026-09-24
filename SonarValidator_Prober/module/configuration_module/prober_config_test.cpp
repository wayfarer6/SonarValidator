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

    assert(config.GetAgentName() == "test-agent");
    assert(config.GetKernelName() == "test-kernel");
    assert(config.GetDistributionName() == "test-distribution");
    assert(config.GetDeviceType() == DeviceType::kRouter);
    assert(config.GetMemorySizeBytes() == 4096);
    assert(config.GetServerIpv4() == "192.0.2.1");
    assert(config.GetServerPort() == 8080);
    assert(config.GetArchitecture() == "test-architecture");

    config.DetectKernelName();
    config.DetectDistributionName();
    config.DetectMemorySizeBytes();
    config.DetectArchitecture();

    assert(!config.GetKernelName().empty());
    assert(!config.GetDistributionName().empty());
    assert(config.GetMemorySizeBytes() > 0);
    assert(!config.GetArchitecture().empty());
}
