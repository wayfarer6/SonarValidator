#include "management_service.hpp"
#include "policy/policy_json.hpp"
#include <nlohmann/json.hpp>
#include <utility>
#include <sstream>
#include <iostream>
#include <cstdio>
#include <array>
#include <chrono>
#include <fstream>
#include <sys/wait.h>

struct TerminalHandler
{
    void operator()(FILE* pipe) const
    {
        if (pipe != nullptr)
        {
            pclose(pipe);
        }
    }
};

using TerminalFile = std::unique_ptr<FILE, TerminalHandler>;

ManagementService::ManagementService()
    : host_(), port_(0), target_(), ioc_(), resolver_(ioc_), stream_(ioc_), connected_(false)
{
}

ManagementService::ManagementService(std::string host, int port, std::string target)
    : host_(std::move(host)), port_(port), target_(std::move(target)), ioc_(), resolver_(ioc_), stream_(ioc_), connected_(false)
{
}

ManagementService::~ManagementService()
{
    if (connected_)
    {
        try
        {
            stream_.close(websocket::close_code::normal);
        }
        catch (...)
        {
        }
    }
}

bool ManagementService::connect()
{
    try
    {
        if (host_.empty() || port_ <= 0)
        {
            return false;
        }

        auto const results = resolver_.resolve(host_, std::to_string(port_));
        beast::get_lowest_layer(stream_).connect(results);
        stream_.handshake(host_, target_);
        connected_ = true;
        return true;
    }
    catch (...)
    {
        connected_ = false;
        return false;
    }
}

Json ManagementService::sendText(const std::string &message)
{
    if (!connected_)
    {
        return {};
    }

    try
    {
        stream_.write(net::buffer(message));
        beast::flat_buffer buffer; // 동적 버퍼 boost library가 제공
        stream_.read(buffer);
        const std::string response = beast::buffers_to_string(buffer.data());
        Json result = Json::parse(response);
        return result;
    }
    catch (...)
    {
        connected_ = false;
        return {};
    }
}

std::string ManagementService::receiveText()
{
    if (!connected_)
    {
        return {};
    }

    try
    {
        beast::flat_buffer buffer;
        std::string message;
        stream_.read(buffer);
        message.assign(static_cast<const char *>(buffer.data().data()), buffer.size());
        return message;
    }
    catch (...)
    {
        connected_ = false;
        return {};
    }
}

bool ManagementService::applyPolicy(const std::string& policy_name, const std::string& payload)
{
    (void)policy_name;
    if (!connected_ && !connect())
    {
        return false;
    }

    return sendText(payload);
}

Json ManagementService::fetchPolicy(const DeviceType device_type,const std::string& device_id)
{
    if(!connected_ && !connect())
    {
        return false;
    }
    Json payload_json = Json();
    payload_json["device_type"] = device_type;
    payload_json["device_id"] = device_id;

    const std::string payload = to_string(payload_json);

    try {
        return sendText(payload);
        // 보낸 다음에 답을 받을려면 

    } catch(...) {
        connected_ = false;
        return {};
    }
}

bool ManagementService::processOpenVSwitchPolicy(const Json& policy_payload)
{
   


    

    return true;
}



bool ManagementService::processAristaSwitchPolicy(const Json& policy_payload)
{
     // OpenVSwitch Policy 처리 로직
    std::string command = "";
    
    // 예시
    //command = "sudo ovs-vsctl set bridge br0 other-config:policy=" + policy_payload["policy"] ;
    
    
    commandAristaSwitch(command);
    return true;
}

