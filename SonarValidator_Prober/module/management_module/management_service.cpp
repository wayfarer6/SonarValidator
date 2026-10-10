#include "module/management_module/management_service.hpp"
#include "components/backend_communication/connect_with_timeout.hpp"
#include "components/backend_communication/timed_websocket_operation.hpp"
#include <iostream>
#include "components/policy/policy_json.hpp"
#include "components/policy/acl_commands.hpp"
#include "components/terminal/command_runner.hpp"
#include "components/terminal/ios_cli.hpp"
#include <nlohmann/json.hpp>
#include <utility>
#include <sstream>
#include <iostream>
#include <chrono>
#include <cctype>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <fstream>
#include <stop_token>
#include <string>
#include <vector>
#include <thread>
#include <sys/stat.h>

using Json = nlohmann::json;

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

// OVS 에 넣는 우리 흐름에만 찍는 표식(cookie)입니다.
//
// 왜 필요한가
//   OVS 브리지에는 운영자가 직접 넣은 흐름(QoS·미러·계측)이 함께 있습니다.
//   우리 규칙을 갱신할 때 브리지의 흐름을 전부 지우면 그것들이 함께 사라집니다.
//   OpenFlow 의 cookie 는 정확히 "누가 넣었는가" 를 구분하기 위한 필드입니다.
//
// 값을 16진수 문자열로 두는 이유: ovs-ofctl 은 `cookie=<값>/<마스크>` 형태의
// 문자열을 그대로 받으므로, 숫자로 바꿔 쓸 필요가 없습니다.
constexpr const char* kAclCookie = "0x534f4e41"; // "SONA"

// 우리가 넣는 차단 흐름의 우선순위입니다. OVS 기본값(32768)보다 낮게 두되,
// 일반 스위칭 흐름보다는 높여 "존 간 차단이 먼저 매칭" 되게 합니다.
constexpr int kAclPriority = 20000;

// ACL 한 줄의 주소 4종(네트워크 주소 + 반전 마스크)입니다.
//
// 서버가 보낸 acl_rules[] 를 벤더별 문법으로 옮길 때 공통으로 씁니다.
// ACL 한 줄의 주소 4종(네트워크 주소 + 반전 마스크)입니다.
//
// 서버가 보낸 acl_rules[] 를 벤더별 문법으로 옮길 때 공통으로 씁니다.
// 해석과 명령 생성은 components/policy/acl_commands.hpp 로 빠졌습니다 —
// 장치 명령을 만드는 코드이므로 "위험한 줄이 빠졌는지" 를 네트워크 없이
// 테스트할 수 있어야 하기 때문입니다.
using AclEntry = acl::Entry;

// 일괄 ACL(acl_apply) 노드에서 차단 목록을 읽습니다.
constexpr auto ReadAclEntries = &acl::ReadEntries;

// 일괄 ACL 을 적용할 ACL 이름을 읽습니다. (rule_target.acl_name)
constexpr auto ReadAclName = &acl::ReadName;

// 일괄 ACL 을 걸 대상(인터페이스/브리지)을 읽습니다.
constexpr auto ReadApplyTarget = &acl::ReadApplyTarget;

// guestshell 안에서 프로버가 쓰는 데이터 디렉터리입니다.
//
// systemd 유닛/컨테이너에서 SONAR_DATA_DIR 로 주입합니다. 값이 없으면
// 설치 스크립트가 만드는 기본 경로를 씁니다.
std::string SonarDataDir()
{
    const char* env_dir = std::getenv("SONAR_DATA_DIR");
    return (env_dir != nullptr && *env_dir != '\0') ? env_dir
                                                    : "/var/lib/sonar_validator_prober";
}

