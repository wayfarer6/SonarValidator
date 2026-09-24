#include "module/telemetry_module/command_collector.hpp"

#include <algorithm>
#include <cctype>
#include <iostream>
#include <map>
#include <sstream>
#include <vector>

#include "module/management_module/management_service.hpp"
#include "components/parser/cli_output_parser.hpp"

using Json = nlohmann::json;

namespace collector
{
namespace
{

// ---------------------------------------------------------------------------
//  제품군 분류
//
//  ProberConfig::GetProductName() 은 "Cisco 8000v" / "Arista" / "Ubuntu" 같은
//  사람이 읽는 문자열이라, 조회 명령을 고르려면 한 번 분류가 필요하다.
// ---------------------------------------------------------------------------
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

ProductKind ClassifyProduct(const std::string& product_name)
{
    if (product_name.find("Cisco") != std::string::npos)
    {
        return ProductKind::kCisco;
    }
    if (product_name.find("Arista") != std::string::npos)
    {
        return ProductKind::kArista;
    }
    // OpenVSwitch 는 스위치의 L2 설정(port 의 tag/trunks)을 vtysh/IP 와
    // 전혀 다른 CLI 로 내놓으므로 별도 분기로 처리한다.
    if (product_name.find("OpenVSwitch") != std::string::npos ||
        product_name.find("Open vSwitch") != std::string::npos)
    {
        return ProductKind::kOpenVSwitch;
    }
    // FRR 은 라우팅 데몬일 뿐이고 실제 호스트는 리눅스(Alpine 등)이므로
    // `ip ...` 명령으로 NIC/라우팅/이웃을 그대로 수집할 수 있다.
    // (제품 감지는 vtysh 존재를 먼저 보므로 Ubuntu 판정보다 앞에 둔다.)
    if (product_name.find("FRR") != std::string::npos)
    {
        return ProductKind::kFrr;
    }
    // nftables 방화벽도 호스트는 Alpine 리눅스다.
    // NIC/라우팅/이웃은 `ip ...` 로, 규칙은 `nft list ruleset` 으로 수집한다.
    if (product_name.find("nftables") != std::string::npos ||
        product_name.find("nft") != std::string::npos ||
        product_name.find("Firewall") != std::string::npos)
    {
        return ProductKind::kFirewall;
    }
    if (product_name.find("Ubuntu") != std::string::npos ||
        product_name.find("Linux") != std::string::npos)
    {
        return ProductKind::kLinux;
    }
    return ProductKind::kOther;
}

// ---------------------------------------------------------------------------
//  원문 라인에서 주소/MAC 을 보조 추출하기 위한 유틸
//
//  Cisco/Arista 의 `show ip interface brief` 는 벤더·버전마다 컬럼이 달라
//  FrrRouter 문법이 만든 구조 필드만으로는 주소를 100% 신뢰할 수 없다.
//  그래서 구조 필드(이름/상태/IP)는 파서 결과에서, 주소/MAC/MTU 는 원문에서
//  보조로 뽑아 합친다. (parser 가 실패해도 여기서 얻은 값은 살아남는다.)
// ---------------------------------------------------------------------------

// 문자열을 소문자로.
std::string ToLower(std::string text)
{
    std::transform(text.begin(), text.end(), text.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return text;
}

// 원문 라인을 토큰으로 쪼갠다(공백/탭 기준, `,` 는 제거).
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

// `10.0.8.1/24` 를 (주소, prefix) 로 나눈다. CIDR 이 없으면 false.
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

// IPv4/IPv6 주소처럼 보이는가? (점 4옥텟 또는 콜론 포함)
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

// MAC 주소처럼 보이는가? (aa:bb:cc:dd:ee:ff)
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

// 인터페이스 이름 자리에 올 수 있는 토큰인가?
// (`global`, `forever` 같은 속성 토큰과 주소/MAC 을 걸러낸다.)
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

// 원문에서 특정 인터페이스 이름으로 시작하는 라인을 찾아준다.
// (파서가 만든 엔트리 순서는 원문 라인 순서와 같다.)
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

// `show ip interface brief` 원문 라인에서 주소/MAC/MTU 만 뽑는다.
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

        // CIDR 주소
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

// `ip -br addr show` 결과(brief[])를 nic_info/nic_address 저장 모양(interfaces[])으로 바꾼다.
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

// `show ip interface brief` 파서 결과 + 원문을 합쳐 nic_info/nic_address 모양을 만든다.
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

    // 원문 라인을 미리 쪼개 둔다(엔트리 순서 = 라인 순서).
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

        // 상태는 파서 결과를 우선하고, 없으면 원문에서 보조한다.
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

        // 주소: 원문에서 못 찾으면 구조 필드(ip_address)로 대체한다.
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

// ---------------------------------------------------------------------------
//  Arista FastCli 배치 출력 정리
//
//  `printf 'enable\n<cmd>\n' | FastCli` 출력에는 다음 잡음이 섞인다.
//    - 명령 에코:        `> show vlan brief`
//    - 안내문:           `Pagination disabled.` / `Arista Networks EOS shell`
//    - 종료 시 오류:     `% Internal error at line 3`
//    - 프롬프트 조각:    `ARISTA#` / `>`
//  파서가 이들을 헤더/데이터로 오인하지 않도록 제거한다.
// ---------------------------------------------------------------------------
std::string CleanAristaOutput(const std::string& raw)
{
    std::istringstream stream(raw);
    std::string line;
    std::string cleaned;

    while (std::getline(stream, line))
    {
        // 앞뒤 공백/캐리지리턴 제거
        while (!line.empty() && (line.back() == '\r' || line.back() == ' '))
        {
            line.pop_back();
        }

        // 빈 줄은 유지한다(파서가 빈 줄을 허용).
        if (line.empty())
        {
            cleaned += '\n';
            continue;
        }

        const std::size_t first = line.find_first_not_of(" \t");
        const std::string trimmed = (first == std::string::npos) ? line : line.substr(first);

        // `> ` 로 시작하는 명령 에코
        if (trimmed.size() >= 2 && trimmed[0] == '>' && trimmed[1] == ' ')
        {
            continue;
        }
        // 프롬프트만 있는 줄
        if (trimmed == ">" || trimmed.rfind("ARISTA#", 0) == 0 ||
            trimmed.rfind("ARISTA>", 0) == 0)
        {
            continue;
        }
        // 종료 시 발생하는 내부 오류 메시지
        if (trimmed.rfind("%", 0) == 0)
        {
            continue;
        }
        // 셸 진입 안내문
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

// JSON 객체의 배열 키에 실제 항목이 있는지.
bool HasItems(const Json& object, const char* key)
{
    if (!object.is_object())
    {
        return false;
    }
    const auto iterator = object.find(key);
    return iterator != object.end() && iterator->is_array() && !iterator->empty();
}

// JSON 객체의 배열 멤버를 찾습니다. 없거나 배열이 아니면 nullptr.
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

// ---------------------------------------------------------------------------
//  Open vSwitch 변환
//
//  `ovs-vsctl show` 는 브리지 → 포트 → 인터페이스 계층을 준다.
//  포트의 `tag`(액세스 VLAN)와 `trunks`(트렁크 허용 VLAN)가 L2 설정의 전부다.
//  이를 벤더 중립적인 vlan_status / trunk_status 모양으로 바꿔 다른 장비와
//  같은 테이블에 저장되도록 한다.
// ---------------------------------------------------------------------------

// 포트의 tag 값을 정수로 읽습니다. 없으면 -1(액세스 VLAN 아님).
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
    // `tag: []` 처럼 배열로 올 수도 있다.
    if (iterator->is_array() && !iterator->empty() && iterator->front().is_number_integer())
    {
        return iterator->front().get<int>();
    }
    return -1;
}

// 포트의 trunks 배열을 정수 목록으로 읽습니다.
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

// `ovs-vsctl show` 결과를 vlan_status(vlans) 로 변환합니다.
//  액세스 포트의 tag 를 VLAN 별로 묶고, 그 VLAN 을 쓰는 포트 목록을 남긴다.
Json OvsTopologyToVlanStatus(const Json& topology)
{
    Json vlans = Json::array();
    const Json* bridges = FindArrayMember(topology, "bridges");
    if (bridges == nullptr)
    {
        return Json::object();
    }

    // vlan_id -> 포트 이름 목록
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

// `ovs-vsctl show` 결과를 trunk_status(ports) 로 변환합니다.
//  포트의 tag 를 access_vlan, trunks 를 trunk_vlans 로 옮긴다.
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

            // 액세스 VLAN 도 트렁크도 없는 포트(예: br0 internal)는 제외한다.
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

// `ovs-vsctl list port` 결과를 vlan_status(vlans) 로 변환합니다.
//  포트 레코드의 tag 를 VLAN 별로 모아 `show` 와 같은 모양으로 만든다.
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

// `ovs-vsctl list port` 결과를 trunk_status(ports) 로 변환합니다.
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

CollectedState BuildStateFromOutputs(DeviceType device_type,
                                     const std::string& product_name,
                                     const std::map<std::string, std::string>& outputs)
{
    CollectedState state;
    const cli_parser::Vendor vendor = cli_parser::VendorFromProductName(product_name);
    const ProductKind product = ClassifyProduct(product_name);

    auto output = [&outputs](const std::string& command) -> std::string {
        const auto iterator = outputs.find(command);
        return iterator == outputs.end() ? std::string{} : iterator->second;
    };

    auto empty = [](const std::string& value) { return value.empty(); };

    Json snapshot = Json::object();

    // 리눅스 계열(VM/방화벽, Ubuntu)은 `ip ...` 명령을 쓴다.
    // FRR 라우터와 nftables 방화벽도 호스트 OS 가 리눅스(Alpine 등)이므로 같은 경로를 쓴다.
    // OpenVSwitch 스위치도 컨테이너 호스트가 리눅스라 `ip a` 로 포트 정보를 얻는다.
    const bool linux_style = product == ProductKind::kLinux ||
                             product == ProductKind::kFrr ||
                             product == ProductKind::kFirewall ||
                             product == ProductKind::kOpenVSwitch ||
                             device_type == DeviceType::kVirtualMachine;

    // -----------------------------------------------------------------------
    // 1) NIC / 주소 — ip a, ip -br addr show, show ip interface brief
    // -----------------------------------------------------------------------
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

        // `ip -br addr show` 는 보조 수단이다. `ip a` 가 실패했을 때만 쓴다.
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
        // 라우터/스위치는 `show ip interface brief` 결과를 nic_info 모양으로 변환해 쓴다.
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

    // -----------------------------------------------------------------------
    // 2) 라우팅 테이블 — ip route show / show ip route
    // -----------------------------------------------------------------------
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

    // -----------------------------------------------------------------------
    // 3) VLAN — ovs-vsctl show (OpenVSwitch) 또는 show vlan brief (Arista)
    // -----------------------------------------------------------------------
    if (product == ProductKind::kOpenVSwitch)
    {
        // 스위치는 L2 전용이라 `show vlan brief` 가 없다.
        // 포트의 tag/trunks 가 곧 VLAN 구성이므로 ovs-vsctl 출력에서 만든다.
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

        // `list port` 는 `show` 와 같은 tag/trunks 를 레코드 형태로 준다.
        // `show` 가 브리지 이름을 못 준 경우의 대안으로만 쓴다.
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

    // -----------------------------------------------------------------------
    // 4) 트렁크/스위치포트 — show interfaces switchport (Arista/Cisco)
    //    (OpenVSwitch 는 위 3) 에서 이미 채웠다.)
    // -----------------------------------------------------------------------
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

    // -----------------------------------------------------------------------
    // 5) 방화벽 규칙 — nft list ruleset (nftables)
    //
    //  nftables 방화벽은 라우터/스위치의 VLAN·트렁크 대신 필터 규칙이
    //  수집 대상이다. 규칙은 스냅샷에만 실어 보내고(서버 계약 유지)
    //  DB 규칙 테이블 저장은 기존 firewall_rule_table 경로가 담당한다.
    // -----------------------------------------------------------------------
    if (product == ProductKind::kFirewall)
    {
        const std::string raw = output("nft list ruleset");
        if (!empty(raw))
        {
            state.rules = cli_parser::ParseFirewallRules(raw);
            // 파서는 tables 배열을 돌려준다(table → chains → rules 구조).
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

    // -----------------------------------------------------------------------
    // 6) ARP / 이웃 — ip neigh show, show arp, show ip arp
    // -----------------------------------------------------------------------
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

    // 벤더명/명령 개수는 디버깅에 유용하다(서버 payload 계약에 영향 없음).
    snapshot["vendor"] = cli_parser::VendorName(vendor);
    snapshot["product"] = product_name;

    state.snapshot = std::move(snapshot);
    return state;
}

CollectedState CollectState(const ProberConfig& config, ManagementService& management_service)
{
    const std::string product = config.GetProductName();
    const ProductKind kind = ClassifyProduct(product);
    std::map<std::string, std::string> outputs;

    // 명령 하나가 예외/실패를 내도 나머지 수집은 계속한다.
    auto run_shell = [&outputs](const std::string& command, ManagementService& service) {
        try
        {
            outputs[command] = service.RunCommandOutput(command);
        }
        catch (const std::exception& exception)
        {
            std::cerr << "[COLLECT] command failed: " << command << " (" << exception.what() << ")\n";
        }
    };

    auto run_ios = [&outputs](const std::string& command, ManagementService& service) {
        try
        {
            outputs[command] = service.ExecuteIosCli({command});
        }
        catch (const std::exception& exception)
        {
            std::cerr << "[COLLECT] ios command failed: " << command << " (" << exception.what()
                      << ")\n";
        }
    };

    auto run_arista = [&outputs](const std::string& command, ManagementService& service) {
        try
        {
            // FastCli 배치 출력에는 명령 에코와 종료 시 오류 잡음이 섞인다.
            // 파서가 헤더로 오인하지 않도록 여기서 걸러낸다.
            outputs[command] = CleanAristaOutput(service.QueryAristaCli(command));
        }
        catch (const std::exception& exception)
        {
            std::cerr << "[COLLECT] arista command failed: " << command << " (" << exception.what()
                      << ")\n";
        }
    };

    switch (kind)
    {
    case ProductKind::kLinux:
        // docs/Agent_Command.md "Linux VM (NIC 설정)" + 라우팅/이웃 조회
        run_shell("ip a", management_service);
        run_shell("ip -br addr show", management_service);
        run_shell("ip route show", management_service);
        run_shell("ip neigh show", management_service);
        break;

    case ProductKind::kFrr:
        // FRR 라우터의 호스트 OS 는 리눅스(Alpine 등)이므로 `ip ...` 로 수집한다.
        // 수집기는 조회 전용이므로 vtysh 설정 변경은 하지 않는다.
        run_shell("ip a", management_service);
        run_shell("ip -br addr show", management_service);
        run_shell("ip route show", management_service);
        run_shell("ip neigh show", management_service);
        break;

    case ProductKind::kFirewall:
        // nftables 방화벽도 호스트는 Alpine 리눅스다.
        // NIC/라우팅/이웃/ARP 는 `ip ...` 로, 필터 규칙은 nft 조회로 수집한다.
        // (조회 전용 — 규칙을 추가/삭제하지 않는다.)
        run_shell("ip a", management_service);
        run_shell("ip -br addr show", management_service);
        run_shell("ip route show", management_service);
        run_shell("ip neigh show", management_service);
        run_shell("nft list ruleset", management_service);
        break;

    case ProductKind::kCisco:
        // docs/Agent_Command.md "Cisco" — guestshell 의 dohost 로 IOS CLI 실행
        run_ios("show ip interface brief", management_service);
        run_ios("show ip route", management_service);
        run_ios("show ip arp", management_service);
        break;

    case ProductKind::kArista:
        // docs/Agent_Command.md "Arista" — FastCli 영속 세션
        run_arista("show vlan brief", management_service);
        run_arista("show ip interface brief", management_service);
        run_arista("show interfaces switchport", management_service);
        run_arista("show arp", management_service);
        break;

    case ProductKind::kOpenVSwitch:
        // docs/Agent_Command.md "OpenvSwitch" — L2 토폴로지는 ovs-vsctl 로만 보인다.
        // `show` 는 브리지/포트/인터페이스 계층을, `list port` 는 포트 속성
        // 레코드(tag/trunks/vlan_mode)를 준다. 둘 다 조회 전용이다.
        // 스위치는 L2 전용이지만 `ip a` 로 포트별 MAC/상태는 수집할 수 있다.
        run_shell("ovs-vsctl show", management_service);
        run_shell("ovs-vsctl list port", management_service);
        run_shell("ip a", management_service);
        run_shell("ip -br addr show", management_service);
        break;

    case ProductKind::kOther:
        std::cerr << "[COLLECT] no live query commands for product '" << product
                  << "' — 수집 생략\n";
        break;
    }

    CollectedState state = BuildStateFromOutputs(config.GetDeviceType(), product, outputs);

    std::size_t empty_outputs = 0;
    for (const auto& [command, value] : outputs)
    {
        if (value.empty())
        {
            ++empty_outputs;
            std::cerr << "[COLLECT] empty output: " << command << '\n';
        }
    }

    if (!state.any_success)
    {
        std::cerr << "[COLLECT] no usable state collected (product=" << product
                  << ", commands=" << outputs.size() << ", empty=" << empty_outputs << ")\n";
    }

    return state;
}

} // namespace collector
