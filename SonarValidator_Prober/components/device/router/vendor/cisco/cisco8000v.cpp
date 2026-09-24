#include "components/device/router/vendor/cisco/cisco8000v.hpp"

#include "components/parser/cli_output_parser.hpp"

#include <nlohmann/json.hpp>

// Cisco IOS-XE 의 `show running-config`(interface/switchport 줄) 파싱은
// components/parser 의 ANTLR 문법(SwitchTopology.g4)이 담당합니다.
// 이 클래스는 그 JSON 결과를 스위치 모델로 옮기는 얇은 변환 계층입니다.
std::vector<BridgeInfo> CiscoTopologyParser::parse(const std::string& raw_output) const
{
    std::vector<BridgeInfo> bridges;

    const nlohmann::json parsed = cli_parser::ParseRunningConfig(raw_output);
    if (!parsed.is_object() || !parsed.contains("ports") ||
        !parsed["ports"].is_array() || parsed["ports"].empty())
    {
        return bridges;
    }

    // 브리지 이름은 벤더 접두가 아니라 장치 제품명을 씁니다.
    // (8000v 는 라우터이고, 이 파서는 `show running-config` 의 switchport 라인을
    //  그대로 옮기는 역할이다. 이름을 catalyst8000v 로 두면 라우터인데
    //  스위치로 보여 혼동을 만든다.)
    BridgeInfo bridge;
    bridge.name = "cisco-ios-xe";

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