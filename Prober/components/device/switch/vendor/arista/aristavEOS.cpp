#include "components/device/switch/vendor/arista/aristavEOS.hpp"

#include "components/parser/cli_output_parser.hpp"

#include <nlohmann/json.hpp>

// Arista EOS 의 `show running-config`(interface/switchport 줄) 파싱은
// components/parser 의 ANTLR 문법(SwitchTopology.g4)이 담당합니다.
// 이 클래스는 그 JSON 결과를 스위치 모델로 옮기는 얇은 변환 계층입니다.
std::vector<BridgeInfo> AristaTopologyParser::parse(const std::string& raw_output) const
{
    std::vector<BridgeInfo> bridges;

    const nlohmann::json parsed = cli_parser::ParseRunningConfig(raw_output);
    if (!parsed.is_object() || !parsed.contains("ports") ||
        !parsed["ports"].is_array() || parsed["ports"].empty())
    {
        return bridges;
    }

    BridgeInfo bridge;
    bridge.name = "arista-eos";

    for (const auto& port_json : parsed["ports"])
    {
        PortInfo port;
        port.name = port_json.value("name", std::string{});
        port.interface_name = port.name;

        if (port_json.contains("access_vlan") && port_json["access_vlan"].is_number())
        {
            port.access_vlans.push_back(port_json["access_vlan"].get<int>());
        }
        if (port_json.contains("native_vlan") && port_json["native_vlan"].is_number())
        {
            port.access_vlans.push_back(port_json["native_vlan"].get<int>());
        }
        if (port_json.contains("trunk_vlans") && port_json["trunk_vlans"].is_array())
        {
            for (const auto& vlan : port_json["trunk_vlans"])
            {
                if (vlan.is_number())
                {
                    port.trunk_vlans.push_back(vlan.get<int>());
                }
            }
        }

        bridge.ports.push_back(std::move(port));
    }

    bridges.push_back(std::move(bridge));
    return bridges;
}