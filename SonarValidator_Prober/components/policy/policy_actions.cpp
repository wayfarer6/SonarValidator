#include "components/policy/ios_acl_target.hpp"
#include "utils/log.hpp"
#include <algorithm>
#include "components/policy/policy_actions.hpp"
#include "components/policy/policy_executor.hpp"
#include "components/policy/policy_json.hpp"
#include "components/policy/acl_commands.hpp"
#include "components/terminal/ios_snapshot.hpp"
#include "components/terminal/arista_cli.hpp"
#include "utils/runtime_environment.hpp"
#include <chrono>
#include <cctype>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <sys/stat.h>

using Json = nlohmann::json;
using runtime_environment::ShellQuote;
namespace
{

constexpr const char* kAclCookie = "0x534f4e41"; // "SONA"

constexpr int kAclPriority = 20000;

using AclEntry = acl::Entry;

constexpr auto ReadAclEntries = &acl::ReadEntries;

constexpr auto ReadAclName = &acl::ReadName;

constexpr auto ReadApplyTarget = &acl::ReadApplyTarget;

using ios_acl_target::ParseIpv4;
using ios_acl_target::IpInCidr;
using ios_acl_target::ResolveIosBindInterface;

std::string VtyshCommand(const std::vector<std::string>& lines)
{
    std::string out = "vtysh";
    for (const std::string& line : lines)
    {
        out += " -c " + ShellQuote(line);
    }
    return out;
}

std::string NftScript(const std::string& script)
{
    return "nft -i " + ShellQuote(script);
}

} // namespace

bool PolicyActions::HasCommand(const std::string& program)
{
    if (program.empty())
    {
        return false;
    }
    return RunCommand("command -v " + program + " >/dev/null 2>&1");
}

std::string PolicyActions::PrimaryInterface()
{
    if (!primary_interface_.empty())
    {
        return primary_interface_;
    }

    const std::string raw = RunCommandOutput("ip -o -4 addr show 2>/dev/null");
    std::istringstream stream(raw);
    std::string line;
    while (std::getline(stream, line))
    {
        std::istringstream tokens(line);
        std::string index;
        std::string name;
        if (!(tokens >> index >> name))
        {
            continue;
        }
        if (!name.empty() && name.back() == ':')
        {
            name.pop_back();
        }
        const std::size_t at = name.find('@');
        if (at != std::string::npos)
        {
            name = name.substr(0, at);
        }
        if (name.empty() || name == "lo")
        {
            continue;
        }
        primary_interface_ = name;
        SV_LOG_DEBUG << "[POLICY] primary interface resolved: " << name << '\n';
        return primary_interface_;
    }

    SV_LOG_WARN << "[POLICY] could not resolve primary interface\n";
    return {};
}

std::string PolicyActions::ResolveInterfaceName(const std::string& name)
{
    if (name == "__primary__")
    {
        return PrimaryInterface();
    }
    return name;
}

