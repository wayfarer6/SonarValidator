#include "components/policy/quarantine_handler.hpp"

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <iostream>
#include <sstream>
#include <utility>

#include "components/backend_communication/envelope.hpp"
#include "components/policy/policy_json.hpp"
#include "module/configuration_module/prober_config.hpp"
#include "module/management_module/management_service.hpp"

namespace quarantine
{
namespace
{

// 주소 하나를 담습니다. (인터페이스 이름 + CIDR)
struct InterfaceAddress
{
    std::string name;
    std::string cidr;      // 10.10.0.1/24
    std::string address;   // 10.10.0.1
    int prefix_len{0};
};

// `10.10.0.1/24` → (10.10.0.1, 24)
std::pair<std::string, int> SplitCidr(const std::string& token)
{
    const std::size_t slash = token.find('/');
    if (slash == std::string::npos)
    {
        return {token, -1};
    }
    int prefix = -1;
    try
    {
        prefix = std::stoi(token.substr(slash + 1));
    }
    catch (const std::exception&)
    {
        prefix = -1;
    }
    return {token.substr(0, slash), prefix};
}

// IPv4 문자열을 32비트 정수로. 실패하면 false.
bool ToIpv4(const std::string& text, std::uint32_t& out)
{
    std::uint32_t value = 0;
    int octets = 0;
    std::size_t start = 0;

    while (start <= text.size() && octets < 4)
    {
        const std::size_t dot = text.find('.', start);
        const std::string part = text.substr(
            start, dot == std::string::npos ? std::string::npos : dot - start);

        if (part.empty() || part.size() > 3 ||
            !std::all_of(part.begin(), part.end(),
                         [](unsigned char ch) { return std::isdigit(ch) != 0; }))
        {
            return false;
        }

        const int octet = std::stoi(part);
        if (octet < 0 || octet > 255)
        {
            return false;
        }

        value = (value << 8) | static_cast<std::uint32_t>(octet);
        ++octets;

        if (dot == std::string::npos)
        {
            break;
        }
        start = dot + 1;
    }

    if (octets != 4)
    {
        return false;
    }

    out = value;
    return true;
}

// 두 IPv4 가 같은 /prefix 대역에 있는가?
bool SameSubnet(const std::string& a, const std::string& b, int prefix_len)
{
    if (prefix_len < 0 || prefix_len > 32)
    {
        return false;
    }

    std::uint32_t left = 0;
    std::uint32_t right = 0;
    if (!ToIpv4(a, left) || !ToIpv4(b, right))
    {
        return false;
    }

    if (prefix_len == 0)
    {
        return true;
    }

    const std::uint32_t mask = (prefix_len == 32)
                                   ? 0xFFFFFFFFu
                                   : ~((1u << (32 - prefix_len)) - 1u);
    return (left & mask) == (right & mask);
}

// 루프백 주소 문자열인가?
bool IsLoopback(const std::string& address)
{
    return address == "127.0.0.1" || address == "localhost" || address.rfind("127.", 0) == 0;
}

// `ip -o -4 addr show` 출력에서 주소를 가진 인터페이스를 뽑습니다.
//
// 형식:  2: eth0    inet 10.99.143.2/24 scope global eth0\   valid_lft ...
// 첫 토큰은 "2:", 둘째는 이름, "inet" 다음 토큰이 CIDR 입니다.
std::vector<InterfaceAddress> DiscoverAddresses(ManagementService& mgmt)
{
    std::vector<InterfaceAddress> found;

    const std::string raw = mgmt.RunCommandOutput("ip -o -4 addr show 2>/dev/null");
    if (raw.empty())
    {
        return found;
    }

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

        // "eth0:" 처럼 콜론이 붙어 나오면 떼어냅니다.
        if (!name.empty() && name.back() == ':')
        {
            name.pop_back();
        }
        // "eth1.131@eth1" → "eth1.131"
        const std::size_t at = name.find('@');
        if (at != std::string::npos)
        {
            name = name.substr(0, at);
        }

        std::string token;
        while (tokens >> token)
        {
            if (token != "inet")
            {
                continue;
            }
            std::string cidr;
            if (!(tokens >> cidr))
            {
                break;
            }
            // "10.0.0.1/24" 에 후행 백슬래시가 붙는 경우가 있습니다.
            if (!cidr.empty() && cidr.back() == '\\')
            {
                cidr.pop_back();
            }

            auto [address, prefix] = SplitCidr(cidr);
            InterfaceAddress entry;
            entry.name = name;
            entry.cidr = cidr;
            entry.address = address;
            entry.prefix_len = prefix;
            found.push_back(std::move(entry));
            break;
        }
    }

