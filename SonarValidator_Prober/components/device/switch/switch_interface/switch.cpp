#include "components/device/switch/switch_interface/switch.hpp"

#include "components/device/switch/vendor/arista/aristavEOS.hpp"
#include "components/device/switch/vendor/openvswitch/openvswitch.hpp"

Switch::Switch(const std::string& name) : name_(name), bridges_() {}

Switch::~Switch() = default;

void Switch::setName(const std::string& name)
{
    name_ = name;
}


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