bool PolicyActions::ApplyAddressesWithIp(const Json& policy,
                                            const Json::const_iterator& ethernets,
                                            const std::string& command)
{
    (void)policy;
    (void)command;

    bool all_ok = true;
    for (auto entry = ethernets->begin(); entry != ethernets->end(); ++entry)
    {
        const std::string name = ResolveInterfaceName(entry.key());
        const Json& settings = entry.value();
        if (!settings.is_object() || name.empty())
        {
            SV_LOG_WARN << "[POLICY] skipping unresolvable interface '" << entry.key() << "'\n";
            all_ok = false;
            continue;
        }

        for (const std::string& address : policy_json::AsStringList(settings, "addresses"))
        {
            const std::string script =
                "ip addr replace " + address + " dev " + name;
            if (!RunCommand(script))
            {
                SV_LOG_WARN << "[POLICY] failed to set address " << address
                          << " on " << name << '\n';
                all_ok = false;
            }
            else
            {
                SV_LOG_DEBUG << "[POLICY] address " << address << " on " << name << '\n';
            }
        }

        if (!RunCommand("ip link set " + name + " up"))
        {
            SV_LOG_WARN << "[POLICY] failed to bring up " << name << '\n';
            all_ok = false;
        }

        const auto routes = settings.find("routes");
        if (routes != settings.end() && routes->is_array())
        {
            for (const auto& route : *routes)
            {
                const std::string to = policy_json::AsString(route, "to");
                const std::string via = policy_json::AsString(route, "via");
                if (to.empty() || via.empty())
                {
                    continue;
                }
                const std::string script =
                    "ip route replace " + to + " via " + via + " dev " + name;
                if (!RunCommand(script))
                {
                    SV_LOG_WARN << "[POLICY] failed to add route " << to
                              << " via " << via << '\n';
                    all_ok = false;
                }
                else
                {
                    SV_LOG_DEBUG << "[POLICY] route " << to << " via " << via << '\n';
                }
            }
        }
    }

    return all_ok;
}
bool PolicyActions::ApplyOpenVSwitchPolicy(const Json& policy)
{
    const std::string command = policy_json::AsString(policy, "command");
    const std::string interface = policy_json::AsString(policy, "interface",
                                                        policy_json::AsString(policy, "port"));

    if (command == "on")
    {
        return RunCommand("ip link set " + interface + " up");
    }
    if (command == "off")
    {
        return RunCommand("ip link set " + interface + " down");
    }
    if (command == "create")
    {
        const std::string vlan_id = policy_json::AsString(policy, "vlan_id");
        if (!vlan_id.empty() && !interface.empty())
        {
            return RunCommand("ovs-vsctl set port " + interface + " tag=" + vlan_id);
        }

        const std::string acl_name = policy_json::AsString(policy, "acl_name");
        if (!acl_name.empty())
        {
            const std::string src = policy_json::AsString(policy, "source_subnet");
            const std::string dst = policy_json::AsString(policy, "destination_subnet");
            const std::string action = policy_json::AsString(policy, "action");

            const std::string bridge = ReadApplyTarget(policy);
            if (src.empty() || dst.empty() || bridge.empty())
            {
                SV_LOG_WARN << "[POLICY] OVS ACL needs source/destination/bridge\n";
                return false;
            }

            const bool deny = (action == "deny" || action == "drop");

            const int priority = deny ? kAclPriority : kAclPriority - 100;

            return RunCommand("ovs-ofctl -O OpenFlow13 add-flow " + bridge + " " +
                              "cookie=" + kAclCookie + ",priority=" + std::to_string(priority) +
                              ",ip,nw_src=" + src + ",nw_dst=" + dst +
                              ",actions=" + (deny ? "drop" : "normal"));
        }

        const std::string destination = policy_json::AsString(policy, "destination_prefix");
        const std::string next_hop = policy_json::AsString(policy, "next_hop");
        if (!destination.empty() && !next_hop.empty())
        {
            return RunCommand("ip route add " + destination + " via " + next_hop);
        }

        const std::string ip = policy_json::AsString(policy, "ip_address");
        if (!ip.empty() && !interface.empty())
        {
            return RunCommand("ip addr add " + ip + " dev " + interface);
        }
    }
    if (command == "remove")
    {
        const std::string destination = policy_json::AsString(policy, "destination_prefix");
        const std::string next_hop = policy_json::AsString(policy, "next_hop");
        if (!destination.empty())
        {
            return RunCommand("ip route del " + destination +
                              (next_hop.empty() ? "" : " via " + next_hop));
        }

        const std::string acl_name = policy_json::AsString(policy, "acl_name");
        if (!acl_name.empty())
        {
            const std::string bridge = ReadApplyTarget(policy);
            if (bridge.empty())
            {
                SV_LOG_WARN << "[POLICY] OVS ACL remove without bridge name\n";
                return false;
            }
            return RunCommand("ovs-ofctl -O OpenFlow13 del-flows " + bridge +
                              " cookie=" + kAclCookie + "/-1");
        }
    }
    if (command == "get")
    {
        const auto diagnostic_output = RunCommandOutput("ovs-vsctl show");
        SV_LOG_DEBUG << diagnostic_output;
        return true;
    }
    if (command == "apply")
    {
        return ApplyOpenVSwitchAcl(policy);
    }
    return false;
}

