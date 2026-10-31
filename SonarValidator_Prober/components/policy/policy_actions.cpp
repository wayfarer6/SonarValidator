#include <algorithm>
#include "components/policy/policy_actions.hpp"
#include "components/policy/policy_executor.hpp"
#include "components/policy/policy_json.hpp"
#include "components/policy/acl_commands.hpp"
#include "components/terminal/ios_snapshot.hpp"
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

std::string IosApplyDir()
{
    return runtime_environment::DataDirectory() + "/ios_apply";
}

std::string Fingerprint(const std::string& text)
{
    unsigned long long hash = 5381ULL;
    for (const unsigned char ch : text)
    {
        hash = (hash * 33ULL) ^ ch;
    }
    char buffer[17]{};
    std::snprintf(buffer, sizeof(buffer), "%016llx", hash);
    return buffer;
}

std::string ReadStateValue(const std::string& path, const std::string& key)
{
    std::ifstream input(path);
    if (!input)
    {
        return {};
    }
    const std::string prefix = key + "=";
    std::string line;
    while (std::getline(input, line))
    {
        if (line.rfind(prefix, 0) == 0)
        {
            return line.substr(prefix.size());
        }
    }
    return {};
}

bool WriteIosAclRequest(const std::vector<std::string>& commands)
{
    const std::string dir = IosApplyDir();
    if (::mkdir(dir.c_str(), 0775) != 0 && errno != EEXIST)
    {
        std::cerr << "[POLICY] cannot create IOS apply dir: " << dir
                  << " (" << std::strerror(errno) << ")\n";
        return false;
    }

    std::string body;
    for (const auto& command : commands)
    {
        body += command;
        body += '\n';
    }

    const std::string target = dir + "/acl_desired.cmd";
    const std::string tmp = target + ".tmp";
    {
        std::ofstream output(tmp, std::ios::trunc);
        if (!output)
        {
            std::cerr << "[POLICY] cannot write IOS apply file: " << tmp << '\n';
            return false;
        }
        output << "# fingerprint=" << Fingerprint(body) << '\n';
        output << body;
        output.flush();
        if (!output)
        {
            std::cerr << "[POLICY] IOS apply file write failed: " << tmp << '\n';
            return false;
        }
    }
    if (std::rename(tmp.c_str(), target.c_str()) != 0)
    {
        std::cerr << "[POLICY] cannot publish IOS apply file: " << target << '\n';
        return false;
    }

    const std::string applied = ReadStateValue(dir + "/acl_applied.state", "applied");
    const std::string pending = ReadStateValue(dir + "/acl_applied.state", "pending");
    const std::string desired = Fingerprint(body);
    if (!pending.empty() && applied != pending)
    {
        std::cerr << "[POLICY] IOS ACL queued but device has NOT applied the previous "
                     "request (EEM apply may be down): desired="
                  << desired << " pending=" << pending << " applied=" << applied << '\n';
    }
    std::cout << "[POLICY] IOS ACL queued for EEM apply: fingerprint=" << desired
              << " commands=" << commands.size() << '\n';
    return true;
}

std::string ReadIosCliSnapshot(const std::string& script)
{
    return ios_snapshot::Read(runtime_environment::DataDirectory(), script);
}

bool ParseIpv4(const std::string& text, unsigned int& value)
{
    unsigned int address = 0;
    int octets = 0;
    int shift = 24;
    std::size_t index = 0;

    while (index <= text.size())
    {
        const std::size_t dot = text.find('.', index);
        const std::string part = text.substr(
            index, dot == std::string::npos ? std::string::npos : dot - index);
        if (part.empty())
        {
            return false;
        }
        int octet = 0;
        for (const char ch : part)
        {
            if (std::isdigit(static_cast<unsigned char>(ch)) == 0)
            {
                return false;
            }
            octet = (octet * 10) + (ch - '0');
        }
        if (octet > 255 || shift < 0)
        {
            return false;
        }
        address |= static_cast<unsigned int>(octet) << shift;
        ++octets;
        shift -= 8;

        if (dot == std::string::npos)
        {
            break;
        }
        index = dot + 1;
    }

    if (octets != 4)
    {
        return false;
    }
    value = address;
    return true;
}

bool IpInCidr(const std::string& address, const std::string& cidr)
{
    const std::size_t slash = cidr.find('/');
    if (slash == std::string::npos)
    {
        return false;
    }

    unsigned int host = 0;
    unsigned int base = 0;
    if (!ParseIpv4(address, host) || !ParseIpv4(cidr.substr(0, slash), base))
    {
        return false;
    }

    int prefix = 0;
    const std::string prefix_text = cidr.substr(slash + 1);
    if (prefix_text.empty() || prefix_text.size() > 2)
    {
        return false;
    }
    for (const char ch : prefix_text)
    {
        if (std::isdigit(static_cast<unsigned char>(ch)) == 0)
        {
            return false;
        }
        prefix = (prefix * 10) + (ch - '0');
    }
    if (prefix > 32)
    {
        return false;
    }

    const unsigned int mask = (prefix == 0) ? 0u : (0xffffffffu << (32 - prefix));
    return (host & mask) == (base & mask);
}

