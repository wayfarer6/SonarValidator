#include "components/device/switch/switch_interface/switch.hpp"

#include "components/device/router/vendor/cisco/cisco8000v.hpp"
#include "components/device/switch/vendor/arista/aristavEOS.hpp"
#include "components/device/switch/vendor/openvswitch/openvswitch.hpp"

Switch::Switch(const std::string& name) : name_(name), routing_table_(), ports_(), bridges_() {}

Switch::~Switch() = default;

void Switch::setName(const std::string& name)
{
    name_ = name;
}

void Switch::addRoute(const std::string& destination, const std::string& next_hop)
{
    (void)destination;
    (void)next_hop;
}

void Switch::addPort(const std::string& destination, const std::string& port)
{
    (void)destination;
    (void)port;
}

void Switch::addVlan(const int subnet_id, const std::string& port)
{
    (void)subnet_id;
}

void Switch::printRoutes() const {}

void Switch::printPorts() const {}

void Switch::updateRoutingTable(const std::string& destination, const std::string& next_hop)
{
    (void)destination;
    (void)next_hop;
}

void Switch::updatePort(const std::string& destination, const std::string& port)
{
    (void)destination;
    (void)port;
}

// 벤더별 토폴로지 파서 구현은 각 벤더 디렉터리로 분리했습니다.
//   - components/device/switch/vendor/openvswitch/openvswitch.cpp
//   - components/device/switch/vendor/arista/aristavEOS.cpp
//   - components/device/router/vendor/cisco/cisco8000v.cpp
// 공통 파싱은 components/parser 의 ANTLR 문법이 담당합니다.

void Switch::loadTopology(const std::string& raw_output, SwitchVendor vendor)
{
    switch (vendor) {
        case SwitchVendor::OpenVSwitch: {
            OpenVSwitchTopologyParser parser;
            bridges_ = parser.parse(raw_output);
            break;
        }
        case SwitchVendor::CiscoIosXe: {
            CiscoTopologyParser parser;
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

// Cisco IOS-XE 토폴로지 파싱입니다.
// 과거에는 "PoC가 안된다" 는 이유로 주석 처리되어 있었지만,
// 수집/파싱 자체는 동작하므로 다시 연결했습니다.
// (정책 적용만 아직 지원하지 않습니다 — management_service 참고)
void Switch::parseCiscoSwitchTopology(const std::string& raw_output)
{
    loadTopology(raw_output, SwitchVendor::CiscoIosXe);
}

void Switch::parseAristaTopology(const std::string& raw_output)
{
    loadTopology(raw_output,SwitchVendor::AristavEOS);
}