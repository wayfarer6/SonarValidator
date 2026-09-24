
#ifndef SONAR_VALIDATOR_PROBER_SWITCH_VENDOR_ARISTAV_EOS_HPP_
#define SONAR_VALIDATOR_PROBER_SWITCH_VENDOR_ARISTAV_EOS_HPP_

#include <string>
#include <vector>

#include "components/device/switch/switch_interface/switch.hpp"

// Arista EOS 스위치의 토폴로지 파서입니다.
class AristaTopologyParser : public TopologyParser
{
public:
    std::vector<BridgeInfo> parse(const std::string& raw_output) const override;
};

#endif // SONAR_VALIDATOR_PROBER_SWITCH_VENDOR_ARISTAV_EOS_HPP_