bool PolicyActions::ApplyOpenVSwitchAcl(const Json& policy)
{
    const std::string bridge = ReadApplyTarget(policy);
    if (bridge.empty())
    {
        SV_LOG_WARN << "[POLICY] OVS ACL apply without bridge name\n";
        return false;
    }

    const std::vector<AclEntry> entries = ReadAclEntries(policy);

    std::string bundle = "flow delete cookie=" + std::string(kAclCookie) + "/0xffffffffffffffff\n";
    for (const auto& entry : entries) {
        bundle += "flow add cookie=" + std::string(kAclCookie) + ",priority=" + std::to_string(kAclPriority)
            + ",ip,nw_src=" + entry.source + ",nw_dst=" + entry.destination + ",actions=drop\n";
    }
    if (!RunCommand("printf %s " + ShellQuote(bundle) + " | ovs-ofctl -O OpenFlow14 bundle "
                    + ShellQuote(bridge) + " -")) {
        SV_LOG_WARN << "OVS 정책 교체 실패: 기존 flow 유지";
        return false;
    }
    const auto observed = RunCommandOutput("ovs-ofctl -O OpenFlow14 dump-flows " + ShellQuote(bridge)
                                          + " cookie=" + kAclCookie + "/0xffffffffffffffff");
    if (observed.empty()) return false;
    std::vector<std::string> flows;
    std::istringstream flowOutput(observed);
    std::string line;
    while (std::getline(flowOutput, line))
        if (line.find("cookie=") != std::string::npos) flows.push_back(line);
    if (flows.size() != entries.size()) return false;
    const auto ovsAddress = [](std::string cidr) {
        if (cidr.ends_with("/32")) cidr.resize(cidr.size() - 3);
        return cidr;
    };
    for (const auto& entry : entries) {
        const bool present = std::any_of(flows.begin(), flows.end(), [&](const auto& flow) {
            return flow.find("nw_src=" + ovsAddress(entry.source) + ",") != std::string::npos
                && flow.find("nw_dst=" + ovsAddress(entry.destination) + " ") != std::string::npos
                && flow.find("actions=drop") != std::string::npos;
        });
        if (!present) return false;
    }

    SV_LOG_DEBUG << "[POLICY] OVS ACL applied: bridge=" << bridge
              << " entries=" << entries.size() << '\n';
    return true;
}

bool PolicyActions::ApplyAristaSwitchPolicy(const Json& policy)
{
    const std::string command = policy_json::AsString(policy, "command");
    const std::string interface = policy_json::AsString(policy, "interface",
                                                       policy_json::AsString(policy, "port"));
    std::vector<std::string> commands{"configure terminal"};
    if (command == "on" || command == "off")
    {
        if (!arista_cli::ValidName(interface)) return false;
        commands.push_back("interface " + interface);
        commands.push_back(command == "on" ? "no shutdown" : "shutdown");
    }
    else if (command == "create")
    {
        const std::string vlan = policy_json::AsString(policy, "vlan_id");
        if (!vlan.empty())
        {
            if (!arista_cli::ValidVlan(vlan)) return false;
            commands.push_back("vlan " + vlan);
        }
        else
        {
            const std::string acl = policy_json::AsString(policy, "acl_name");
            if (!arista_cli::ValidName(acl)) return false;
            commands.push_back("ip access-list " + acl);
        }
    }
    else if (command == "get")
    {
        const std::string output = device_.QueryAristaCli("show running-config");
        if (output.empty()) return false;
        SV_LOG_DEBUG << output;
        return true;
    }
    else return false;

    commands.push_back("end");
    return RunCommand(arista_cli::BuildCommand(commands));
}

bool PolicyActions::ApplyCiscoSwitchPolicy(const Json& policy)
{
    (void)policy;
    SV_LOG_WARN << "[POLICY] Cisco switch is not supported yet (only Cisco 8000v)\n";
    return false;
}