    return found;
}

// `ip -o link show` 출력에서 모든 인터페이스 이름을 뽑습니다. (주소 유무 무관)
std::vector<std::string> DiscoverAllInterfaces(ManagementService& mgmt)
{
    std::vector<std::string> names;

    const std::string raw = mgmt.RunCommandOutput("ip -o link show 2>/dev/null");
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
        names.push_back(name);
    }

    return names;
}

// 이 인터페이스가 서버로 나가는 길인가?
//
// 두 가지를 봅니다.
//   1) 주소가 관리망 대역(kManagementPrefix) 안에 있는가
//   2) 주소가 서버와 같은 대역인가 (대역이 문서와 달라도 놓치지 않게)
//
// ⚠️ 여기서 놓치면 해제 경로가 사라집니다. 그래서 판정을 넉넉하게 합니다.
bool IsManagementPath(const InterfaceAddress& entry, const ProberConfig& config)
{
    const std::string server = config.GetServerIpv4();
    if (server.empty() || IsLoopback(server))
    {
        return false;
    }

    // 관리망 대역에 속하면 관리 경로입니다. (/24 고정)
    const std::string mgmt_prefix(kManagementPrefix);
    const std::size_t slash = mgmt_prefix.find('/');
    if (slash != std::string::npos &&
        SameSubnet(entry.address, mgmt_prefix.substr(0, slash),
                   std::stoi(mgmt_prefix.substr(slash + 1))))
    {
        return true;
    }

    // 서버와 같은 대역이면 그 인터페이스로 서버에 도달합니다.
    return entry.prefix_len >= 0 && SameSubnet(entry.address, server, entry.prefix_len);
}

// ack payload 를 만듭니다.
nlohmann::json BuildAck(const Outcome& outcome)
{
    nlohmann::json payload;
    payload["action"] = outcome.action;
    payload["ok"] = outcome.ok;
    payload["affected"] = outcome.affected;
    payload["preserved"] = outcome.preserved;
    if (!outcome.detail.empty())
    {
        payload["detail"] = outcome.detail;
    }
    return payload;
}

} // namespace

bool IsQuarantineCommand(const nlohmann::json& message)
{
    if (!envelope::IsType(message, envelope::kCommand))
    {
        return false;
    }

    const nlohmann::json& payload = envelope::Payload(message);

    // payload.action 을 우선 보고, 없으면 최상위 action 도 받습니다.
    // (서버는 payload 를 쓰지만, 손으로 만든 테스트 봉투를 배려합니다.)
    std::string action = policy_json::AsString(payload, "action");
    if (action.empty())
    {
        action = policy_json::AsString(message, "action");
    }
    if (action.empty())
    {
        return false;
    }

    return action == kIsolate || action == kRelease;
}