std::vector<std::pair<std::string, std::string>> ReadIosInterfaces()
{
    std::vector<std::pair<std::string, std::string>> interfaces;

    const std::string snapshot = ReadIosCliSnapshot("show ip interface brief");
    if (snapshot.empty())
    {
        return interfaces;
    }

    std::istringstream stream(snapshot);
    std::string line;
    bool header_seen = false;
    while (std::getline(stream, line))
    {
        if (!header_seen)
        {
            header_seen = line.rfind("Interface", 0) == 0;
            continue;
        }
        std::istringstream fields(line);
        std::string name;
        std::string address;
        if (!(fields >> name >> address))
        {
            continue;
        }
        unsigned int ignored = 0;
        if (!ParseIpv4(address, ignored))
        {
            continue;
        }
        interfaces.emplace_back(name, address);
    }
    return interfaces;
}

std::string ResolveIosBindInterface(const std::string& hint, const std::string& subnet_cidr)
{
    const auto interfaces = ReadIosInterfaces();
    if (interfaces.empty())
    {
        std::cerr << "[POLICY] IOS interface table unavailable (EEM snapshot missing); "
                     "ACL will be created without interface binding\n";
        return {};
    }

    if (!subnet_cidr.empty())
    {
        for (const auto& [name, address] : interfaces)
        {
            if (IpInCidr(address, subnet_cidr))
            {
                std::cout << "[POLICY] IOS bind interface " << name << " (holds "
                          << subnet_cidr << ")\n";
                return name;
            }
        }
    }

    if (!hint.empty())
    {
        for (const auto& [name, address] : interfaces)
        {
            if (name == hint)
            {
                return name;
            }
        }
        std::cerr << "[POLICY] server interface hint '" << hint
                  << "' does not exist on this device; not binding\n";
    }
    return {};
}

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
        std::cout << "[POLICY] primary interface resolved: " << name << '\n';
        return primary_interface_;
    }

    std::cerr << "[POLICY] could not resolve primary interface\n";
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
            std::cerr << "[POLICY] skipping unresolvable interface '" << entry.key() << "'\n";
            all_ok = false;
            continue;
        }

        for (const std::string& address : policy_json::AsStringList(settings, "addresses"))
        {
            const std::string script =
                "ip addr replace " + address + " dev " + name;
            if (!RunCommand(script))
            {
                std::cerr << "[POLICY] failed to set address " << address
                          << " on " << name << '\n';
                all_ok = false;
            }
            else
            {
                std::cout << "[POLICY] address " << address << " on " << name << '\n';
            }
        }

        if (!RunCommand("ip link set " + name + " up"))
        {
            std::cerr << "[POLICY] failed to bring up " << name << '\n';
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
                    std::cerr << "[POLICY] failed to add route " << to
                              << " via " << via << '\n';
                    all_ok = false;
                }
                else
                {
                    std::cout << "[POLICY] route " << to << " via " << via << '\n';
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
                std::cerr << "[POLICY] OVS ACL needs source/destination/bridge\n";
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
                std::cerr << "[POLICY] OVS ACL remove without bridge name\n";
                return false;
            }
            return RunCommand("ovs-ofctl -O OpenFlow13 del-flows " + bridge +
                              " cookie=" + kAclCookie + "/-1");
        }
    }
    if (command == "get")
    {
        std::cout << RunCommandOutput("ovs-vsctl show");
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
        std::cerr << "[POLICY] OVS ACL apply without bridge name\n";
        return false;
    }

    const std::vector<AclEntry> entries = ReadAclEntries(policy);

    const std::string delete_command =
        "ovs-ofctl -O OpenFlow13 del-flows " + bridge + " cookie=" + kAclCookie + "/-1";
    if (!RunCommand(delete_command))
    {
        std::cerr << "[POLICY] OVS flow cleanup failed on bridge " << bridge << '\n';
        return false;
    }

    for (const auto& entry : entries)
    {
        const std::string add_command =
            "ovs-ofctl -O OpenFlow13 add-flow " + bridge +
            " cookie=" + kAclCookie + ",priority=" + std::to_string(kAclPriority) +
            ",ip,nw_src=" + entry.source + ",nw_dst=" + entry.destination +
            ",actions=drop";
        if (!RunCommand(add_command))
        {
            std::cerr << "[POLICY] OVS flow add failed: " << entry.source << " -> "
                      << entry.destination << '\n';
            return false;
        }
    }

    std::cout << "[POLICY] OVS ACL applied: bridge=" << bridge
              << " entries=" << entries.size() << '\n';
    return true;
}