bool PolicyActions::ApplyCiscoRouterPolicy(const Json& policy)
{

    const std::string command = policy_json::AsString(policy, "command");
    const std::string interface = policy_json::AsString(policy, "interface");

    if (command == "on")
    {
        return !ExecuteIosCli({"configure terminal", "interface " + interface, "no shutdown"}).empty();
    }
    if (command == "off")
    {
        return !ExecuteIosCli({"configure terminal", "interface " + interface, "shutdown"}).empty();
    }
    if (command == "create")
    {
        const std::string protocol = policy_json::AsString(policy, "protocol");
        if (protocol == "ospf")
        {
            std::vector<std::string> commands = {
                "configure terminal",
                "router ospf " + policy_json::AsString(policy, "process_id", "1")
            };

            const std::string router_id = policy_json::AsString(policy, "router_id");
            if (!router_id.empty())
            {
                commands.push_back("router-id " + router_id);
            }

            const auto networks = policy.find("networks");
            if (networks != policy.end() && networks->is_array())
            {
                for (const auto& network : *networks)
                {
                    const std::string prefix = network.value("network", "");
                    const std::string wildcard = network.value("wildcard_mask", "");
                    const std::string area = network.value("area", "0");
                    if (!prefix.empty())
                    {
                        commands.push_back("network " + prefix + " " + wildcard + " area " + area);
                    }
                }
            }
            return !ExecuteIosCli(commands).empty();
        }

        const std::string log_feature = policy_json::AsString(policy, "log_feature");
        if (!log_feature.empty())
        {
            const std::string server = policy_json::AsString(policy, "logging_server");
            return !ExecuteIosCli({"configure terminal", "logging host " + server}).empty();
        }
    }
    if (command == "apply")
    {
        return ApplyCiscoRouterAcl(policy);
    }
    if (command == "get")
    {
        const auto targets = policy.find("targets");
        if (targets != policy.end() && targets->is_array())
        {
            for (const auto& target : *targets)
            {
                for (const auto& syntax : policy_json::AsStringList(target, "command_syntax"))
                {
                    const auto diagnostic_output = ExecuteIosCli({syntax});
                    SV_LOG_DEBUG << diagnostic_output;
                }
            }
            return true;
        }
    }
    if (command == "remove")
    {
        const std::string destination = policy_json::AsString(policy, "destination_prefix");
        const std::string mask = policy_json::AsString(policy, "subnet_mask");
        const std::string next_hop = policy_json::AsString(policy, "next_hop");
        return !ExecuteIosCli({"configure terminal",
                               "no ip route " + destination + " " + mask + " " + next_hop}).empty();
    }
    return false;
}

bool PolicyActions::ApplyCiscoRouterAcl(const Json& policy)
{
    const std::string acl_name = ReadAclName(policy);
    if (acl_name.empty())
    {
        SV_LOG_WARN << "[POLICY] IOS ACL apply without rule_target.acl_name\n";
        return false;
    }

    const std::string hint = ReadApplyTarget(policy);
    const std::string subnet_cidr = policy_json::AsString(policy, "subnet_cidr");

    const std::string interface_name = ResolveIosBindInterface(
        ExecuteIosCli({"show ip interface brief"}), hint, subnet_cidr);
    if (interface_name.empty()) {
        SV_LOG_WARN << "Cisco ACL 거부: 적용 인터페이스를 하나로 확인할 수 없음";
        return false;
    }
    const std::vector<AclEntry> entries = ReadAclEntries(policy);

    const std::vector<std::string> commands =
        acl::BuildIosAclCommands(acl_name, interface_name, entries);

    const std::string output = ExecuteIosCli(commands);
    if (output.empty()) return false;
    const auto binding = ExecuteIosCli({"show running-config interface " + interface_name});
    if (binding.empty()) return false;
    const bool bound = binding.find("ip access-group " + acl_name + " in") != std::string::npos;
    if (entries.empty()) return !bound;
    if (!bound) return false;
    const auto observed = ExecuteIosCli({"show ip access-lists " + acl_name});
    if (observed.empty() || observed.find("permit ip any any") == std::string::npos) return false;
    const auto endpoint = [](const std::string& address, const std::string& wildcard) {
        if (wildcard == "255.255.255.255") return std::string("any");
        if (wildcard == "0.0.0.0") return std::string("host ") + address;
        return address + " " + wildcard;
    };
    for (const auto& entry : entries) {
        const auto expected = "deny ip " + endpoint(entry.source, entry.source_wildcard)
            + " " + endpoint(entry.destination, entry.destination_wildcard);
        if (observed.find(expected) == std::string::npos) return false;
    }
    return true;
}

