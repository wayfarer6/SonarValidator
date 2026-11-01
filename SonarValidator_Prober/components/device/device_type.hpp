#ifndef SONAR_VALIDATOR_PROBER_DEVICE_TYPE_HPP_
#define SONAR_VALIDATOR_PROBER_DEVICE_TYPE_HPP_

#include <cstdint>

enum class DeviceType : std::uint8_t
{
    kSwitch,
    kVirtualMachine,
    kFirewall,
    kRouter
};

#endif // SONAR_VALIDATOR_PROBER_DEVICE_TYPE_HPP_
