#ifndef SONAR_VALIDATOR_PROBER_SWITCH_VENDOR_OPENVSWITCH_HPP_
#define SONAR_VALIDATOR_PROBER_SWITCH_VENDOR_OPENVSWITCH_HPP_

#include <string>
#include <vector>

#include "components/device/switch/switch_interface/switch.hpp"

// Open vSwitch `ovs-vsctl show` 출력을 Bridge/Port 트리로 파싱합니다.
class OpenVSwitchTopologyParser : public TopologyParser
{
public:
    std::vector<BridgeInfo> parse(const std::string& raw_output) const override;
};

#endif // SONAR_VALIDATOR_PROBER_SWITCH_VENDOR_OPENVSWITCH_HPP_