bool PolicyActions::ApplyFrrRouterPolicy(const Json& policy)
{
    const std::string command = policy_json::AsString(policy, "command");
    const std::string interface = policy_json::AsString(policy, "interface");

    if (command == "on")
    {
        return RunCommand(VtyshCommand({"configure terminal",
                                       "interface " + interface,
                                       "no shutdown"}));
    }
    if (command == "off")
    {
        return RunCommand(VtyshCommand({"configure terminal",
                                       "interface " + interface,
                                       "shutdown"}));
    }
    if (command == "create")
    {
        const std::string protocol = policy_json::AsString(policy, "protocol");
        if (protocol == "ospf")
        {
            std::vector<std::string> lines = {"configure terminal", "router ospf"};
            const std::string router_id = policy_json::AsString(policy, "router_id");
            if (!router_id.empty())
            {
                lines.push_back("ospf router-id " + router_id);
            }

            const auto networks = policy.find("networks");
            if (networks != policy.end() && networks->is_array())
            {
                for (const auto& network : *networks)
                {
                    const std::string prefix = network.value("network", "");
                    const std::string area = network.value("area", "0");
                    if (!prefix.empty())
                    {
                        lines.push_back("network " + prefix + " area " + area);
                    }
                }
            }
            return RunCommand(VtyshCommand(lines));
        }
    }
    if (command == "get")
    {
        const auto diagnostic_output = RunCommandOutput("vtysh -c 'show ip route'");
        SV_LOG_DEBUG << diagnostic_output;
        return true;
    }
    if (command == "apply")
    {
        return ApplyFrrRouterAcl(policy);
    }
    if (command == "remove")
    {
        const std::string destination = policy_json::AsString(policy, "destination_prefix");
        const std::string next_hop = policy_json::AsString(policy, "next_hop");
        return RunCommand(VtyshCommand({"configure terminal",
                                       "no ip route " + destination + " " + next_hop}));
    }
    return false;
}

bool PolicyActions::ApplyFrrRouterAcl(const Json& policy)
{
    const std::string acl_name = ReadAclName(policy);
    if (acl_name.empty())
    {
        SV_LOG_WARN << "[POLICY] FRR ACL apply without rule_target.acl_name\n";
        return false;
    }

    const std::string interface_name = ReadApplyTarget(policy);
    const std::vector<AclEntry> entries = ReadAclEntries(policy);
    const std::vector<std::string> lines =
        acl::BuildFrrAclCommands(acl_name, interface_name, entries);

    const bool ok = RunCommand(VtyshCommand(lines));
    if (!ok)
    {
        SV_LOG_WARN << "[POLICY] FRR ACL apply failed: acl=" << acl_name
                  << " entries=" << entries.size() << '\n';
        return false;
    }
    SV_LOG_DEBUG << "[POLICY] FRR ACL applied: acl=" << acl_name
              << " entries=" << entries.size() << '\n';
    return true;
}