Outcome Isolate(const ProberConfig& config, ManagementService& mgmt)
{
    Outcome outcome;
    outcome.action = kIsolate;

    const std::vector<InterfaceAddress> addresses = DiscoverAddresses(mgmt);
    if (addresses.empty())
    {
        outcome.ok = false;
        outcome.detail = "인터페이스 주소를 읽지 못했습니다 (ip 명령 실패 또는 권한 부족)";
        std::cerr << "[QUARANTINE] " << outcome.detail << '\n';
        return outcome;
    }

    const bool local_server = config.GetServerIpv4().empty() ||
                              IsLoopback(config.GetServerIpv4());

    for (const InterfaceAddress& entry : addresses)
    {
        if (entry.name == "lo")
        {
            // 루프백은 내려도 의미가 없고, 로컬 서버(localhost) 통신을 끊습니다.
            continue;
        }

        if (IsManagementPath(entry, config))
        {
            outcome.preserved.push_back(entry.name + " (" + entry.cidr + ")");
            std::cout << "[QUARANTINE] 관리 경로 유지: " << entry.name
                      << " " << entry.cidr << '\n';
            continue;
        }

        if (mgmt.RunCommand("ip link set " + entry.name + " down"))
        {
            outcome.affected.push_back(entry.name + " (" + entry.cidr + ")");
            std::cout << "[QUARANTINE] 차단: " << entry.name << " " << entry.cidr << '\n';
        }
        else
        {
            std::cerr << "[QUARANTINE] 인터페이스를 내리지 못했습니다: " << entry.name << '\n';
        }
    }

    // ⚠️ 안전장치: 원격 서버인데 관리 경로를 하나도 못 찾았다면 전부 내린 상태입니다.
    //    이 경우 해제 명령이 도달할 길이 없으므로 격리를 성공으로 보고하지 않습니다.
    if (!local_server && outcome.preserved.empty() && !outcome.affected.empty())
    {
        outcome.ok = false;
        outcome.detail = "관리 경로(서버로 나가는 인터페이스)를 찾지 못해 격리를 중단했습니다. "
                         "해제 명령이 도달할 수 없기 때문입니다.";
        std::cerr << "[QUARANTINE] " << outcome.detail << '\n';
        return outcome;
    }

    if (outcome.affected.empty())
    {
        outcome.ok = true;
        outcome.detail = "차단할 인터페이스가 없습니다 (이미 격리되었거나 관리 경로만 존재)";
        std::cout << "[QUARANTINE] " << outcome.detail << '\n';
        return outcome;
    }

    outcome.ok = true;
    outcome.detail = std::to_string(outcome.affected.size()) + "개 인터페이스를 내렸습니다";
    return outcome;
}

Outcome Release(const ProberConfig& config, ManagementService& mgmt)
{
    (void)config;

    Outcome outcome;
    outcome.action = kRelease;

    const std::vector<std::string> names = DiscoverAllInterfaces(mgmt);
    if (names.empty())
    {
        outcome.ok = false;
        outcome.detail = "인터페이스 목록을 읽지 못했습니다 (ip 명령 실패 또는 권한 부족)";
        std::cerr << "[QUARANTINE] " << outcome.detail << '\n';
        return outcome;
    }

    // 격리 때 내린 목록을 기억하지 않고 전부 올립니다.
    // 프로세스가 그 사이 재시작되었을 수 있고, up 은 멱등이라 안전합니다.
    for (const std::string& name : names)
    {
        if (mgmt.RunCommand("ip link set " + name + " up"))
        {
            outcome.affected.push_back(name);
        }
        else
        {
            std::cerr << "[QUARANTINE] 인터페이스를 올리지 못했습니다: " << name << '\n';
        }
    }

    outcome.ok = !outcome.affected.empty();
    outcome.detail = outcome.affected.empty()
                         ? "올릴 수 있는 인터페이스가 없습니다"
                         : std::to_string(outcome.affected.size()) + "개 인터페이스를 올렸습니다";
    std::cout << "[QUARANTINE] 해제: " << outcome.detail << '\n';
    return outcome;
}

bool HandleCommand(const ProberConfig& config,
                   ManagementService& mgmt,
                   const nlohmann::json& message)
{
    if (!IsQuarantineCommand(message))
    {
        return false;
    }

    const nlohmann::json& payload = envelope::Payload(message);
    std::string action = policy_json::AsString(payload, "action");
    if (action.empty())
    {
        action = policy_json::AsString(message, "action");
    }

    std::cout << "[QUARANTINE] 명령 수신: action=" << action
              << " reason=" << policy_json::AsString(payload, "reason") << '\n';

    const Outcome outcome = (action == kIsolate) ? Isolate(config, mgmt)
                                                 : Release(config, mgmt);

    // 결과를 ack 로 보고합니다. 서버가 "명령은 갔는데 적용은 실패" 를 구분할 수
    // 있어야 운영자가 다음 판단(재시도/콘솔 접속)을 할 수 있습니다.
    std::string correlation = envelope::CorrelationId(message);
    if (correlation.empty())
    {
        correlation = envelope::NextCorrelationId();
    }

    const std::string agent_id =
        config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();

    const bool reported = mgmt.SendEnvelope(
        envelope::Make(envelope::kAck,
                       agent_id,
                       envelope::DeviceTypeToString(config.GetDeviceType()),
                       correlation,
                       BuildAck(outcome)));

    if (!reported)
    {
        std::cerr << "[QUARANTINE] ack 전송 실패 (연결 끊김?)\n";
    }

    return true;
}

} // namespace quarantine