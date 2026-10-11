#include "components/policy/quarantine_handler.hpp"

#include <algorithm>
#include <arpa/inet.h>
#include <map>
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
// 세 가지를 봅니다.
//   1) 주소가 관리망 대역(설정) 안에 있는가
//   2) 주소가 서버와 같은 대역인가 (대역이 문서와 달라도 놓치지 않게)
//
// ⚠️ 여기서 놓치면 해제 경로가 사라집니다. 그래서 판정을 넉넉하게 합니다.
//
// ⚠️ 관리 대역은 더 이상 상수가 아니라 설정입니다. 랩/프로젝트마다 다를 수
//    있고 서버가 프로젝트별로 지정할 수 있기 때문입니다. 설정이 비어 있으면
//    안전한 기본값(kDefaultManagementPrefix)으로 폴백합니다.
//    대역은 쉼표로 여러 개를 지정할 수 있습니다(관리망 + 백업망 등).
bool IsManagementPath(const InterfaceAddress& entry, const ProberConfig& config)
{
    const std::string server = config.GetServerIpv4();
    if (server.empty() || IsLoopback(server))
    {
        return false;
    }

    // 관리 대역 목록을 순회합니다. 하나라도 포함되면 관리 경로입니다.
    const std::string configured = config.GetManagementPrefixes();
    const std::string prefixes =
        configured.empty() ? std::string(kDefaultManagementPrefix) : configured;

    std::size_t start = 0;
    while (start <= prefixes.size())
    {
        const std::size_t comma = prefixes.find(',', start);
        std::string token = prefixes.substr(
            start, comma == std::string::npos ? std::string::npos : comma - start);

        // 앞뒤 공백 제거 — "a, b" 처럼 써도 동작하게 합니다.
        while (!token.empty() && (token.front() == ' ' || token.front() == '\t'))
        {
            token.erase(token.begin());
        }
        while (!token.empty() && (token.back() == ' ' || token.back() == '\t'))
        {
            token.pop_back();
        }

        if (!token.empty())
        {
            const std::size_t slash = token.find('/');
            if (slash != std::string::npos)
            {
                try
                {
                    const int prefix_len = std::stoi(token.substr(slash + 1));
                    if (SameSubnet(entry.address, token.substr(0, slash), prefix_len))
                    {
                        return true;
                    }
                }
                catch (const std::exception&)
                {
                    // 형식 오류는 무시하고 다음 대역을 봅니다.
                }
            }
        }

        if (comma == std::string::npos)
        {
            break;
        }
        start = comma + 1;
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

    // ⚠️ "내릴 대상이 없었다" 와 "내리려 했는데 전부 실패했다" 를 반드시 구분해야 합니다.
    //    실패까지 ok=true 로 보고하면 서버는 **장치가 살아 있는데도 격리됐다고 믿습니다.**
    //    그것이 이 기능에서 가장 위험한 상태입니다(문서 API_Service_Contracts 참고).
    //    (실측: 프로버를 비-root 로 실행하면 `ip link set ... down` 이
    //     `RTNETLINK answers: Operation not permitted` 로 전부 실패하는데도
    //     서버 로그에 `quarantine ack ... affected=null` 로 INFO(성공)이 남았습니다)
    int failed = 0;

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
            ++failed;
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

    // 일부라도 내렸으면 격리는 성립합니다. 실패분은 detail 에 남겨 서버가 알 수 있게 합니다.
    if (!outcome.affected.empty())
    {
        outcome.ok = true;
        outcome.detail = std::to_string(outcome.affected.size()) + "개 인터페이스를 내렸습니다";
        if (failed > 0)
        {
            outcome.detail += " (" + std::to_string(failed) + "개 실패)";
        }
        return outcome;
    }

    // 하나도 내리지 못했습니다. 대상이 없었던 것인지, 실패한 것인지로 나눕니다.
    if (failed > 0)
    {
        outcome.ok = false;
        outcome.detail = std::to_string(failed) +
                         "개 인터페이스를 내리지 못했습니다 (권한 부족 또는 장치 거부). "
                         "이 장치를 격리된 것으로 보면 안 됩니다.";
        std::cerr << "[QUARANTINE] " << outcome.detail << '\n';
        return outcome;
    }

    outcome.ok = true;
    outcome.detail = "차단할 인터페이스가 없습니다 (이미 격리되었거나 관리 경로만 존재)";
    std::cout << "[QUARANTINE] " << outcome.detail << '\n';
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

    // ⚠️ scope == "connection" 이면 인터페이스를 절대 건드리지 않습니다.
    //
    // DB Design v1.5 에서 격리는 두 방식으로 나뉩니다.
    //   - 노드 격리   : Agent 가 관리 경로를 뺀 인터페이스를 내림
    //   - 연결 단위   : 방화벽의 특정 서브넷만 차단 — **서버가 규칙으로** 처리
    //
    // 방화벽은 트렁크 하나로 여러 VLAN 을 동시에 들고 있어, 여기서 인터페이스를
    // 내리면 격리하려던 대역만이 아니라 무관한 존 전체가 끊깁니다.
    // 서버(QuarantineService)는 SUBNET 방식에 명령을 보내지 않지만, 만약
    // 구버전 서버나 손으로 만든 봉투가 인터페이스 down 을 지시하면 여기서
    // **거부**해야 그 위험이 실현되지 않습니다.
    const std::string scope = policy_json::AsString(payload, "scope");
    if (scope == envelope::kScopeConnection)
    {
        Outcome skipped;
        skipped.action = action;
        skipped.ok = true;
        skipped.detail = "연결 단위 격리(scope=connection) — 인터페이스를 내리지 않습니다. "
                         "서버가 대상 서브넷만 차단하는 규칙을 내려보냅니다.";
        std::cout << "[QUARANTINE] " << skipped.detail << '\n';

        std::string correlation_skip = envelope::CorrelationId(message);
        if (correlation_skip.empty())
        {
            correlation_skip = envelope::NextCorrelationId();
        }
        const std::string agent_skip =
            config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();
        mgmt.SendEnvelope(
            envelope::Make(envelope::kAck,
                           agent_skip,
                           correlation_skip,
                           BuildAck(skipped)));
        return true;
    }

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
namespace quarantine {
namespace {
std::string Quote(const std::string& value) {
    std::string result="'";
    for(char c:value) result += c=='\'' ? "'\\''" : std::string(1,c);
    return result+"'";
}
std::string Trim(std::string value) {
    const auto first=value.find_first_not_of(" \t\r\n\"");
    if(first==std::string::npos)return {};
    return value.substr(first,value.find_last_not_of(" \t\r\n\"")-first+1);
}
bool Cidr(const std::string& value, std::uint32_t& address, int& prefix) {
    const auto slash=value.find('/');
    if(slash==std::string::npos)return false;
    const auto bits=value.substr(slash+1);
    if(bits.empty()||bits.size()>2||!std::all_of(bits.begin(),bits.end(),[](unsigned char c){return std::isdigit(c);}))return false;
    prefix=std::stoi(bits);
    struct in_addr parsed{};
    if(prefix>32||inet_pton(AF_INET,value.substr(0,slash).c_str(),&parsed)!=1)return false;
    address=ntohl(parsed.s_addr);return true;
}
bool Overlap(const std::string& a,const std::string& b) {
    std::uint32_t x,y;int p,q;
    if(!Cidr(a,x,p)||!Cidr(b,y,q))return true;
    const int n=std::min(p,q);const std::uint32_t mask=n==0?0:0xffffffffu<<(32-n);
    return (x&mask)==(y&mask);
}
std::vector<int> OvsNumbers(const nlohmann::json& value) {
    std::vector<int> result;
    if(value.is_number_integer())result.push_back(value.get<int>());
    else if(value.is_array()&&value.size()==2&&value[0]=="set")
        for(const auto& n:value[1])if(n.is_number_integer())result.push_back(n.get<int>());
    return result;
}
}

bool ApplySubnetTargets(const nlohmann::json& targets, const std::string& management,
                        const std::string& server, const SubnetExecutor& execute,
                        const SubnetReader& read, std::string& detail) {
    using Json=nlohmann::json;
    if(!targets.is_array()){detail="subnet_quarantine must be an array";return false;}
    std::vector<std::string> cidrs;std::vector<int> vlans;
    for(const auto& target:targets){
        if(!target.is_object()||!target.contains("cidr")||!target["cidr"].is_string()){detail="Missing CIDR";return false;}
        const std::string cidr=target["cidr"];std::uint32_t address;int prefix;
        if(!Cidr(cidr,address,prefix)){detail="Invalid IPv4 CIDR";return false;}
        std::istringstream prefixes(management.empty()?kDefaultManagementPrefix:management);std::string m;
        while(std::getline(prefixes,m,','))if(Overlap(cidr,Trim(m))){detail="Management prefix is protected";return false;}
        if(!server.empty()&&Overlap(cidr,server+"/32")){detail="Backend address is protected";return false;}
        cidrs.push_back(cidr);
        if(target.contains("vlan_id")){
            if(!target["vlan_id"].is_number_integer()){detail="Invalid VLAN ID";return false;}
            int v=target["vlan_id"].get<int>();if(v<1||v>4094){detail="Invalid VLAN ID";return false;}vlans.push_back(v);
        }
    }
    if(execute("command -v ovs-vsctl >/dev/null 2>&1")){
        if(vlans.size()!=cidrs.size()){detail="L2 switch isolation requires a VLAN ID for every target";return false;}
        const Json ports=Json::parse(read("ovs-vsctl --format=json --columns=name,tag,trunks,interfaces list Port"),nullptr,false);
        if(!ports.is_object()||!ports.contains("data")){detail="OVS port discovery failed";return false;}
        std::istringstream bridgeLines(read("ovs-vsctl list-br"));std::string bridge;
        std::map<std::string,std::string> bundles;
        const std::string cookie="0x534f4e415251";
        while(std::getline(bridgeLines,bridge))if(!Trim(bridge).empty())bundles[Trim(bridge)]="flow delete cookie="+cookie+"/0xffffffffffffffff\n";
        if(bundles.empty()){detail="No OVS bridges found";return false;}
        for(int vlan:vlans){
            bool found=false;
            for(const auto& row:ports["data"]){
                if(!row.is_array()||row.size()!=4||!row[0].is_string())continue;
                const auto tags=OvsNumbers(row[1]);const auto trunks=OvsNumbers(row[2]);
                const bool access=std::find(tags.begin(),tags.end(),vlan)!=tags.end();
                const bool trunk=std::find(trunks.begin(),trunks.end(),vlan)!=trunks.end();
                if(!access&&!trunk)continue;
                const auto br=Trim(read("ovs-vsctl port-to-br "+Quote(row[0].get<std::string>())));
                if(!bundles.count(br)){detail="OVS bridge lookup failed";return false;}
                found=true;
                bundles[br]+="flow add cookie="+cookie+",table=0,priority=65500,dl_vlan="+std::to_string(vlan)+",actions=drop\n";
                if(access){
                    std::vector<Json> uuids;
                    if(row[3].is_array()&&row[3].size()==2&&row[3][0]=="set")for(const auto& u:row[3][1])uuids.push_back(u);
                    else uuids.push_back(row[3]);
                    for(const auto& uuid:uuids){
                        if(!uuid.is_array()||uuid.size()!=2||!uuid[1].is_string()){detail="Invalid OVS interface reference";return false;}
                        const std::string port=Trim(read("ovs-vsctl get Interface "+Quote(uuid[1].get<std::string>())+" ofport"));
                        if(port.empty()||!std::all_of(port.begin(),port.end(),[](unsigned char c){return std::isdigit(c);})||std::stoul(port)==0){detail="OVS ofport unavailable";return false;}
                        bundles[br]+="flow add cookie="+cookie+",table=0,priority=65500,in_port="+port+",actions=drop\n";
                    }
                }
            }
            if(!found){detail="VLAN not present on this switch";return false;}
        }
        for(const auto& [br,body]:bundles){
            if(!execute("printf %s "+Quote(body)+" | ovs-ofctl -O OpenFlow14 bundle "+Quote(br)+" -")){detail="OVS quarantine bundle failed";return false;}
        }
        detail="OVS VLAN quarantine reconciled";return true;
    }
    if(execute("command -v nft >/dev/null 2>&1")){
        if(cidrs.empty()){
            const bool ok=execute("if nft list table inet sonar_quarantine >/dev/null 2>&1; then nft delete table inet sonar_quarantine; fi");
            detail=ok?"nft quarantine released":"nft quarantine release failed";return ok;
        }
        std::string body="add table inet sonar_quarantine\n";
        for(const std::string chain:{"input","forward","output"}){
            body+="add chain inet sonar_quarantine "+chain+" { type filter hook "+chain+" priority -200; policy accept; }\nflush chain inet sonar_quarantine "+chain+"\n";
            for(const auto& cidr:cidrs)for(const std::string match:{"saddr","daddr"})
                body+="add rule inet sonar_quarantine "+chain+" ip "+match+" "+cidr+" counter drop\n";
        }
        const bool ok=execute("printf %s "+Quote(body)+" | nft -f -");detail=ok?"nft IPv4 subnet quarantine reconciled":"nft quarantine transaction failed";return ok;
    }
    if(execute("command -v iptables-restore >/dev/null 2>&1")){
        const std::string chain="SONAR_QUARANTINE";
        if(cidrs.empty()){
            const bool ok=execute("for hook in INPUT FORWARD OUTPUT; do while iptables -w -C \"$hook\" -j SONAR_QUARANTINE 2>/dev/null; do iptables -w -D \"$hook\" -j SONAR_QUARANTINE || exit 1; done; done; if iptables -w -S SONAR_QUARANTINE >/dev/null 2>&1; then iptables -w -F SONAR_QUARANTINE && iptables -w -X SONAR_QUARANTINE; fi");
            detail=ok?"iptables quarantine released":"iptables release failed";return ok;
        }
        std::string body="*filter\n:SONAR_QUARANTINE - [0:0]\n-F SONAR_QUARANTINE\n";
        for(const auto& cidr:cidrs)body+="-A "+chain+" -s "+cidr+" -j DROP\n-A "+chain+" -d "+cidr+" -j DROP\n";
        body+="COMMIT\n";
        // --noflush preserves every pre-existing filter chain and NAT table.
        bool ok=execute("printf %s "+Quote(body)+" | iptables-restore -w --noflush");
        if(ok)ok=execute("for hook in INPUT FORWARD OUTPUT; do iptables -w -C \"$hook\" -j SONAR_QUARANTINE 2>/dev/null || iptables -w -I \"$hook\" 1 -j SONAR_QUARANTINE || exit 1; done");
        detail=ok?"iptables IPv4 subnet quarantine reconciled":"iptables quarantine failed";return ok;
    }
    if(cidrs.empty()){detail="No subnet quarantine requested";return true;}
    detail="No supported subnet isolation backend";return false;
}

bool ReconcileSubnets(const ProberConfig& config,ManagementService& mgmt,const nlohmann::json& targets){
    std::string detail;
    const bool ok=ApplySubnetTargets(targets,config.GetManagementPrefixes(),config.GetServerIpv4(),
        [&](const std::string& cmd){return mgmt.RunCommand(cmd);},
        [&](const std::string& cmd){return mgmt.RunCommandOutput(cmd);},detail);
    const std::string agent=config.GetAgentId().empty()?config.GetAgentName():config.GetAgentId();
    mgmt.SendEnvelope(envelope::Make(envelope::kAck,agent,envelope::DeviceTypeToString(config.GetDeviceType()),
        envelope::NextCorrelationId(),{{"action","subnet-quarantine"},{"targets",targets},{"ok",ok},{"detail",detail}}));
    return ok;
}
} // namespace quarantine