bool PolicyActions::ApplyNftablesPolicy(const Json& policy)
{
    const std::string command = policy_json::AsString(policy, "command");

    if (command == "create")
    {
        const std::string log_feature = policy_json::AsString(policy, "log_feature");
        if (!log_feature.empty())
        {
            SV_LOG_WARN << "[POLICY] nftables log feature requested: " << log_feature << '\n';
            return true;
        }

        const std::string table_family = policy_json::AsString(policy, "table_family");
        const std::string table_name = policy_json::AsString(policy, "table_name");

        if (policy_json::Has(policy, "chains"))
        {
            std::string script = "add table " + table_family + " " + table_name;
            for (const auto& chain : policy.at("chains"))
            {
                const std::string chain_name = policy_json::AsString(chain, "chain_name");
                const std::string hook = policy_json::AsString(chain, "hook");
                const std::string priority = policy_json::AsString(chain, "priority");
                const std::string default_policy = policy_json::AsString(chain, "policy");
                script += "\nadd chain " + table_family + " " + table_name + " " + chain_name +
                          " { type " + hook + " hook " + hook +
                          " priority " + priority + "; policy " + default_policy + "; }";
            }
            return RunCommand(NftScript(script));
        }

        const auto rule_target = policy.find("rule_target");
        if (rule_target != policy.end())
        {
            const std::string chain_name = policy_json::AsString(*rule_target, "chain_name");

            std::string src;
            std::string dst;
            std::string protocol;
            std::vector<std::string> source_subnets;
            std::vector<std::string> destination_subnets;
            const auto match = policy.find("match_criteria");
            if (match != policy.end())
            {
                src = policy_json::AsString(*match, "ip_saddr");
                dst = policy_json::AsString(*match, "ip_daddr");
                protocol = policy_json::AsString(*match, "protocol");
            }

            const auto source_subnet = policy.find("source_subnet");
            if (source_subnet != policy.end() && source_subnet->is_array())
            {
                for (const auto& subnet : *source_subnet)
                {
                    if (subnet.is_string()) source_subnets.push_back(subnet.get<std::string>());
                }
            }
            const auto destination_subnet = policy.find("destination_subnet");
            if (destination_subnet != policy.end() && destination_subnet->is_array())
            {
                for (const auto& subnet : *destination_subnet)
                {
                    if (subnet.is_string()) destination_subnets.push_back(subnet.get<std::string>());
                }
            }

            const std::string action = policy_json::AsString(policy, "action");
            std::string expr;
            if (!src.empty()) expr += "ip saddr " + src + " ";
            if (!dst.empty()) expr += "ip daddr " + dst + " ";
            if (src.empty() && !source_subnets.empty())
            {
                expr += "ip saddr { ";
                for (const auto& subnet : source_subnets) expr += subnet + ", ";
                expr.erase(expr.size() - 2);
                expr += " } ";
            }
            if (dst.empty() && !destination_subnets.empty())
            {
                expr += "ip daddr { ";
                for (const auto& subnet : destination_subnets) expr += subnet + ", ";
                expr.erase(expr.size() - 2);
                expr += " } ";
            }
            if (!protocol.empty()) expr += "ip protocol " + protocol + " ";

            return RunCommand(NftScript("add rule " + table_family + " " + table_name + " " +
                                        chain_name + " " + expr + action));
        }
    }
    if (command == "remove")
    {
        const std::string table_family = policy_json::AsString(policy, "table_family");
        const std::string table_name = policy_json::AsString(policy, "table_name");
        return RunCommand(NftScript("delete table " + table_family + " " + table_name));
    }
    if (command == "get")
    {
        const auto diagnostic_output = RunCommandOutput("nft list ruleset");
        SV_LOG_DEBUG << diagnostic_output;
        return true;
    }
    return false;
}

bool PolicyActions::ApplyVmPolicy(const Json& policy)
{
    const std::string command = policy_json::AsString(policy, "command");

    const std::string backend = policy_json::AsString(policy, "config_backend");
    if (backend == "netplan" || policy_json::Has(policy, "network_config"))
    {
        return ApplyNetplanPolicy(policy, command);
    }

    const std::string requested = policy_json::AsString(policy, "interface");
    const std::string interface = ResolveInterfaceName(requested);

    if (interface.empty())
    {
        SV_LOG_WARN << "[POLICY] VM policy has no interface\n";
        return false;
    }

    if (command == "on")
    {
        return RunCommand("ip link set " + interface + " up");
    }
    if (command == "off")
    {
        return RunCommand("ip link set " + interface + " down");
    }
    if (command == "get")
    {
        const auto diagnostic_output = RunCommandOutput("ip addr show " + interface);
        SV_LOG_DEBUG << diagnostic_output;
        return true;
    }
    return false;
}