// IOS 에 적용할 설정 명령을 <b>파일로 남기는</b> 경로입니다.
//
// 왜 파일인가 (게스트셸의 근본 제약)
//   guestshell 안의 프로세스가 IOS CLI 를 쓰려면 `dohost` 를 써야 하는데,
//   `dohost` 는 <b>IOS 가 만든 세션(IOSP)</b> 이 있을 때만 동작합니다.
//   그 세션은 `guestshell run` 으로 띄운 프로세스에서만 잠깐 유효하므로,
//   systemd/ssh 로 돌아가는 프로버는 항상 `Unexpected Error` 를 받습니다
//   (실장비 실측).
//
//   그래서 프로버는 "지금 남아 있어야 하는 설정" 을 이 파일에 쓰고,
//   IOS 의 EEM applet 이 주기적으로 `guestshell run <적용 스크립트>` 를
//   실행해 그 파일을 반영합니다. (세션이 살아 있는 바로 그 순간)
//
// ⚠️ 스크립트 적용 경로는 sonar_ios_apply.sh 이고, 수집 스크립트
//    (sonar_ios_collect.sh) 가 매 주기 끝에 호출합니다 — EEM applet 을
//    새로 만들지 않아도 장치 설정을 건드리지 않고 배포할 수 있습니다.
std::string IosApplyDir()
{
    return SonarDataDir() + "/ios_apply";
}

// 문자열의 빠른 핑거프린트(djb2)를 만듭니다.
//
// 용도는 단 하나입니다 — "지난 주기에 넣은 것과 같은가" 를 판단해
// 무의미한 재기록/재적용을 막습니다. 암호학적 강도는 필요하지 않습니다.
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

// 파일에서 정해진 접두사 뒤의 값을 읽습니다. (state 파일 형식: "key=value")
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

// ACL 적용 요청을 파일로 남깁니다.
//
// @param commands IOS 설정 명령 목록 (configure terminal 부터)
// @return 요청 기록 성공 여부
bool WriteIosAclRequest(const std::vector<std::string>& commands)
{
    const std::string dir = IosApplyDir();
    if (::mkdir(dir.c_str(), 0775) != 0 && errno != EEXIST)
    {
        std::cerr << "[POLICY] cannot create IOS apply dir: " << dir
                  << " (" << std::strerror(errno) << ")\n";
        return false;
    }

    // 명령만 이어붙인 본문입니다. 이 문자열이 바뀌었을 때만 장치가 다시 적용합니다.
    std::string body;
    for (const auto& command : commands)
    {
        body += command;
        body += '\n';
    }

    // 임시 파일에 쓰고 rename 합니다.
    // ⚠️ 바로 덮어쓰면 EEM 스크립트가 <b>반쓴 파일</b>을 읽어 일부만 적용할 수
    //    있습니다. rename 은 같은 파일시스템에서 원자적입니다.
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

    // 장치가 지난번 요청을 반영했는지 확인합니다.
    //
    // ⚠️ 여기서 "큐에 넣었다" 와 "장치에 적용됐다" 는 다릅니다. 구분하지 않으면
    //    EEM 적용 경로가 죽어 있을 때 서버는 정상으로 보이고 장치만 차단이
    //    안 걸린 상태가 됩니다 — 가장 찾기 어려운 실패입니다.
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

// IOS 측 EEM applet 이 `guestshell run` 으로 **즉시** 수집해 둔 IOS CLI 스냅샷을 읽습니다.
//
// 왜 필요한가
//   guestshell 의 IOSP 세션은 `guestshell run` 이 시작된 뒤 **단 몇 초만** 유효합니다.
//   (실장비 실측: 기동 직후 `dohost "show clock"` 은 성공,
//    30초 뒤 같은 세션은 `Unexpected Error`.)
//   그래서 수십 초 뒤에 dohost 를 호출하는 프로버는 항상 실패합니다.
//   IOS(EEM) 가 세션이 살아 있는 동안(=`guestshell run` 실행 즉시) 조회 결과를 파일로
//   남기고, 프로버는 그 스냅샷을 읽어 라우팅/ARP/인터페이스 상태를 채웁니다.
//
// 조회(`show ...`) 명령에만 적용합니다. 설정 명령은 실제로 적용돼야 하므로
// 스냅샷으로 대체하면 안 됩니다.
std::string ReadIosCliSnapshot(const std::string& script)
{
    if (script.rfind("show ", 0) != 0)
    {
        return {};
    }

    const std::string data_dir = SonarDataDir();

    // 파일명 규칙은 IOS 측 수집 스크립트와 동일합니다. 영숫자만 남기고 나머지는 '_'.
    std::string name;
    name.reserve(script.size());
    for (const char ch : script)
    {
        name += (std::isalnum(static_cast<unsigned char>(ch)) != 0) ? ch : '_';
    }

    const std::string path = data_dir + "/ios_cli/" + name + ".out";
    struct stat info{};
    if (::stat(path.c_str(), &info) != 0 || !S_ISREG(info.st_mode))
    {
        return {};
    }

    // 오래된 스냅샷은 쓰지 않습니다. 수집 주기의 몇 배를 넘으면 stale 로 봅니다.
    constexpr long kMaxAgeSeconds = 300;
    const long age =
        static_cast<long>(std::time(nullptr)) - static_cast<long>(info.st_mtime);
    if (age < 0 || age > kMaxAgeSeconds)
    {
        return {};
    }

    std::ifstream input(path);
    if (!input)
    {
        return {};
    }
    std::stringstream buffer;
    buffer << input.rdbuf();
    std::string content = buffer.str();

    // 첫 줄은 수집 스크립트가 넣는 헤더(타임스탬프/명령)입니다. 본문만 씁니다.
    if (const auto newline = content.find('\n'); newline != std::string::npos)
    {
        content.erase(0, newline + 1);
    }

    // 오류 마커가 남아 있으면 신뢰하지 않습니다.
    const ios_cli::OutputCheck cached = ios_cli::CheckOutput(content);
    if (cached.failed)
    {
        return {};
    }
    return cached.text;
}

// IPv4 문자열을 32비트 정수로 바꿉니다. (예: "192.168.122.254")
//
// @param text  주소 문자열
// @param value 결과 (성공 시에만 채워집니다)
// @return 해석 성공 여부
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

// IPv4 주소가 CIDR 대역 안에 있는지 봅니다.
//
// @param address 주소 문자열 (예: "192.168.122.254")
// @param cidr    대역 (예: "192.168.122.254/32")
// @return 포함되면 true
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

    // 접두사 길이를 직접 해석합니다. (stoi 예외를 쓰지 않기 위해)
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

// IOS 의 `show ip interface brief` 스냅샷에서 (인터페이스 이름, 주소) 목록을 읽습니다.
//
// ⚠️ 프로버가 `dohost` 로 직접 조회하지 않고 <b>EEM 스냅샷</b>을 읽는 이유:
//    서비스로 도는 프로버에는 IOSP 세션이 없어 dohost 가 항상 실패합니다.
//    (ReadIosCliSnapshot 주석 참고)
//
// @return (이름, 주소) 목록 (스냅샷이 없거나 오래되면 빈 목록)
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
            // 헤더는 `Interface  IP-Address  OK? ...` 입니다.
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
            // `unassigned` 인터페이스는 주소가 없습니다 — 건너뜁니다.
            continue;
        }
        interfaces.emplace_back(name, address);
    }
    return interfaces;
}

