#include "module/management_module/management_service.hpp"
#include "components/backend_communication/connect_with_timeout.hpp"
#include "components/policy/policy_json.hpp"
#include <nlohmann/json.hpp>
#include <utility>
#include <future>
#include <sstream>
#include <iostream>
#include <cstdio>
#include <array>
#include <chrono>
#include <fstream>
#include <stop_token>
#include <string>
#include <vector>
#include <poll.h>
#include <sys/socket.h>
#include <sys/wait.h>

namespace
{

// 셸 인자를 안전한 단일 인용부호로 감쌉니다.
//
// nft 스크립트나 vtysh 명령은 따옴표가 들어갈 수 있고, 셸에 그대로 붙이면
// 그 지점에서 명령이 쪼개집니다. 단일 인용부호 안에서는 모든 문자가
// 리터럴이므로, 내부의 ' 를 '\'' 로 바꿔 넣는 방식이 가장 안전합니다.
std::string ShellQuote(const std::string& text)
{
    std::string out = "'";
    for (const char ch : text)
    {
        if (ch == '\'')
        {
            out += "'\\''";
        }
        else
        {
            out += ch;
        }
    }
    out += "'";
    return out;
}

// vtysh 는 -c 를 여러 번 받아 순서대로 실행합니다.
//
// ⚠️ pty 세션(CliCommand)을 쓰지 않는 이유:
//   vtysh 는 성공해도 출력이 없는 경우가 많아(설정 명령) 출력 유무로
//   성공을 판정할 수 없습니다. 그래서 종료코드를 보는 RunCommand 를 씁니다.
std::string VtyshCommand(const std::vector<std::string>& lines)
{
    std::string out = "vtysh";
    for (const std::string& line : lines)
    {
        out += " -c " + ShellQuote(line);
    }
    return out;
}

// nft 는 -i 로 표준입력 스크립트를 받습니다. (한 번에 여러 줄 적용)
std::string NftScript(const std::string& script)
{
    return "nft -i " + ShellQuote(script);
}

} // namespace

bool ManagementService::HasCommand(const std::string& program)
{
    if (program.empty())
    {
        return false;
    }
    return RunCommand("command -v " + program + " >/dev/null 2>&1");
}