bool PolicyActions::ApplyNetplanPolicy(const Json& policy, const std::string& command)
{
    const auto config = policy.find("network_config");
    if (config == policy.end() || !config->is_object())
    {
        SV_LOG_WARN << "[POLICY] netplan policy has no network_config\n";
        return false;
    }

    const auto ethernets = config->find("ethernets");
    if (ethernets == config->end() || !ethernets->is_object() || ethernets->empty())
    {
        SV_LOG_WARN << "[POLICY] netplan network_config has no ethernets\n";
        return false;
    }

    if (command == "remove")
    {

        const std::string iface = ResolveInterfaceName(
            policy_json::AsString(policy, "interface"));
        std::string script =
            "set -e; "
            "rm -f /etc/netplan/99-sonar-*.yaml; ";
        if (!iface.empty())
        {
            script += "printf 'network:\\n  version: 2\\n  ethernets:\\n    " + iface +
                      ":\\n      dhcp4: true\\n' > /etc/netplan/99-sonar-" + iface + ".yaml; ";
        }
        script += "netplan apply";
        return RunCommand(script);
    }

    if (!HasCommand("netplan"))
    {
        SV_LOG_INFO << "[POLICY] netplan not installed; applying addresses via ip\n";
        return ApplyAddressesWithIp(policy, ethernets, command);
    }

    std::string yaml = "network:\n  version: 2\n  ethernets:\n";

    for (auto entry = ethernets->begin(); entry != ethernets->end(); ++entry)
    {
        const std::string name = ResolveInterfaceName(entry.key());
        const Json& settings = entry.value();
        if (!settings.is_object() || name.empty())
        {
            continue;
        }

        yaml += "    " + name + ":\n";

        const std::string dhcp4 = policy_json::AsString(settings, "dhcp4");
        const bool dhcp_on = dhcp4.empty() || dhcp4 == "true";
        yaml += std::string("      dhcp4: ") + (dhcp_on ? "true" : "false") + "\n";

        if (policy_json::Has(settings, "addresses"))
        {
            yaml += "      addresses:\n";
            for (const std::string& address : policy_json::AsStringList(settings, "addresses"))
            {
                yaml += "        - " + address + "\n";
            }
        }

        const auto routes = settings.find("routes");
        if (routes != settings.end() && routes->is_array())
        {
            yaml += "      routes:\n";
            for (const auto& route : *routes)
            {
                const std::string to = policy_json::AsString(route, "to");
                const std::string via = policy_json::AsString(route, "via");
                if (to.empty() || via.empty())
                {
                    continue;
                }
                yaml += "        - to: " + to + "\n          via: " + via + "\n";
            }
        }

        const auto nameservers = settings.find("nameservers");
        if (nameservers != settings.end())
        {
            const auto addresses = nameservers->find("addresses");
            if (addresses != nameservers->end() && addresses->is_array() && !addresses->empty())
            {
                yaml += "      nameservers:\n        addresses:\n";
                for (const auto& server : *addresses)
                {
                    if (server.is_string())
                    {
                        yaml += "          - " + server.get<std::string>() + "\n";
                    }
                }
            }
        }
    }

    const std::string target = "/etc/netplan/99-sonar-policy.yaml";
    const std::string script =
        "set -e; "
        "cat > " + target + " <<'SONAR_NETPLAN_EOF'\n" + yaml + "SONAR_NETPLAN_EOF\n"
        "netplan generate 2>&1 || { echo '[POLICY] netplan generate failed' >&2; exit 1; }; "
        "netplan apply 2>&1 || { echo '[POLICY] netplan apply failed' >&2; exit 1; }; "
        "echo '[POLICY] netplan applied'";

    const bool ok = RunCommand(script);
    if (ok)
    {
        SV_LOG_DEBUG << "[POLICY] netplan policy applied (" << target << ")\n";
    }
    else
    {
        SV_LOG_WARN << "[POLICY] netplan policy failed; keeping previous config\n";
    }
    return ok;
}

PolicyExecutorRegistry DefaultPolicyExecutors() {
    PolicyExecutorRegistry registry;
    using Action = bool (PolicyActions::*)(const Json&);
    auto add = [&](const std::string& id, DeviceType type, std::vector<std::string> products, Action action) {
        registry.Register(std::make_shared<FunctionalPolicyExecutor>(id,
            [=](const PolicyCommand& command) {
                return command.device_type == type && (products.empty() ||
                    std::find(products.begin(), products.end(), command.product) != products.end());
            },
            [=](DeviceCommandExecutor& device, const PolicyCommand& command) {
                PolicyActions actions(device);
                return (actions.*action)(command.payload);
            }));
    };
    add("ovs", DeviceType::kSwitch, {"OpenVSwitch"}, &PolicyActions::ApplyOpenVSwitchPolicy);
    add("arista", DeviceType::kSwitch, {"Arista"}, &PolicyActions::ApplyAristaSwitchPolicy);
    add("cisco-switch", DeviceType::kSwitch, {"Cisco"}, &PolicyActions::ApplyCiscoSwitchPolicy);
    add("cisco-router", DeviceType::kRouter, {"Cisco 8000v", "Cisco IOS XE", "Cisco"}, &PolicyActions::ApplyCiscoRouterPolicy);
    add("frr", DeviceType::kRouter, {"FRR"}, &PolicyActions::ApplyFrrRouterPolicy);
    add("nftables", DeviceType::kFirewall, {"nftables"}, &PolicyActions::ApplyNftablesPolicy);
    add("vm", DeviceType::kVirtualMachine, {}, &PolicyActions::ApplyVmPolicy);
    return registry;
}