Json ManagementService::commandAristaSwitch(const std::string& comm )
{
        // AristaVEos Policy 처리 로직
    std::string command = " FastCli -c";
    //command =+ "\"show version\"";
    command += comm;

    // 임시 버퍼 (128바이트씩 쪼개서 안전하게 읽음)
    std::array<char, 128> buffer;
    std::string result;


    TerminalFile terminal_file (popen(command.c_str(), "r"));

    if (!terminal_file) throw std::runtime_error("Can't open Terminal!");

    // 버퍼 단위로 읽어서 하나의 string에 병합

    while (fgets(buffer.data(), buffer.size(), terminal_file.get()) != nullptr) {
        result += buffer.data();
    }

    // string을 입력 스트림(stringstream)으로 변환
    std::stringstream stream(result);
    
    // 이제 일반적인 std::istream처럼 사용 가능
    std::string line;
    while (std::getline(stream, line)) {
        std::cout << line << std::endl;
    }

    return Json{};
}

void ManagementService::commandAristaSwitch_no_return(const std::string& comm )
{
        // AristaVEos Policy 처리 로직
    std::string command = " FastCli -c";
    command =+ "\"show version\"";


    // 임시 버퍼 (128바이트씩 쪼개서 안전하게 읽음)
    std::array<char, 128> buffer;
    std::string result;


    TerminalFile terminal_file (popen(command.c_str(), "r"));

    if (!terminal_file) throw std::runtime_error("Can't open Terminal!");

}

bool ManagementService::RunCommand(const std::string& command)
{
    FILE* pipe = popen(command.c_str(), "r");
    if (pipe == nullptr)
    {
        return false;
    }

    std::array<char, 256> buffer;
    while (std::fgets(buffer.data(), buffer.size(), pipe) != nullptr)
    {
        // 출력을 소비해 파이프 블로킹을 방지합니다.
    }

    const int status = pclose(pipe);
    return WIFEXITED(status) && WEXITSTATUS(status) == 0;
}

std::string ManagementService::RunCommandOutput(const std::string& command)
{
    TerminalFile pipe(popen(command.c_str(), "r"));
    if (!pipe)
    {
        return {};
    }

    std::array<char, 128> buffer;
    std::string result;
    while (std::fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr)
    {
        result += buffer.data();
    }
    return result;
}

std::string ManagementService::CliCommand(const std::vector<std::string>& argv,
                                          const std::string& command)
{
    if (argv.empty())
    {
        return {};
    }

    // 세션이 없거나 다른 프로그램이면 새로 엽니다.
    if (!cli_session_.IsOpen() || cli_program_ != argv[0])
    {
        cli_session_.Close();
        cli_program_.clear();
        if (!cli_session_.Open(argv))
        {
            std::cerr << "[CLI] failed to open session for " << argv[0] << '\n';
            return {};
        }
        cli_program_ = argv[0];
        // 초기 배너/프롬프트를 소비합니다.
        cli_session_.ReadAvailable(std::chrono::milliseconds(300));
    }

    if (!cli_session_.Write(command))
    {
        return {};
    }
    return cli_session_.ReadAvailable(std::chrono::milliseconds(300));
}

bool ManagementService::ApplyOpenVSwitchPolicy(const Json& policy)
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
            const std::string applied = policy_json::AsString(policy, "applied_interface");
            return RunCommand("ovs-ofctl add-flow " + applied +
                              " priority=100,ip,nw_src=" + src + ",nw_dst=" + dst +
                              ",actions=" + (action == "deny" ? "drop" : "normal"));
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
            return RunCommand("ovs-ofctl del-flows " +
                              policy_json::AsString(policy, "applied_interface"));
        }
    }
    if (command == "get")
    {
        std::cout << RunCommandOutput("ovs-vsctl show");
        return true;
    }
    return false;
}

bool ManagementService::ApplyAristaSwitchPolicy(const Json& policy)
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

std::string ManagementService::ExecuteIosCli(const std::vector<std::string>& cli_commands)
{
    // Cisco IOS-XE guestshell의 dohost 유틸로 IOS CLI를 실행합니다.
    // dohost는 guestshell ↔ IOS-XE 간 로컬 IPC라 인증 정보가 필요 없습니다.
    std::string output;
    for (const auto& command : cli_commands)
    {
        output += RunCommandOutput("dohost \"" + command + "\"");
    }
    return output;
}

