#ifndef SONAR_VALIDATOR_PROBER_ROUTER_VENDOR_CISCO_8000V_HPP_
#define SONAR_VALIDATOR_PROBER_ROUTER_VENDOR_CISCO_8000V_HPP_

#include <string>
#include <vector>

#include "components/device/switch/switch_interface/switch.hpp"

// Cisco 8000v(IOS-XE) 토폴로지 파서입니다.
//
// 8000v 는 라우터이지만 `show running-config` 의 switchport 라인을 그대로 옮겨
// 스위치 토폴로지(Bridge/Port) 형태로 제공합니다.
class CiscoTopologyParser : public TopologyParser
{
public:
    std::vector<BridgeInfo> parse(const std::string& raw_output) const override;
};

#endif // SONAR_VALIDATOR_PROBER_ROUTER_VENDOR_CISCO_8000V_HPP_

