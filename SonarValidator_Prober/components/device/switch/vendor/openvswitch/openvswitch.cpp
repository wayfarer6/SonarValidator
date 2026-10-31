#include "components/device/switch/vendor/openvswitch/openvswitch.hpp"

#include "components/parser/cli_output_parser.hpp"

#include <nlohmann/json.hpp>

std::vector<BridgeInfo> OpenVSwitchTopologyParser::parse(const std::string& raw_output) const
{
    std::vector<BridgeInfo> bridges;

    const nlohmann::json parsed = cli_parser::ParseOvsTopology(raw_output);
    if (!parsed.is_object() || !parsed.contains("bridges") ||
        !parsed["bridges"].is_array())
    {
        return bridges;
    }

    for (const auto& bridge_json : parsed["bridges"])
    {
        BridgeInfo bridge;
        bridge.name = bridge_json.value("name", std::string{});

        if (bridge_json.contains("ports") && bridge_json["ports"].is_array())
        {
            for (const auto& port_json : bridge_json["ports"])
            {
                Port port;
                port.name = port_json.value("name", std::string{});
                port.interface_name = port.name;

                if (port_json.contains("interfaces") &&
                    port_json["interfaces"].is_array() &&
                    !port_json["interfaces"].empty())
                {
                    const auto& iface = port_json["interfaces"].front();
                    port.interface_name = iface.value("name", port.name);
                    port.is_internal = (iface.value("type", std::string{}) == "internal");
                }

                if (port_json.contains("tag") && port_json["tag"].is_number())
                {
                    port.access_vlans.push_back(port_json["tag"].get<int>());
                }
                if (port_json.contains("trunks") && port_json["trunks"].is_array())
                {
                    for (const auto& vlan : port_json["trunks"])
                    {
                        if (vlan.is_number())
                        {
                            port.trunk_vlans.push_back(vlan.get<int>());
                        }
                    }
                }

                bridge.ports.push_back(std::move(port));
            }
        }

        bridges.push_back(std::move(bridge));
    }

    return bridges;
}
