#include "components/device/switch/switch_interface/switch.hpp"

#include "components/device/switch/vendor/arista/aristavEOS.hpp"
#include "components/device/switch/vendor/openvswitch/openvswitch.hpp"

Switch::Switch(const std::string& name) : name_(name), bridges_() {}

Switch::~Switch() = default;

void Switch::setName(const std::string& name)
{
    name_ = name;
}

// 벤더별 토폴로지 파서 구현은 각 벤더 디렉터리로 분리했습니다.
//   - components/device/switch/vendor/openvswitch/openvswitch.cpp
//   - components/device/switch/vendor/arista/aristavEOS.cpp
// 공통 파싱은 components/parser 의 ANTLR 문법이 담당합니다.

void Switch::loadTopology(const std::string& raw_output, SwitchVendor vendor)
{
    switch (vendor) {
        case SwitchVendor::OpenVSwitch: {
            OpenVSwitchTopologyParser parser;
            bridges_ = parser.parse(raw_output);
            break;
        }
        case SwitchVendor::AristavEOS: {
            AristaTopologyParser parser;
            bridges_ = parser.parse(raw_output);
            break;
        }
    }
}

void Switch::parseOpenVSwitchTopology(const std::string& raw_output)
{
    loadTopology(raw_output, SwitchVendor::OpenVSwitch);
}

void Switch::parseAristaTopology(const std::string& raw_output)
{
    loadTopology(raw_output,SwitchVendor::AristavEOS);
}