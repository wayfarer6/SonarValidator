#include "switch.hpp"

#include <cctype>
#include <sstream>
#include <utility>

namespace {

std::string Trim(const std::string& input)
{
    std::size_t start = 0;
    while (start < input.size() && std::isspace(static_cast<unsigned char>(input[start]))) {
        ++start;
    }

    std::size_t end = input.size();
    while (end > start && std::isspace(static_cast<unsigned char>(input[end - 1]))) {
        --end;
    }

    return input.substr(start, end - start);
}

std::vector<int> ParseIntegerList(const std::string& text)
{
    std::vector<int> values;
    std::istringstream stream(text);
    std::string token;

    while (std::getline(stream, token, ',')) {
        token = Trim(token);
        if (token.empty()) {
            continue;
        }
        const std::size_t start = token.find_first_of("0123456789");
        if (start == std::string::npos) {
            continue;
        }
        const std::size_t end = token.find_last_of("0123456789");
        if (end < start) {
            continue;
        }
        values.push_back(std::stoi(token.substr(start, end - start + 1)));
    }

    return values;
}

}  // namespace

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

// std::vector<BridgeInfo> OpenVSwitchTopologyParser::parse(const std::string& raw_output) const
// {
//     std::vector<BridgeInfo> bridges;
//     BridgeInfo* current_bridge = nullptr;
//     PortInfo* current_port = nullptr;

//     std::istringstream stream(raw_output);
//     std::string line;
//     while (std::getline(stream, line)) {
//         const std::string trimmed = Trim(line);
//         if (trimmed.empty()) {
//             continue;
//         }

//         if (trimmed.rfind("Bridge ", 0) == 0) {
//             bridges.push_back(BridgeInfo{trimmed.substr(7), {}});
//             current_bridge = &bridges.back();
//             current_port = nullptr;
//             continue;
//         }

//         if (trimmed.rfind("Port ", 0) == 0) {
//             if (current_bridge != nullptr) {
//                 current_bridge->ports.push_back(PortInfo{trimmed.substr(5), "", {}, {}, false});
//                 current_port = &current_bridge->ports.back();
//             }
//             continue;
//         }

//         if (trimmed.rfind("Interface ", 0) == 0) {
//             if (current_port != nullptr) {
//                 current_port->interface_name = trimmed.substr(10);
//             }
//             continue;
//         }

//         if (trimmed.rfind("type: ", 0) == 0) {
//             if (current_port != nullptr && Trim(trimmed.substr(6)) == "internal") {
//                 current_port->is_internal = true;
//             }
//             continue;
//         }

//         if (trimmed.rfind("tag: ", 0) == 0) {
//             if (current_port != nullptr) {
//                 const std::string value = Trim(trimmed.substr(5));
//                 if (!value.empty()) {
//                     current_port->access_vlans.push_back(std::stoi(value));
//                 }
//             }
//             continue;
//         }

//         if (trimmed.rfind("trunks: [", 0) == 0) {
//             if (current_port != nullptr) {
//                 const std::size_t end = trimmed.find(']');
//                 if (end != std::string::npos) {
//                     const std::string list = trimmed.substr(9, end - 9);
//                     current_port->trunk_vlans = ParseIntegerList(list);
//                 }
//             }
//             continue;
//         }
//     }

//     return bridges;
// }

std::vector<BridgeInfo> CiscoTopologyParser::parse(const std::string& raw_output) const
{
    std::vector<BridgeInfo> bridges;
    // 브리지 이름은 벤더 접두가 아니라 장치 제품명을 씁니다.
    // (8000v 는 라우터이고, 이 파서는 `show running-config` 의 switchport 라인을
    //  그대로 옮기는 역할이다. 이름을 catalyst8000v 로 두면 라우터인데
    //  스위치로 보여 혼동을 만든다.)
    BridgeInfo bridge{"cisco-ios-xe", {}};

    std::istringstream stream(raw_output);
    std::string line;
    PortInfo* current_port = nullptr;

    while (std::getline(stream, line)) {
        const std::string trimmed = Trim(line);
        if (trimmed.empty()) {
            continue;
        }

        if (trimmed.rfind("interface ", 0) == 0) {
            bridge.ports.push_back(PortInfo{trimmed.substr(10), "", {}, {}, false});
            current_port = &bridge.ports.back();
            continue;
        }

        if (current_port == nullptr) {
            continue;
        }

        if (trimmed.rfind("switchport access vlan ", 0) == 0) {
            const std::string value = Trim(trimmed.substr(std::string("switchport access vlan ").size()));
            if (!value.empty()) {
                current_port->access_vlans.push_back(std::stoi(value));
            }
            continue;
        }

        if (trimmed.rfind("switchport trunk allowed vlan ", 0) == 0) {
            const std::string value = Trim(trimmed.substr(std::string("switchport trunk allowed vlan ").size()));
            current_port->trunk_vlans = ParseIntegerList(value);
            continue;
        }
    }

    if (!bridge.ports.empty()) {
        bridges.push_back(bridge);
    }
    return bridges;
}


std::vector<BridgeInfo> AristaTopologyParser::parse(const std::string& raw_output) const
{
    // TODO: Arista EOS 'show vlan'/'show interfaces switchport' 출력 파싱을 구현합니다.
    (void)raw_output;
    return {};
}


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