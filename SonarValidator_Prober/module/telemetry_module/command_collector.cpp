#include "module/telemetry_module/command_collector.hpp"

#include <algorithm>
#include <cctype>
#include <iostream>
#include <map>
#include <sstream>
#include <vector>

#include "module/configuration_module/prober_config.hpp"
#include <stdexcept>
#include "components/parser/cli_output_parser.hpp"

using Json = nlohmann::json;

namespace collector
{
namespace
{

enum class ProductKind
{
    kLinux,        // Ubuntu 등 일반 리눅스 (ip ... 명령)
    kFrr,          // FRR 라우터 (Alpine/Debian 호스트의 ip ... 명령 + vtysh 보조)
    kFirewall,     // nftables 방화벽 (Alpine 호스트의 ip ... 명령 + nft 규칙)
    kCisco,        // Cisco IOS-XE (guestshell 의 dohost)
    kArista,       // Arista vEOS (FastCli)
    kOpenVSwitch,  // Open vSwitch 스위치 (ovs-vsctl show / list port)
    kOther         // 알 수 없는 제품 — 수집 명령을 실행하지 않는다
};

std::string ToLower(std::string text)
{
    std::transform(text.begin(), text.end(), text.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return text;
}

std::vector<std::string> SplitTokens(const std::string& line)
{
    std::vector<std::string> tokens;
    std::istringstream stream(line);
    std::string token;
    while (stream >> token)
    {
        while (!token.empty() && (token.back() == ',' || token.back() == '\r'))
        {
            token.pop_back();
        }
        if (!token.empty())
        {
            tokens.push_back(token);
        }
    }
    return tokens;
}

bool SplitCidr(const std::string& token, std::string& address, int& prefix_len)
{
    const std::size_t slash = token.find('/');
    if (slash == std::string::npos || slash == 0 || slash + 1 >= token.size())
    {
        return false;
    }

    const std::string prefix_text = token.substr(slash + 1);
    if (!std::all_of(prefix_text.begin(), prefix_text.end(), [](unsigned char ch) {
            return std::isdigit(ch) != 0;
        }))
    {
        return false;
    }

    address = token.substr(0, slash);
    prefix_len = std::stoi(prefix_text);
    return true;
}

bool LooksLikeIp(const std::string& token)
{
    if (token.find(':') != std::string::npos)
    {
        return true;
    }
    std::size_t dots = 0;
    for (const char ch : token)
    {
        if (ch == '.')
        {
            ++dots;
        }
        else if (std::isdigit(static_cast<unsigned char>(ch)) == 0)
        {
            return false;
        }
    }
    return dots == 3;
}

bool LooksLikeMac(const std::string& token)
{
    std::size_t colons = 0;
    for (const char ch : token)
    {
        if (ch == ':')
        {
            ++colons;
        }
    }
    return colons == 5 && token.size() == 17;
}

bool LooksLikeIfaceName(const std::string& token)
{
    if (token.empty() || token.find('/') != std::string::npos || token.find(':') != std::string::npos)
    {
        return false;
    }
    static const char* kAttributes[] = {
        "global", "host", "link", "forever", "noprefixroute", "dynamic",
        "secondary", "temporary", "mngtmpaddr", "nodad", "metric", "brd",
        "proto", "deprecated", "preferred_lft", "valid_lft", "permanent"};
    for (const char* attribute : kAttributes)
    {
        if (token == attribute)
        {
            return false;
        }
    }
    return std::isalpha(static_cast<unsigned char>(token[0])) != 0;
}

std::string FindLineForName(const std::vector<std::string>& lines,
                            const std::string& name,
                            std::size_t& cursor)
{
    for (std::size_t i = cursor; i < lines.size(); ++i)
    {
        const std::vector<std::string> tokens = SplitTokens(lines[i]);
        if (tokens.empty())
        {
            continue;
        }

        std::string first = tokens[0];
        if (!first.empty() && first.back() == ':')
        {
            first.pop_back();
        }
        if (first == name)
        {
            cursor = i + 1;
            return lines[i];
        }
    }
    return {};
}

struct LineFacts
{
    Json addresses = Json::array();
    std::string mac;
    std::string mtu;
    std::string state;
};

LineFacts ExtractLineFacts(const std::string& line)
{
    LineFacts facts;
    const std::vector<std::string> tokens = SplitTokens(line);

    bool address_seen = false;
    for (std::size_t i = 0; i < tokens.size(); ++i)
    {
        std::string token = tokens[i];
        if (token.size() >= 2 && token.front() == '<' && token.back() == '>')
        {
            continue;  // 플래그 묶음
        }

        std::string bare;
        int prefix_len = -1;
        if (SplitCidr(token, bare, prefix_len))
        {
            Json address = Json::object();
            address["family"] = (bare.find(':') == std::string::npos) ? "inet" : "inet6";
            address["address"] = bare;
            address["prefix_len"] = prefix_len;
            address_seen = true;
            facts.addresses.push_back(std::move(address));
            continue;
        }

        if (LooksLikeMac(token))
        {
            facts.mac = token;
            continue;
        }

        const std::string key = ToLower(token);
        if (key == "mtu" && i + 1 < tokens.size())
        {
            facts.mtu = tokens[++i];
            continue;
        }
        if (key == "up" || key == "down" || key == "administratively" ||
            key == "notconnect" || key == "connected")
        {
            if (facts.state.empty())
            {
                facts.state = key;
            }
            continue;
        }
    }
    (void)address_seen;
    return facts;
}

Json ConvertBriefEntriesToNic(const Json& brief_result)
{
    Json nic = Json::object();
    Json interfaces = Json::array();

    const auto brief = brief_result.is_object() ? brief_result.find("brief") : brief_result.end();
    if (brief_result.is_object() && brief != brief_result.end() && brief->is_array())
    {
        for (const Json& entry : *brief)
        {
            if (!entry.is_object())
            {
                continue;
            }

            Json iface = Json::object();
            iface["name"] = entry.value("name", std::string{});
            iface["state"] = entry.value("state", std::string{});
            if (entry.contains("mac") && entry["mac"].is_string())
            {
                iface["mac"] = entry["mac"];
            }

            Json addresses = Json::array();
            if (entry.contains("addresses") && entry["addresses"].is_array())
            {
                for (const Json& value : entry["addresses"])
                {
                    if (!value.is_string())
                    {
                        continue;
                    }
                    std::string bare;
                    int prefix_len = -1;
                    if (!SplitCidr(value.get<std::string>(), bare, prefix_len))
                    {
                        continue;  // `UP` 같은 상태 토큰이 섞여 들어온 경우
                    }
                    Json address = Json::object();
                    address["family"] = (bare.find(':') == std::string::npos) ? "inet" : "inet6";
                    address["address"] = bare;
                    address["prefix_len"] = prefix_len;
                    address["interface"] = iface["name"];
                    addresses.push_back(std::move(address));
                }
            }
            iface["addresses"] = addresses;
            interfaces.push_back(std::move(iface));
        }
    }

    nic["interfaces"] = interfaces;
    return nic;
}

Json BuildNicFromInterfaceBrief(const Json& brief_result, const std::string& raw)
{
    Json nic = Json::object();
    Json interfaces = Json::array();
    if (!brief_result.is_object() || !brief_result.contains("interfaces") ||
        !brief_result["interfaces"].is_array())
    {
        nic["interfaces"] = interfaces;
        return nic;
    }

    std::vector<std::string> lines;
    {
        std::istringstream stream(raw);
        std::string line;
        while (std::getline(stream, line))
        {
            lines.push_back(line);
        }
    }

    std::size_t cursor = 0;
    for (const Json& entry : brief_result["interfaces"])
    {
        if (!entry.is_object())
        {
            continue;
        }

        const std::string name = entry.value("name", std::string{});
        if (name.empty())
        {
            continue;
        }

        const LineFacts facts = ExtractLineFacts(FindLineForName(lines, name, cursor));

        Json iface = Json::object();
        iface["name"] = name;

        std::string state = entry.value("status", std::string{});
        if (state.empty())
        {
            state = facts.state;
        }
        if (!state.empty())
        {
            iface["state"] = state;
        }
        if (!facts.mac.empty())
        {
            iface["mac"] = facts.mac;
        }
        if (!facts.mtu.empty())
        {
            iface["mtu"] = facts.mtu;
        }

        Json addresses = facts.addresses;
        if (addresses.empty() && entry.contains("ip_address") && entry["ip_address"].is_string() &&
            !entry["ip_address"].get<std::string>().empty())
        {
            Json address = Json::object();
            const std::string ip = entry["ip_address"].get<std::string>();
            address["family"] = (ip.find(':') == std::string::npos) ? "inet" : "inet6";
            address["address"] = ip;
            address["interface"] = name;
            if (entry.contains("prefix_len") && entry["prefix_len"].is_number())
            {
                address["prefix_len"] = entry["prefix_len"];
            }
            addresses.push_back(std::move(address));
        }
        for (Json& address : addresses)
        {
            if (!address.contains("interface"))
            {
                address["interface"] = name;
            }
        }
        iface["addresses"] = addresses;

        interfaces.push_back(std::move(iface));
    }

    nic["interfaces"] = interfaces;
    return nic;
}

std::string CleanAristaOutput(const std::string& raw)
{
    std::istringstream stream(raw);
    std::string line;
    std::string cleaned;

    while (std::getline(stream, line))
    {
        while (!line.empty() && (line.back() == '\r' || line.back() == ' '))
        {
            line.pop_back();
        }

        if (line.empty())
        {
            cleaned += '\n';
            continue;
        }

        const std::size_t first = line.find_first_not_of(" \t");
        const std::string trimmed = (first == std::string::npos) ? line : line.substr(first);

        if (trimmed.size() >= 2 && trimmed[0] == '>' && trimmed[1] == ' ')
        {
            continue;
        }
        if (trimmed == ">" || trimmed.rfind("ARISTA#", 0) == 0 ||
            trimmed.rfind("ARISTA>", 0) == 0)
        {
            continue;
        }
        if (trimmed.rfind("%", 0) == 0)
        {
            continue;
        }
        if (trimmed == "Arista Networks EOS shell" ||
            trimmed.rfind("Pagination disabled", 0) == 0 ||
            trimmed.rfind("Last login:", 0) == 0)
        {
            continue;
        }

        cleaned += line;
        cleaned += '\n';
    }
    return cleaned;
}

bool HasItems(const Json& object, const char* key)
{
    if (!object.is_object())
    {
        return false;
    }
    const auto iterator = object.find(key);
    return iterator != object.end() && iterator->is_array() && !iterator->empty();
}

const Json* FindArrayMember(const Json& object, const char* key)
{
    if (!object.is_object())
    {
        return nullptr;
    }
    const auto iterator = object.find(key);
    if (iterator == object.end() || !iterator->is_array())
    {
        return nullptr;
    }
    return &(*iterator);
}

int OvsPortAccessVlan(const Json& port)
{
    const auto iterator = port.find("tag");
    if (iterator == port.end())
    {
        return -1;
    }
    if (iterator->is_number_integer())
    {
        return iterator->get<int>();
    }
    if (iterator->is_array() && !iterator->empty() && iterator->front().is_number_integer())
    {
        return iterator->front().get<int>();
    }
    return -1;
}

std::vector<int> OvsPortTrunks(const Json& port)
{
    std::vector<int> vlans;
    const auto iterator = port.find("trunks");
    if (iterator == port.end() || !iterator->is_array())
    {
        return vlans;
    }
    for (const Json& item : *iterator)
    {
        if (item.is_number_integer())
        {
            vlans.push_back(item.get<int>());
        }
    }
    return vlans;
}

Json OvsTopologyToVlanStatus(const Json& topology)
{
    Json vlans = Json::array();
    const Json* bridges = FindArrayMember(topology, "bridges");
    if (bridges == nullptr)
    {
        return Json::object();
    }

    std::map<int, std::vector<std::string>> members;
    for (const Json& bridge : *bridges)
    {
        const Json* ports = FindArrayMember(bridge, "ports");
        if (ports == nullptr)
        {
            continue;
        }
        for (const Json& port : *ports)
        {
            const int access_vlan = OvsPortAccessVlan(port);
            if (access_vlan <= 0)
            {
                continue;
            }
            const auto name = port.find("name");
            members[access_vlan].push_back(
                name != port.end() && name->is_string() ? name->get<std::string>() : std::string{});
        }
    }

    for (const auto& [vlan_id, ports] : members)
    {
        Json vlan = Json::object();
        vlan["vlan_id"] = vlan_id;
        vlan["name"] = "VLAN" + std::to_string(vlan_id);
        vlan["status"] = "active";
        vlan["ports"] = ports;
        vlans.push_back(std::move(vlan));
    }

    if (vlans.empty())
    {
        return Json::object();
    }

    Json body = Json::object();
    body["vlans"] = std::move(vlans);
    body["vlan_count"] = body["vlans"].size();
    return body;
}

Json OvsTopologyToTrunkStatus(const Json& topology)
{
    Json ports_out = Json::array();
    const Json* bridges = FindArrayMember(topology, "bridges");
    if (bridges == nullptr)
    {
        return Json::object();
    }

    for (const Json& bridge : *bridges)
    {
        const Json* ports = FindArrayMember(bridge, "ports");
        if (ports == nullptr)
        {
            continue;
        }
        for (const Json& port : *ports)
        {
            const int access_vlan = OvsPortAccessVlan(port);
            const std::vector<int> trunks = OvsPortTrunks(port);

            if (access_vlan <= 0 && trunks.empty())
            {
                continue;
            }

            Json entry = Json::object();
            const auto name = port.find("name");
            entry["name"] =
                name != port.end() && name->is_string() ? name->get<std::string>() : std::string{};
            entry["mode"] = trunks.empty() ? "access" : "trunk";
            if (access_vlan > 0)
            {
                entry["access_vlan"] = access_vlan;
            }
            entry["trunk_vlans"] = trunks;
            entry["admin_enabled"] = true;
            ports_out.push_back(std::move(entry));
        }
    }

    if (ports_out.empty())
    {
        return Json::object();
    }

    Json body = Json::object();
    body["ports"] = std::move(ports_out);
    body["port_count"] = body["ports"].size();
    return body;
}

Json OvsPortListToVlanStatus(const Json& ports_result)
{
    const Json* ports = FindArrayMember(ports_result, "ports");
    if (ports == nullptr)
    {
        return Json::object();
    }

    std::map<int, std::vector<std::string>> members;
    for (const Json& port : *ports)
    {
        const int access_vlan = OvsPortAccessVlan(port);
        if (access_vlan <= 0)
        {
            continue;
        }
        const auto name = port.find("name");
        members[access_vlan].push_back(
            name != port.end() && name->is_string() ? name->get<std::string>() : std::string{});
    }

    Json vlans = Json::array();
    for (const auto& [vlan_id, names] : members)
    {
        Json vlan = Json::object();
        vlan["vlan_id"] = vlan_id;
        vlan["name"] = "VLAN" + std::to_string(vlan_id);
        vlan["status"] = "active";
        vlan["ports"] = names;
        vlans.push_back(std::move(vlan));
    }

    if (vlans.empty())
    {
        return Json::object();
    }

    Json body = Json::object();
    body["vlans"] = std::move(vlans);
    body["vlan_count"] = body["vlans"].size();
    return body;
}

Json OvsPortListToTrunkStatus(const Json& ports_result)
{
    const Json* ports = FindArrayMember(ports_result, "ports");
    if (ports == nullptr)
    {
        return Json::object();
    }

    Json ports_out = Json::array();
    for (const Json& port : *ports)
    {
        const int access_vlan = OvsPortAccessVlan(port);
        const std::vector<int> trunks = OvsPortTrunks(port);
        if (access_vlan <= 0 && trunks.empty())
        {
            continue;
        }

        Json entry = Json::object();
        const auto name = port.find("name");
        entry["name"] =
            name != port.end() && name->is_string() ? name->get<std::string>() : std::string{};
        entry["mode"] = trunks.empty() ? "access" : "trunk";
        if (access_vlan > 0)
        {
            entry["access_vlan"] = access_vlan;
        }
        entry["trunk_vlans"] = trunks;
        entry["admin_enabled"] = true;
        ports_out.push_back(std::move(entry));
    }

    if (ports_out.empty())
    {
        return Json::object();
    }

    Json body = Json::object();
    body["ports"] = std::move(ports_out);
    body["port_count"] = body["ports"].size();
    return body;
}

} // namespace

static CollectedState BuildBuiltinState(DeviceType device_type,
                                     const std::string& product_name,
                                     const std::map<std::string, std::string>& outputs, ProductKind product)
{
    CollectedState state;
    const cli_parser::Vendor vendor = cli_parser::VendorFromProductName(product_name);

    auto output = [&outputs](const std::string& command) -> std::string {
        const auto iterator = outputs.find(command);
        return iterator == outputs.end() ? std::string{} : iterator->second;
    };

    auto empty = [](const std::string& value) { return value.empty(); };

    Json snapshot = Json::object();

    const bool linux_style = product == ProductKind::kLinux ||
                             product == ProductKind::kFrr ||
                             product == ProductKind::kFirewall ||
                             product == ProductKind::kOpenVSwitch ||
                             device_type == DeviceType::kVirtualMachine;

    if (linux_style)
    {
        const std::string raw = output("ip a");
        const std::string brief_raw = output("ip -br addr show");

        if (!empty(raw))
        {
            state.nic = cli_parser::ParseNicStatus(raw);
            if (HasItems(state.nic, "interfaces"))
            {
                snapshot["nic_status"] = state.nic;
            }
        }

        if (!HasItems(state.nic, "interfaces") && !empty(brief_raw))
        {
            const Json brief_result = cli_parser::ParseNicBrief(brief_raw);
            state.nic = ConvertBriefEntriesToNic(brief_result);
        }

        if (HasItems(state.nic, "interfaces"))
        {
            snapshot["nic_status"] = state.nic;
            state.any_success = true;
        }
        else
        {
            state.nic = Json{};
        }
    }
    else
    {
        const std::string brief_raw = output("show ip interface brief");
        if (!empty(brief_raw))
        {
            const Json brief_result = cli_parser::ParseInterfaceStatus(brief_raw, vendor);
            if (HasItems(brief_result, "interfaces"))
            {
                snapshot["interface_status"] = brief_result;
                state.nic = BuildNicFromInterfaceBrief(brief_result, brief_raw);
                if (HasItems(state.nic, "interfaces"))
                {
                    snapshot["nic_status"] = state.nic;
                    state.any_success = true;
                }
                else
                {
                    state.nic = Json{};
                }
            }
        }
    }

    {
        std::string raw = output("show ip route");
        if (empty(raw))
        {
            raw = output("ip route show");
        }
        if (!empty(raw))
        {
            state.route = cli_parser::ParseRouteStatus(raw, vendor);
            if (HasItems(state.route, "routes"))
            {
                snapshot["route_status"] = state.route;
                state.any_success = true;
            }
            else
            {
                state.route = Json{};
            }
        }
    }

    if (product == ProductKind::kOpenVSwitch)
    {
        const std::string raw = output("ovs-vsctl show");
        if (!empty(raw))
        {
            const Json topology = cli_parser::ParseOvsTopology(raw);
            state.topology = topology;
            if (HasItems(topology, "bridges"))
            {
                snapshot["ovs_topology"] = topology;
                state.any_success = true;

                state.vlan = OvsTopologyToVlanStatus(topology);
                if (HasItems(state.vlan, "vlans"))
                {
                    snapshot["vlan_status"] = state.vlan;
                }
                else
                {
                    state.vlan = Json{};
                }

                state.trunk = OvsTopologyToTrunkStatus(topology);
                if (HasItems(state.trunk, "ports"))
                {
                    snapshot["trunk_status"] = state.trunk;
                }
                else
                {
                    state.trunk = Json{};
                }
            }
        }

        if (!HasItems(state.trunk, "ports"))
        {
            const std::string raw_list = output("ovs-vsctl list port");
            if (!empty(raw_list))
            {
                const Json ports_result = cli_parser::ParseOvsTopology(raw_list);
                if (HasItems(ports_result, "ports"))
                {
                    snapshot["ovs_ports"] = ports_result;
                    state.any_success = true;

                    state.vlan = OvsPortListToVlanStatus(ports_result);
                    if (HasItems(state.vlan, "vlans"))
                    {
                        snapshot["vlan_status"] = state.vlan;
                    }
                    else
                    {
                        state.vlan = Json{};
                    }

                    state.trunk = OvsPortListToTrunkStatus(ports_result);
                    if (HasItems(state.trunk, "ports"))
                    {
                        snapshot["trunk_status"] = state.trunk;
                    }
                    else
                    {
                        state.trunk = Json{};
                    }
                }
            }
        }
    }
    else
    {
        const std::string raw = output("show vlan brief");
        if (!empty(raw))
        {
            state.vlan = cli_parser::ParseSwitchVlan(raw);
            if (HasItems(state.vlan, "vlans"))
            {
                snapshot["vlan_status"] = state.vlan;
                state.any_success = true;
            }
            else
            {
                state.vlan = Json{};
            }
        }
    }

    if (product != ProductKind::kOpenVSwitch)
    {
        const std::string raw = output("show interfaces switchport");
        if (!empty(raw))
        {
            state.trunk = cli_parser::ParseSwitchPorts(raw);
            if (HasItems(state.trunk, "ports"))
            {
                snapshot["trunk_status"] = state.trunk;
                state.any_success = true;
            }
            else
            {
                state.trunk = Json{};
            }
        }
    }

    if (product == ProductKind::kFirewall)
    {
        const Json native = Json::parse(output("nft -j list ruleset"), nullptr, false);
        if (native.is_object() && native.contains("nftables") && native["nftables"].is_array())
        {
            state.rules = native;
            state.rules["parsed"] = true;
            state.rules["format"] = "nftables-json";
            snapshot["firewall_rules"] = state.rules;
            state.any_success = true;
        }
        else
        {
            const std::string raw = output("nft list ruleset");
            if (!empty(raw))
            {
                state.rules = cli_parser::ParseFirewallRules(raw);
                if (HasItems(state.rules, "tables"))
                {
                    snapshot["firewall_rules"] = state.rules;
                    state.any_success = true;
                }
                else
                {
                    state.rules = Json{};
                }
            }
        }
    }

    {
        std::string raw = output("show arp");
        if (empty(raw))
        {
            raw = output("show ip arp");
        }
        if (empty(raw))
        {
            raw = output("ip neigh show");
        }
        if (!empty(raw))
        {
            state.arp = cli_parser::ParseArpTable(raw, vendor);
            if (HasItems(state.arp, "entries"))
            {
                snapshot["arp_table"] = state.arp;
                state.any_success = true;
            }
            else
            {
                state.arp = Json{};
            }
        }
    }

    snapshot["vendor"] = cli_parser::VendorName(vendor);
    snapshot["product"] = product_name;

    state.snapshot = std::move(snapshot);
    return state;
}

void TelemetryStrategyRegistry::Register(std::shared_ptr<const TelemetryStrategy> strategy) {
    if (!strategy || strategy->Id().empty()) throw std::invalid_argument("telemetry strategy requires an id");
    for (const auto& current : strategies_)
        if (current->Id() == strategy->Id()) throw std::invalid_argument("duplicate telemetry strategy id");
    strategies_.push_back(std::move(strategy));
}

const TelemetryStrategy* TelemetryStrategyRegistry::Select(DeviceType type, const std::string& product) const {
    const TelemetryStrategy* selected = nullptr;
    bool ambiguous = false;
    for (const auto& strategy : strategies_) {
        if (!strategy->Supports(type, product)) continue;
        if (!selected || strategy->Priority() > selected->Priority()) {
            selected = strategy.get();
            ambiguous = false;
        } else if (strategy->Priority() == selected->Priority()) ambiguous = true;
    }
    return ambiguous ? nullptr : selected;
}

const TelemetryStrategyRegistry& DefaultTelemetryStrategies() {
    static const auto registry = [] {
        TelemetryStrategyRegistry result;
        const std::vector<DeviceQuery> linux = {
            {QueryChannel::Shell, "ip a"}, {QueryChannel::Shell, "ip -br addr show"},
            {QueryChannel::Shell, "ip route show"}, {QueryChannel::Shell, "ip neigh show"}};
        auto add = [&](std::string id, ProductKind kind, std::vector<std::string> markers,
                       std::vector<DeviceQuery> queries, int priority = 100) {
            result.Register(std::make_shared<FunctionalTelemetryStrategy>(std::move(id),
                [markers](DeviceType, const std::string& product) {
                    return std::any_of(markers.begin(), markers.end(), [&](const auto& marker) { return product.find(marker) != std::string::npos; });
                }, std::move(queries),
                [kind](DeviceType type, const std::string& product, const Outputs& outputs) {
                    return BuildBuiltinState(type, product, outputs, kind);
                }, priority));
        };
        add("linux", ProductKind::kLinux, {"Ubuntu", "Linux"}, linux, 0);
        add("frr", ProductKind::kFrr, {"FRR"}, linux);
        auto firewall = linux;
        firewall.push_back({QueryChannel::Shell, "nft list ruleset"});
        firewall.push_back({QueryChannel::Shell, "nft -j list ruleset"});
        add("nftables", ProductKind::kFirewall, {"nftables", "nft", "Firewall"}, firewall);
        add("cisco", ProductKind::kCisco, {"Cisco"}, {{QueryChannel::Ios, "show ip interface brief"},
            {QueryChannel::Ios, "show ip route"}, {QueryChannel::Ios, "show ip arp"}});
        add("arista", ProductKind::kArista, {"Arista"}, {{QueryChannel::Arista, "show vlan brief"},
            {QueryChannel::Arista, "show ip interface brief"}, {QueryChannel::Arista, "show ip route"},
            {QueryChannel::Arista, "show interfaces switchport"}, {QueryChannel::Arista, "show arp"}});
        add("ovs", ProductKind::kOpenVSwitch, {"OpenVSwitch", "Open vSwitch"}, {{QueryChannel::Shell, "ovs-vsctl show"},
            {QueryChannel::Shell, "ovs-vsctl list port"}, {QueryChannel::Shell, "ip a"}, {QueryChannel::Shell, "ip -br addr show"}});
        return result;
    }();
    return registry;
}

CollectedState BuildStateFromOutputs(DeviceType type, const std::string& product, const Outputs& outputs,
                                    const TelemetryStrategyRegistry& strategies) {
    const auto* strategy = strategies.Select(type, product);
    if (!strategy) return {};
    return strategy->Parse(type, product, outputs);
}

CollectedState CollectState(const ProberConfig& config, DeviceCommandExecutor& device,
                            const TelemetryStrategyRegistry& strategies) {
    const auto product = config.GetProductName();
    const auto* strategy = strategies.Select(config.GetDeviceType(), product);
    if (!strategy) {
        std::cerr << "[COLLECT] no unambiguous strategy for " << product << '\n';
        return {};
    }
    Outputs outputs;
    for (const auto& query : strategy->Queries()) {
        try {
            switch (query.channel) {
                case QueryChannel::Shell: outputs[query.command] = device.RunCommandOutput(query.command); break;
                case QueryChannel::Ios: outputs[query.command] = device.ExecuteIosCli({query.command}); break;
                case QueryChannel::Arista: outputs[query.command] = CleanAristaOutput(device.QueryAristaCli(query.command)); break;
            }
        } catch (const std::exception& error) {
            std::cerr << "[COLLECT] " << query.command << ": " << error.what() << '\n';
        }
    }
    return strategy->Parse(config.GetDeviceType(), product, outputs);
}
} // namespace collector