// 차단 ACL 을 묶을 IOS 인터페이스를 <b>장치의 실제 목록</b>에서 찾습니다.
//
// 왜 서버가 준 인터페이스를 그대로 쓰지 않는가
//   서버의 `applied_interface` 는 <b>배포 기본값</b>입니다(예: GigabitEthernet0/0/1).
//   그런데 8000v 는 `GigabitEthernet1..4` 를 씁니다. 존재하지 않는 인터페이스를
//   설정에 넣으면 IOS 가 그 줄을 거부하고, dohost 는
//   `CLI syntax error or execution Failure` 만 돌려줍니다 —
//   <b>ACL 자체도 만들어지지 않고</b> 차단이 통째로 사라집니다.
//   (RVI 실장비에서 실측한 실패입니다)
//
//   그래서 이 정책의 대역(subnet_cidr)이 실제로 설정된 인터페이스를 먼저 찾고,
//   없으면 서버 힌트가 장치에 존재할 때만 씁니다.
//
// @param hint        서버가 준 인터페이스 이름 (틀릴 수 있음)
// @param subnet_cidr 이 정책이 보호하는 대역
// @return 묶을 인터페이스 이름 (찾지 못하면 빈 문자열 = 묶지 않음)
std::string ResolveIosBindInterface(const std::string& hint, const std::string& subnet_cidr)
{
    const auto interfaces = ReadIosInterfaces();
    if (interfaces.empty())
    {
        std::cerr << "[POLICY] IOS interface table unavailable (EEM snapshot missing); "
                     "ACL will be created without interface binding\n";
        return {};
    }

    // 1순위: 이 정책의 대역이 실제로 설정된 인터페이스입니다.
    //        (ACL 을 그 대역의 입구에 거는 것이 의도입니다)
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

    // 2순위: 서버 힌트가 장치에 실제로 있는 인터페이스일 때만 씁니다.
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
    reader_.Reset(ioc_, stream_);
}