bool ManagementService::ApplyCiscoSwitchPolicy(const Json& policy)
{
    // 현재 Cisco는 8000v(라우터)만 지원합니다. 스위치(예: Catalyst 9000v)는 추후 지원 예정.
    (void)policy;
    std::cerr << "[POLICY] Cisco switch is not supported yet (only Cisco 8000v)\n";
    return false;
}

bool ManagementService::ApplyCiscoRouterPolicy(const Json& policy)
{
    // Cisco 8000v(IOS-XE)는 guestshell에서 프로버 바이너리가 실행됩니다.
    // 서버 ↔ 프로버는 SSH가 아니라 WebSocket으로 통신하며(이미 fetchPolicy가 수행),
    // guestshell → IOS-XE 설정은 dohost 명령(인증 불필요)으로 적용합니다.
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

bool ManagementService::ApplyFrrRouterPolicy(const Json& policy)
{
    const std::string command = policy_json::AsString(policy, "command");
    const std::string interface = policy_json::AsString(policy, "interface");

    if (command == "on")
    {
        return !CliCommand({"vtysh"}, "configure terminal\ninterface " + interface + "\nno shutdown").empty();
    }
    if (command == "off")
    {
        return !CliCommand({"vtysh"}, "configure terminal\ninterface " + interface + "\nshutdown").empty();
    }
    if (command == "create")
    {
        const std::string protocol = policy_json::AsString(policy, "protocol");
        if (protocol == "ospf")
        {
            const std::string router_id = policy_json::AsString(policy, "router_id");
            std::string script = "configure terminal\nrouter ospf";
            if (!router_id.empty())
            {
                script += "\nospf router-id " + router_id;
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
                        script += "\nnetwork " + prefix + " area " + area;
                    }
                }
            }
            return !CliCommand({"vtysh"}, script).empty();
        }
    }
    if (command == "get")
    {
        std::cout << CliCommand({"vtysh"}, "show ip route");
        return true;
    }
    if (command == "remove")
    {
        const std::string destination = policy_json::AsString(policy, "destination_prefix");
        const std::string next_hop = policy_json::AsString(policy, "next_hop");
        return !CliCommand({"vtysh"},
                           "configure terminal\nno ip route " + destination + " " + next_hop).empty();
    }
    return false;
}

bool ManagementService::ApplyNftablesPolicy(const Json& policy)
{
    const std::string command = policy_json::AsString(policy, "command");

    if (command == "create")
    {
        const std::string log_feature = policy_json::AsString(policy, "log_feature");
        if (!log_feature.empty())
        {
            // syslog/ulog 연동은 rsyslog/systemd-journald 설정이 필요합니다.
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
            return !CliCommand({"nft", "-i"}, script).empty();
        }

        const auto rule_target = policy.find("rule_target");
        if (rule_target != policy.end())
        {
            const std::string chain_name = policy_json::AsString(*rule_target, "chain_name");

            std::string src;
            std::string dst;
            std::string protocol;
            const auto match = policy.find("match_criteria");
            if (match != policy.end())
            {
                src = policy_json::AsString(*match, "ip_saddr");
                dst = policy_json::AsString(*match, "ip_daddr");
                protocol = policy_json::AsString(*match, "protocol");
            }

            const std::string action = policy_json::AsString(policy, "action");
            std::string expr;
            if (!src.empty()) expr += "ip saddr " + src + " ";
            if (!dst.empty()) expr += "ip daddr " + dst + " ";
            if (!protocol.empty()) expr += "ip protocol " + protocol + " ";

            return !CliCommand({"nft", "-i"},
                               "add rule " + table_family + " " + table_name + " " +
                               chain_name + " " + expr + action).empty();
        }
    }
    if (command == "remove")
    {
        const std::string table_family = policy_json::AsString(policy, "table_family");
        const std::string table_name = policy_json::AsString(policy, "table_name");
        return !CliCommand({"nft", "-i"},
                           "delete table " + table_family + " " + table_name).empty();
    }
    if (command == "get")
    {
        std::cout << CliCommand({"nft", "-i"}, "list ruleset");
        return true;
    }
    return false;
}