std::string ManagementService::PrimaryInterface()
{
    // 이미 찾았으면 그대로 돌려줍니다. (매 정책마다 ip 를 돌리지 않기)
    if (!primary_interface_.empty())
    {
        return primary_interface_;
    }

    // `ip -o -4 addr show` 는 "1: eth0    inet 10.20.111.10/24 ..." 형태입니다.
    // 주소를 가진 첫 인터페이스를 기본 인터페이스로 봅니다(lo 제외).
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

std::string ManagementService::ResolveInterfaceName(const std::string& name)
{
    // 서버는 장치의 인터페이스 이름을 모르므로 자리표시자를 보냅니다.
    // (고정 이름을 쓰면 `Cannot find device` 로 모든 VM 정책이 실패함)
    if (name == "__primary__")
    {
        return PrimaryInterface();
    }
    return name;
}

bool ManagementService::ApplyAddressesWithIp(const Json& policy,
                                            const Json::const_iterator& ethernets,
                                            const std::string& command)
{
    (void)policy;
    (void)command;

    bool all_ok = true;
    for (auto entry = ethernets->begin(); entry != ethernets->end(); ++entry)
    {
        // 자리표시자를 장치의 실제 인터페이스로 치환합니다.
        const std::string name = ResolveInterfaceName(entry.key());
        const Json& settings = entry.value();
        if (!settings.is_object() || name.empty())
        {
            std::cerr << "[POLICY] skipping unresolvable interface '" << entry.key() << "'\n";
            all_ok = false;
            continue;
        }

        // 주소를 부여합니다. dhcp4=false 인데 주소가 없으면 손대지 않습니다.
        for (const std::string& address : policy_json::AsStringList(settings, "addresses"))
        {
            // 같은 주소를 반복 적용하면 "File exists" 로 실패하므로
            // 기존 주소를 지우고 넣습니다(멱등).
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

        // 인터페이스를 올립니다. 주소만 넣고 down 이면 통신이 되지 않습니다.
        if (!RunCommand("ip link set " + name + " up"))
        {
            std::cerr << "[POLICY] failed to bring up " << name << '\n';
            all_ok = false;
        }

        // 기본 게이트웨이/정적 경로를 넣습니다.
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
                // replace 로 멱등하게 넣습니다. (기존 경로가 있어도 교체)
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

        // 제한 시간이 있는 연결입니다. 자세한 이유는 ConnectWithTimeout 주석 참고
        // (표준 동기 connect 에는 타임아웃이 없고,
        //  beast::tcp_stream::expires_after() 는 비동기 전용이며,
        //  std::async + wait_for 는 future 소멸자에서 다시 블록된다)
        const boost::system::error_code ec =
            sonar::net::ConnectWithTimeout(stream_, results, kConnectTimeout);
        if (ec)
        {
            connected_ = false;
            return false;
        }

        // 핸드셰이크는 ConnectWithTimeout 이 설정한 소켓 타임아웃
        // (SO_RCVTIMEO/SO_SNDTIMEO) 안에서 끝나거나 예외로 실패합니다.
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

void ManagementService::SetAgentId(std::string agent_id)
{
    agent_id_ = std::move(agent_id);
}

bool ManagementService::SendEnvelope(const nlohmann::json& message)
{
    if (!connected_ && !connect())
    {
        return false;
    }

    try
    {
        stream_.write(net::buffer(message.dump()));
        return true;
    }
    catch (...)
    {
        connected_ = false;
        return false;
    }
}

bool ManagementService::TryReceive(std::string& message, std::chrono::milliseconds timeout)
{
    if (!connected_)
    {
        return false;
    }

    // 이전 호출에서 남은 완전한 프레임이 있으면 즉시 돌려줍니다.
    if (read_buffer_.size() > 0) // 남은 버퍼를 서버로 보내 주는 듯함.
    {
        const std::string pending = beast::buffers_to_string(read_buffer_.data());
        try
        {
            (void)nlohmann::json::parse(pending);
            message = pending;
            read_buffer_.consume(read_buffer_.size());
            return true;
        }
        catch (const std::exception&)
        {
            // 아직 덜 온 프레임입니다. 아래에서 더 읽습니다.
        }
    }

    boost::system::error_code ec;
    auto& socket = beast::get_lowest_layer(stream_).socket();
    socket.native_non_blocking(true, ec);
    if (ec)
    {
        return false;
    }

    const auto deadline = std::chrono::steady_clock::now() + timeout;
    bool received = false;

    while (std::chrono::steady_clock::now() < deadline)
    {
        const std::size_t document_size = read_buffer_.size();
        stream_.read_some(read_buffer_, 65536, ec);

        if (ec == websocket::error::closed)
        {
            connected_ = false;
            break;
        }

        if (!ec && read_buffer_.size() > document_size)
        {
            const std::string raw = beast::buffers_to_string(read_buffer_.data());
            try
            {
                // 완전한 JSON 프레임이 도착했는지 검증합니다.
                (void)nlohmann::json::parse(raw); // 파서 돌리면 직렬화 실패하면 바로 안된거니까... ok... 이런방법이 있네
                message = raw;
                read_buffer_.consume(read_buffer_.size());
                received = true;
                break;
            }
            catch (const std::exception&)
            {
                // 부분 프레임이면 계속 누적합니다.
            }
        }

        if (ec == boost::asio::error::would_block || ec == boost::asio::error::try_again)
        {
            ec.clear();
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
            continue;
        }

        if (ec)
        {
            break;
        }
    }

    socket.native_non_blocking(false, ec);
    return received;
}

std::string ManagementService::ResolveAgentId(const std::string& device_id) const
{
    if (!agent_id_.empty())
    {
        return agent_id_;
    }
    return device_id.empty() ? "unknown" : device_id;
}

nlohmann::json ManagementService::fetchPolicy(const DeviceType device_type,
                                              const std::string& device_id,
                                              std::stop_token stop_token)
{
    if (!connected_ && !connect())
    {
        return {};
    }

    const std::string agent_id = ResolveAgentId(device_id);

    // 최초 연결이면 hello 를 보내 서버 세션 레지스트리에 등록합니다.
    // ack 는 흘려보내되, error 봉투면 로그로 알립니다.
    if (!hello_sent_)
    {
        if (!SendEnvelope(envelope::Hello(agent_id, device_type)))
        {
            return {};
        }
        hello_sent_ = true;

        std::string greeting;
        if (TryReceive(greeting, kResponseTimeout))
        {
            try
            {
                const Json reply = Json::parse(greeting);
                if (envelope::IsType(reply, envelope::kError))
                {
                    std::cerr << "[MGMT] hello rejected: " << envelope::ErrorText(reply) << '\n';
                }
            }
            catch (const std::exception&)
            {
                std::cerr << "[MGMT] hello reply was not JSON\n";
            }
        }
    }

    const Json request = envelope::PolicyRequest(agent_id, device_type, device_id);
    const std::string correlation_id = envelope::CorrelationId(request);

    if (!SendEnvelope(request))
    {
        return {};
    }

    // 같은 correlation_id 를 가진 응답이 올 때까지 기다립니다.
    // (다른 봉투 — 예: 서버 푸시 — 는 로그만 남기고 버립니다.)
    const auto deadline = std::chrono::steady_clock::now() + kResponseTimeout;
    while (std::chrono::steady_clock::now() < deadline)
    {
        // 정지 요청이 오면 남은 대기를 버리고 즉시 돌아갑니다.
        if (stop_token.stop_requested())
        {
            return {};
        }

        std::string raw;
        const auto remaining = std::chrono::duration_cast<std::chrono::milliseconds>(
            deadline - std::chrono::steady_clock::now());
        if (!TryReceive(raw, remaining))
        {
            break;
        }

        Json reply;
        try
        {
            reply = Json::parse(raw);
        }
        catch (const std::exception&)
        {
            std::cerr << "[MGMT] dropped non-JSON frame: " << raw << '\n';
            continue;
        }

        if (envelope::CorrelationId(reply) != correlation_id)
        {
            std::cout << "[MGMT] ignored out-of-band envelope: " << envelope::Type(reply) << '\n';
            continue;
        }

        if (envelope::IsType(reply, envelope::kError))
        {
            std::cerr << "[MGMT] policy request failed: " << envelope::ErrorText(reply) << '\n';
            return {};
        }

        if (envelope::IsType(reply, envelope::kPolicyResponse))
        {
            return envelope::Payload(reply);
        }

        std::cerr << "[MGMT] unexpected reply type: " << envelope::Type(reply) << '\n';
    }

    std::cerr << "[MGMT] policy response timeout (correlation_id=" << correlation_id << ")\n";
    return {};
}

bool ManagementService::ReportPolicyApplied(const DeviceType device_type,
                                            const std::string& device_id,
                                            const std::string& policy_id,
                                            bool applied)
{
    if (!connected_ && !connect())
    {
        return false;
    }

    Json payload;
    payload["device_id"] = device_id;
    payload["policy_id"] = policy_id;
    payload["applied"] = applied;

    // ack 는 서버가 응답하지 않는 일방향 봉투입니다.
    return SendEnvelope(envelope::Make(envelope::kAck,
                                       ResolveAgentId(device_id),
                                       envelope::DeviceTypeToString(device_type),
                                       envelope::NextCorrelationId(),
                                       std::move(payload)));
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
            if (src.empty() || dst.empty() || applied.empty())
            {
                std::cerr << "[POLICY] OVS ACL needs source/destination/applied_interface\n";
                return false;
            }

            const bool deny = (action == "deny" || action == "drop");

            // ⚠️ 차단을 허용보다 높은 우선순위로 둡니다.
            //    같은 5-튜플에 두 규칙이 걸렸을 때 차단이 이겨야 합니다.
            //    (허용이 이기면 등급 건너뛰기가 조용히 통과합니다)
            const int priority = deny ? 200 : 100;

            // openflow 는 대역을 CIDR 표기로 그대로 받습니다.
            // nw_src/nw_dst 는 흐름 매칭의 표준 필드입니다.
            return RunCommand("ovs-ofctl add-flow " + applied + " " +
                              "priority=" + std::to_string(priority) +
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

std::string ManagementService::QueryAristaCli(const std::string& command)
{
    // Arista vEOS 의 FastCli 는 표준입력으로 명령을 주면 배치 모드로 동작한다.
    //
    // 왜 pty 대화형 세션이 아니라 파이프인가
    //   대화형(pty) 경로는 프롬프트 타이밍에 의존해 `show ...` 출력을
    //   안정적으로 얻지 못했다(실측: 조회 4건 모두 빈 결과).
    //   반면 `printf 'enable\n<cmd>\n' | FastCli` 는 출력이 온전히 나온다(실측 확인).
    //   조회는 부작용이 없으므로 매번 새 프로세스로 실행해도 문제없다.
    //
    // 출력에는 명령 에코(`> show ...`)와 종료 시의
    // `% Internal error at line N` 잡음이 섞이므로 수집기가 정리한다.
    const std::string script = "enable\n" + command + "\n";
    const std::string pipeline =
        "printf '%s' '" + script + "' | timeout 20 FastCli 2>&1";
    return RunCommandOutput(pipeline);
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
        // 조회는 출력이 필요하므로 RunCommandOutput 을 씁니다.
        std::cout << RunCommandOutput("vtysh -c 'show ip route'");
        return true;
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
            return RunCommand(NftScript(script));
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

bool ManagementService::ApplyVmPolicy(const Json& policy)
{
    const std::string command = policy_json::AsString(policy, "command");

    // ------------------------------------------------------------------
    //  netplan 경로 (문서: VM_Policy_Design.md "정적 IP 주소 및 게이트웨이 설정")
    //
    //  ⚠️ 왜 interface 를 요구하지 않는가
    //    netplan 스키마는 인터페이스 이름을 network_config.ethernets 의
    //    "키" 로 갖습니다. 즉 interface 필드가 따로 오지 않습니다.
    //    예전 구현이 interface 를 먼저 요구해서, 서버가 정상 정책을 보내도
    //    "[POLICY] VM policy has no interface" 로 전부 실패했습니다.
    //    (실측: TOD-Cam/VDI-1 등 VM 4대 모두 적용 실패)
    // ------------------------------------------------------------------
    const std::string backend = policy_json::AsString(policy, "config_backend");
    if (backend == "netplan" || policy_json::Has(policy, "network_config"))
    {
        return ApplyNetplanPolicy(policy, command);
    }

    // ⚠️ 폴백 경로(on/off/get)도 자리표시자를 치환해야 합니다.
    //    VM 전략의 defaultRule() 은 인터페이스 이름을 알 수 없어
    //    `"interface": "__primary__"` 를 보냅니다(VmPolicyStrategy.PRIMARY_INTERFACE_TOKEN).
    //    netplan 경로만 ResolveInterfaceName 을 거치고 이 경로는 원문을 그대로
    //    `ip link set ... up` 에 넣어  `Cannot find device "__primary__"` 로
    //    실패했습니다. (실측: 기동 직후 정책 수신 시 1회 발생)
    //    ResolveInterfaceName 은 토큰이 아니면 원문을 그대로 돌려주므로
    //    일반 정책에는 영향이 없습니다.
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

bool ManagementService::ApplyNetplanPolicy(const Json& policy, const std::string& command)
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
        // DHCP 로 원복: 기존 파일을 지우고 백엔드 기본값으로 되돌립니다.
        // 자리표시자가 오면 실제 인터페이스로 치환합니다. (그대로 쓰면
        // `/etc/netplan/99-sonar-__primary__.yaml` 이라는 무의미한 파일이 남습니다)
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

    // ⚠️ netplan 이 없는 이미지에서는 ip 로 직접 적용합니다.
    //
    // GNS3 의 gns3/ubuntu 컨테이너에는 netplan 이 설치되어 있지 않습니다.
    // (실측: netplan=NO, /etc/netplan 없음)
    // 그런데 서버는 netplan 스키마로 주소를 내려줍니다. 여기서 포기하면
    // VM 정책이 영원히 실패하고, ack 도 실패로 남습니다.
    //
    // 주소/게이트웨이를 ip 명령으로 적용합니다. 이것은 "영속 설정" 이 아니라
    // "런타임 적용" 이라 재부팅하면 사라지지만, 랩에서는 그 편이 오히려
    // 안전합니다 — 잘못된 주소를 영속화하면 재부팅 후 장치에 접속할 수 없게
    // 됩니다. (프로버가 스스로를 고립시키는 사고)
    if (!HasCommand("netplan"))
    {
        std::cout << "[POLICY] netplan not installed; applying addresses via ip\n";
        return ApplyAddressesWithIp(policy, ethernets, command);
    }

    // 기본은 create 입니다.
    //
    // ⚠️ 안전장치 1: 남의 netplan 설정을 지우지 않습니다.
    //   랩의 VM 은 부팅 시 netplan 이 이미 IP 를 잡습니다. 99-sonar-* 만
    //   쓰면 기존 설정과 공존하지만, 기존 파일을 건드리면 네트워크가 끊겨
    //   프로버가 서버에 보고할 수 없게 됩니다(자기 자신을 고립시킴).
    //
    // ⚠️ 안전장치 2: 적용 실패를 반드시 드러냅니다.
    //   `netplan apply` 는 YAML 이 틀려도 조용히 실패할 수 있습니다.
    //   그래서 `netplan generate` 로 먼저 검증합니다.
    std::string yaml = "network:\n  version: 2\n  ethernets:\n";

    for (auto entry = ethernets->begin(); entry != ethernets->end(); ++entry)
    {
        // 자리표시자를 장치의 실제 인터페이스로 치환합니다.
        const std::string name = ResolveInterfaceName(entry.key());
        const Json& settings = entry.value();
        if (!settings.is_object() || name.empty())
        {
            continue;
        }

        yaml += "    " + name + ":\n";

        // dhcp4
        const std::string dhcp4 = policy_json::AsString(settings, "dhcp4");
        const bool dhcp_on = dhcp4.empty() || dhcp4 == "true";
        yaml += std::string("      dhcp4: ") + (dhcp_on ? "true" : "false") + "\n";

        // addresses (배열을 여러 줄로)
        if (policy_json::Has(settings, "addresses"))
        {
            yaml += "      addresses:\n";
            for (const std::string& address : policy_json::AsStringList(settings, "addresses"))
            {
                yaml += "        - " + address + "\n";
            }
        }

        // routes (to/via 쌍)
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

        // nameservers
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

    // YAML 을 셸에 안전하게 넘깁니다. (여러 줄 + 특수문자)
    const std::string target = "/etc/netplan/99-sonar-policy.yaml";
    const std::string script =
        "set -e; "
        "cat > " + target + " <<'SONAR_NETPLAN_EOF'\n" + yaml + "SONAR_NETPLAN_EOF\n"
        // 먼저 검증합니다. YAML 이 틀리면 여기서 멈추고 적용하지 않습니다.
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