bool PolicyActions::ApplyAristaSwitchPolicy(const Json& policy)
{
    const std::string command = policy_json::AsString(policy, "command");
    const std::string interface = policy_json::AsString(policy, "interface",
                                                        policy_json::AsString(policy, "port"));

    if (command == "on")
    {
        return !CliCommand({"FastCli"}, "enable\nconfigure\ninterface " + interface + "\nno shutdown").empty();
    }
    if (command == "off")
    {
        return !CliCommand({"FastCli"}, "enable\nconfigure\ninterface " + interface + "\nshutdown").empty();
    }
    if (command == "create")
    {
        const std::string vlan_id = policy_json::AsString(policy, "vlan_id");
        if (!vlan_id.empty())
        {
            return !CliCommand({"FastCli"}, "enable\nconfigure\nvlan " + vlan_id).empty();
        }

        const std::string acl_name = policy_json::AsString(policy, "acl_name");
        if (!acl_name.empty())
        {
            return !CliCommand({"FastCli"}, "enable\nconfigure\nip access-list " + acl_name).empty();
        }
    }
    if (command == "get")
    {
        std::cout << CliCommand({"FastCli"}, "show running-config");
        return true;
    }
    return false;
}

bool PolicyActions::ApplyCiscoSwitchPolicy(const Json& policy)
{
    (void)policy;
    std::cerr << "[POLICY] Cisco switch is not supported yet (only Cisco 8000v)\n";
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
                    std::cout << ExecuteIosCli({syntax});
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
        std::cerr << "[POLICY] IOS ACL apply without rule_target.acl_name\n";
        return false;
    }

    const std::string hint = ReadApplyTarget(policy);
    const std::string subnet_cidr = policy_json::AsString(policy, "subnet_cidr");

    const std::string interface_name = ResolveIosBindInterface(hint, subnet_cidr);
    const std::vector<AclEntry> entries = ReadAclEntries(policy);

    const std::vector<std::string> commands =
        acl::BuildIosAclCommands(acl_name, interface_name, entries);

    const std::string output = ExecuteIosCli(commands);
    if (output.empty())
    {
        if (WriteIosAclRequest(commands))
        {
            std::cerr << "[POLICY] IOS ACL request queued; device application is unconfirmed\n";
            return false;
        }
        std::cerr << "[POLICY] IOS ACL apply failed: acl=" << acl_name
                  << " entries=" << entries.size() << " (direct and queued both failed)\n";
        return false;
    }
    std::cout << "[POLICY] IOS ACL applied directly: acl=" << acl_name
              << " entries=" << entries.size()
              << " interface=" << (interface_name.empty() ? "<none>" : interface_name) << '\n';
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
        std::cout << RunCommandOutput("vtysh -c 'show ip route'");
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
        std::cerr << "[POLICY] FRR ACL apply without rule_target.acl_name\n";
        return false;
    }

    const std::string interface_name = ReadApplyTarget(policy);
    const std::vector<AclEntry> entries = ReadAclEntries(policy);
    const std::vector<std::string> lines =
        acl::BuildFrrAclCommands(acl_name, interface_name, entries);

    const bool ok = RunCommand(VtyshCommand(lines));
    if (!ok)
    {
        std::cerr << "[POLICY] FRR ACL apply failed: acl=" << acl_name
                  << " entries=" << entries.size() << '\n';
        return false;
    }
    std::cout << "[POLICY] FRR ACL applied: acl=" << acl_name
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
            std::cerr << "[POLICY] nftables log feature requested: " << log_feature << '\n';
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
        std::cout << RunCommandOutput("nft list ruleset");
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
        std::cerr << "[POLICY] VM policy has no interface\n";
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
        std::cout << RunCommandOutput("ip addr show " + interface);
        return true;
    }
    return false;
}

bool PolicyActions::ApplyNetplanPolicy(const Json& policy, const std::string& command)
{
    const auto config = policy.find("network_config");
    if (config == policy.end() || !config->is_object())
    {
        std::cerr << "[POLICY] netplan policy has no network_config\n";
        return false;
    }

    const auto ethernets = config->find("ethernets");
    if (ethernets == config->end() || !ethernets->is_object() || ethernets->empty())
    {
        std::cerr << "[POLICY] netplan network_config has no ethernets\n";
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
        std::cout << "[POLICY] netplan not installed; applying addresses via ip\n";
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
        std::cout << "[POLICY] netplan policy applied (" << target << ")\n";
    }
    else
    {
        std::cerr << "[POLICY] netplan policy failed; keeping previous config\n";
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