bool ManagementService::connect()
{
    try
    {
        if (host_.empty() || port_ <= 0)
        {
            return false;
        }

        reader_.Reset(ioc_, stream_);
        hello_sent_ = false;
        auto const results = resolver_.resolve(host_, std::to_string(port_));

        // 제한 시간이 있는 연결입니다. 자세한 이유는 ConnectWithTimeout 주석 참고
        // (표준 동기 connect 에는 타임아웃이 없고,
        //  beast::tcp_stream::expires_after() 는 비동기 전용이며,
        //  std::async + wait_for 는 future 소멸자에서 다시 블록된다)
        const boost::system::error_code ec =
            sonar::net::ConnectWithTimeout(stream_, results, kConnectTimeout);
        if (ec)
        {
            throw boost::system::system_error(ec);
        }

        sonar::net::HandshakeWithTimeout(ioc_, stream_, host_, target_, kHandshakeTimeout);

        connected_ = true;
        return true;
    }
    catch (const std::exception& error)
    {
        std::cerr << "[MGMT] connection failed: " << host_ << ':' << port_
                  << target_ << " (" << error.what() << ")\n";
        reader_.Reset(ioc_, stream_);
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
        sonar::net::WriteWithTimeout(ioc_, stream_, message.dump(), kConnectTimeout);
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
    if (!connected_) return false;
    boost::system::error_code ec;
    const bool received = reader_.ReadFor(ioc_, stream_, message, timeout, ec);
    if (ec) connected_ = false;
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
    if (stop_token.stop_requested()) return nullptr;
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
        bool received = false;
        const auto greeting_deadline = std::chrono::steady_clock::now() + kResponseTimeout;
        while (!stop_token.stop_requested() && connected_ &&
               std::chrono::steady_clock::now() < greeting_deadline)
        {
            if (TryReceive(greeting, std::chrono::milliseconds(200))) { received = true; break; }
        }
        if (received)
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

    if (stop_token.stop_requested()) return nullptr;
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
        if (!TryReceive(raw, std::min(remaining, std::chrono::milliseconds(200))))
        {
            if (!connected_) break;
            continue;
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
    const command_runner::Result result =
        command_runner::RunWithStatus(command, kCommandTimeout, 4 * 1024 * 1024, stop_token_);
    if (!result.completed)
    {
        std::cerr << "[COMMAND] execution timed out or failed to complete\n";
        return false;
    }
    if (result.exit_code != 0)
    {
        std::cerr << "[COMMAND] execution failed with exit code " << result.exit_code << '\n';
        return false;
    }
    return true;
}

std::string ManagementService::RunCommandOutput(const std::string& command)
{
    const command_runner::Result result =
        command_runner::RunWithStatus(command, kCommandTimeout, 4 * 1024 * 1024, stop_token_);
    if (!result.completed)
    {
        std::cerr << "[COMMAND] output command timed out or failed to complete\n";
    }
    else if (result.exit_code != 0)
    {
        std::cerr << "[COMMAND] output command failed with exit code "
                  << result.exit_code << '\n';
    }
    return result.output;
}

bool ManagementService::ApplyPolicyCommand(const PolicyCommand& command)
{
    Json payload = command.payload;
    payload["command"] = PolicyCommand::ActionName(command.action);

    switch (command.device_type)
    {
    case DeviceType::kSwitch:
        if (command.product == "OpenVSwitch")
        {
            return ApplyOpenVSwitchPolicy(payload);
        }
        if (command.product == "Arista")
        {
            return ApplyAristaSwitchPolicy(payload);
        }
        if (command.product == "Cisco")
        {
            return ApplyCiscoSwitchPolicy(payload);
        }
        break;
    case DeviceType::kRouter:
        if (command.product == "FRR")
        {
            return ApplyFrrRouterPolicy(payload);
        }
        if (command.product == "Cisco 8000v" || command.product == "Cisco IOS XE" ||
            command.product == "Cisco")
        {
            return ApplyCiscoRouterPolicy(payload);
        }
        break;
    case DeviceType::kFirewall:
        if (command.product == "nftables")
        {
            return ApplyNftablesPolicy(payload);
        }
        break;
    case DeviceType::kVirtualMachine:
        return ApplyVmPolicy(payload);
    }

    std::cerr << "[POLICY] Unsupported product '" << command.product
              << "' for device type\n";
    return false;
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

            // ⚠️ ovs-ofctl 의 대상은 <b>브리지</b>입니다. 예전에는 업링크
            //    포트(applied_interface=eth0)를 넣어 "no bridge named eth0"
            //    로 실패했습니다. bridge_name 을 먼저 봅니다.
            const std::string bridge = ReadApplyTarget(policy);
            if (src.empty() || dst.empty() || bridge.empty())
            {
                std::cerr << "[POLICY] OVS ACL needs source/destination/bridge\n";
                return false;
            }

            const bool deny = (action == "deny" || action == "drop");

            // ⚠️ 차단을 허용보다 높은 우선순위로 둡니다.
            //    같은 5-튜플에 두 규칙이 걸렸을 때 차단이 이겨야 합니다.
            //    (허용이 이기면 등급 건너뛰기가 조용히 통과합니다)
            const int priority = deny ? kAclPriority : kAclPriority - 100;

            // openflow 는 대역을 CIDR 표기로 그대로 받습니다.
            // nw_src/nw_dst 는 흐름 매칭의 표준 필드입니다.
            //
            // 쿠키를 찍는 이유는 일괄 적용(ApplyOpenVSwitchAcl)과 같습니다 —
            // 나중에 <b>우리 흐름만</b> 골라 지우기 위해서입니다.
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
            // ⚠️ 예전에는 `ovs-ofctl del-flows <인터페이스>` 로 브리지의
            //    흐름을 <b>전부</b> 지웠습니다. 그러면 다른 도구가 넣은
            //    흐름까지 함께 사라지고, 대상도 인터페이스라 실패했습니다
            //    (ovs-ofctl 은 브리지를 받습니다).
            //    이제는 우리 쿠키가 찍힌 흐름만 지웁니다.
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

// Open vSwitch 의 차단 플로우를 <b>선언적으로</b> 맞춥니다.
//
// 왜 쿠키(cookie)를 쓰는가
//   OVS 는 IOS 와 달리 흐름 하나를 정확히 지울 수 있습니다. 그런데 지울
//   대상을 찾으려면 "우리가 넣은 흐름" 과 "다른 도구가 넣은 흐름" 을
//   구분해야 합니다. 예전 구현은 `ovs-ofctl del-flows <인터페이스>` 로
//   <b>인터페이스의 모든 흐름</b>을 지웠습니다 — 운영자가 직접 넣은 QoS/
//   미러 흐름까지 함께 사라집니다.
//
//   그래서 우리가 넣는 흐름에만 쿠키를 찍고, 쿠키로만 지웁니다.
//   (OpenFlow 의 cookie 는 정확히 이 용도입니다)
//
// ⚠️ ovs-ofctl 의 대상은 <b>브리지</b>입니다. 인터페이스 이름(eth0)을 넣으면
//    "no bridge named eth0" 로 실패합니다. 그래서 bridge_name 을 먼저 봅니다.
//    (apply 노드의 rule_target.bridge_name)
bool ManagementService::ApplyOpenVSwitchAcl(const Json& policy)
{
    const std::string bridge = ReadApplyTarget(policy);
    if (bridge.empty())
    {
        std::cerr << "[POLICY] OVS ACL apply without bridge name\n";
        return false;
    }

    const std::vector<AclEntry> entries = ReadAclEntries(policy);

    // 1) 우리가 이전에 넣은 흐름을 먼저 모두 지웁니다.
    //
    //    ⚠️ 쿠키 필터는 반드시 `cookie=<값>/<마스크>` 형태여야 합니다.
    //       `/` 없이 쓰면 ovs-ofctl 이 마스크를 0xffffffff 로 보고 "정확히
    //       이 쿠키" 만 지웁니다 — 동작은 같지만, 마스크를 명시하는 편이
    //       의도가 분명합니다.
    const std::string delete_command =
        "ovs-ofctl -O OpenFlow13 del-flows " + bridge + " cookie=" + kAclCookie + "/-1";
    if (!RunCommand(delete_command))
    {
        std::cerr << "[POLICY] OVS flow cleanup failed on bridge " << bridge << '\n';
        return false;
    }

    // 2) 남아 있어야 하는 차단 흐름을 다시 넣습니다.
    //
    //    차단이 없으면(운영자가 마지막 금지 연결을 지운 경우) 여기서 끝납니다.
    //    그것이 정상 동작입니다 — 이전 규칙이 위에서 지워졌으므로 차단이 풀립니다.
    for (const auto& entry : entries)
    {
        // ⚠️ 차단을 허용(normal)보다 높은 우선순위로 둡니다. 같은 5-튜플에
        //    두 흐름이 걸렸을 때 차단이 이겨야 합니다. (허용이 이기면 등급
        //    건너뛰기가 조용히 통과합니다)
        //
        //    in_port 를 쓰지 않는 이유: 존 간 이동은 브리지의 어느 포트로든
        //    들어올 수 있습니다. 업링크만 지정하면 다른 경로로 들어온 같은
        //    이동을 놓칩니다.
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
    //
    // ⚠️ 명령마다 dohost를 따로 띄우면 설정 컨텍스트가 사라집니다.
    //    `configure terminal` 프로세스가 끝나면 설정 모드도 끝나므로,
    //    뒤이은 `interface ...` / `no shutdown` 은 exec 모드에서 실행되어
    //    `% Invalid input` 으로 거부됩니다. 그래서 전체 시퀀스를 dohost 한 번에
    //    세미콜론으로 이어 붙여 실행합니다. (components/terminal/ios_cli.hpp)
    if (cli_commands.empty())
    {
        return {};
    }

    const std::string script = ios_cli::BuildScript(cli_commands);
    if (script.empty())
    {
        return {};
    }

    const std::string raw = RunCommandOutput("dohost " + ShellQuote(script));

    // dohost 는 IOS 가 명령을 거부해도 종료코드 0 을 돌려줍니다.
    // 출력의 오류 줄로 실패를 판정하고, 실패면 빈 문자열을 돌려줍니다.
    const ios_cli::OutputCheck check = ios_cli::CheckOutput(raw);
    if (check.session_unavailable)
    {
        // IOSP 세션은 `guestshell run` 시작 후 몇 초 안에 만료됩니다. 그래서 프로버가
        // 직접 dohost 하는 것은 원칙적으로 실패합니다. IOS(EEM) 가 즉시 수집해 둔
        // 스냅샷이 있으면 그것으로 대체합니다. (조회 명령에만 적용)
        const std::string snapshot = ReadIosCliSnapshot(script);
        if (!snapshot.empty())
        {
            std::cerr << "[IOS] dohost 세션 만료 → EEM 수집 스냅샷 사용: " << script << '\n';
            return snapshot;
        }
        std::cerr << "[IOS] guestshell↔IOS CLI 세션이 없습니다. dohost 실패: "
                     "IOSP_SESSION/app-session-info 부재, EEM 수집 스냅샷도 없음. "
                     "IOS 에서 `guestshell run` 으로 즉시 수집하거나 "
                     "`guestshell disable` → `guestshell enable` 로 앱을 재시작해야 합니다.\n";
    }
    else if (check.failed)
    {
        std::cerr << "[IOS] dohost reported an IOS CLI error for: " << script << '\n';
    }
    return check.text;
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

// IOS-XE 라우터의 차단 ACL 을 <b>선언적으로</b> 맞춥니다.
//
// 왜 한 번에 지우고 다시 쓰는가
//   IOS 확장 ACL 은 규칙 하나만 지우는 문법이 없습니다.
//   `no ip access-list extended SONAR-CSO` 는 ACL 을 통째로 지웁니다.
//   그래서 "지금 남아 있어야 하는 규칙 전체" 를 받아 매번 다시 씁니다.
//   연결 하나만 보고 만든 규칙을 차례로 보내면 <b>운영자가 지운 규칙이
//   장치에 그대로 남습니다</b> — 차단이 안 풀린 채 남는 가장 위험한 실패입니다.
//
// ⚠️ 반드시 한 번의 dohost 호출로 보냅니다.
//    dohost 는 호출마다 별도 세션이라 `configure terminal` 컨텍스트가
//    다음 호출로 이어지지 않습니다. 나눠 보내면 두 번째 호출은
//    exec 모드에서 `ip access-list ...` 를 실행해 실패합니다.
bool ManagementService::ApplyCiscoRouterAcl(const Json& policy)
{
    const std::string acl_name = ReadAclName(policy);
    if (acl_name.empty())
    {
        std::cerr << "[POLICY] IOS ACL apply without rule_target.acl_name\n";
        return false;
    }

    const std::string hint = ReadApplyTarget(policy);
    const std::string subnet_cidr = policy_json::AsString(policy, "subnet_cidr");
    // ⚠️ 서버 힌트를 그대로 쓰지 않습니다. 장치에 없는 인터페이스를 넣으면
    //    IOS 가 그 줄을 거부하고 <b>ACL 자체가 만들어지지 않습니다.</b>
    //    (RVI 실장비: 서버 기본값 GigabitEthernet0/0/1, 장치에는 GigabitEthernet1..4)
    const std::string interface_name = ResolveIosBindInterface(hint, subnet_cidr);
    const std::vector<AclEntry> entries = ReadAclEntries(policy);
    // 명령 생성은 순수 함수(acl::BuildIosAclCommands)가 합니다. 그래야
    // "permit ip any any 가 빠지지 않았는지" 를 장치 없이 고정할 수 있습니다 —
    // 그 줄이 빠지면 인터페이스가 통째로 죽습니다.
    const std::vector<std::string> commands =
        acl::BuildIosAclCommands(acl_name, interface_name, entries);

    const std::string output = ExecuteIosCli(commands);
    if (output.empty())
    {
        // 직접 적용이 실패했다면 파일로 남겨 EEM 이 반영하게 합니다.
        //
        // 이 경로가 필요한 이유: guestshell 의 IOSP 세션은 `guestshell run`
        // 으로 띄운 프로세스에서만 잠깐 유효해서, 서비스로 돌아가는 프로버는
        // dohost 가 항상 실패합니다(실장비 실측). 그렇다고 false 만 돌려주면
        // 차단이 영원히 안 걸립니다.
        if (WriteIosAclRequest(commands))
        {
            return true;
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

// FRR 라우터의 차단 ACL 을 선언적으로 맞춥니다. (Cisco 의 ApplyCiscoRouterAcl 과 같은 이유)
//
// ⚠️ 이 랩에는 FRR 라우터가 없어 <b>실장비 검증을 하지 못했습니다.</b>
//    문법은 FRR(zebra) 의 표준 access-list 입니다:
//      access-list <name> deny ip <src> <wild> <dst> <wild>
//    vtysh 는 표준 ACL 에서 `ip` 키워드를 받습니다. (IOS 와 달리 이름이
//    숫자/문자 모두 가능)
//
// ⚠️ `no access-list <name>` 은 목록 전체를 지웁니다. 그래서 IOS 와 같은
//    "지우고 다시 쓰기" 전략을 씁니다 — 지운 규칙이 남지 않게 하는 유일한
//    방법입니다.
bool ManagementService::ApplyFrrRouterAcl(const Json& policy)
{
    const std::string acl_name = ReadAclName(policy);
    if (acl_name.empty())
    {
        std::cerr << "[POLICY] FRR ACL apply without rule_target.acl_name\n";
        return false;
    }

    const std::string interface_name = ReadApplyTarget(policy);
    const std::vector<AclEntry> entries = ReadAclEntries(policy);
    // IOS 와 같은 순수 함수로 명령을 만듭니다. 두 벤더가 같은 규칙
    // (지우고 다시 쓰기 + 마지막 permit)을 따르는지 나란히 볼 수 있습니다.
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