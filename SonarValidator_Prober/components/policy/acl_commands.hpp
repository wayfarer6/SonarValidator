#ifndef SONAR_VALIDATOR_PROBER_ACL_COMMANDS_HPP_
#define SONAR_VALIDATOR_PROBER_ACL_COMMANDS_HPP_

#include <iostream>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "components/policy/policy_json.hpp"

// 서버가 보낸 일괄 차단 ACL(`acl_apply` 노드)을 장치 명령으로 옮기는 <b>순수 함수</b> 모음입니다.
//
// <h2>⚠️ 왜 순수 함수로 분리하는가</h2>
//   이 변환은 장치 설정을 실제로 바꾸는 명령을 만듭니다. 그런데 서비스 안에
//   있으면 "장치에 보낼 명령이 무엇인가" 를 네트워크 없이 확인할 수 없습니다.
//   랩에 장비가 없거나(FRR) 게스트셸 세션이 없으면 검증 자체가 불가능해집니다.
//
//   그래서 <b>해석과 명령 생성</b>을 여기로 빼고, 실제 전송은 서비스가 합니다.
//   그러면 "위험한 줄이 빠졌는지" 를 단위 테스트로 고정할 수 있습니다.
//
// <h2>역할 분담</h2>
//   - 서버: "지금 남아 있어야 하는 차단 목록 전체" 를 보냄 (선언적)
//   - 이 코드: 그 목록을 벤더 문법으로 옮김
//   - 서비스: 지우고 다시 쓰기(멱등) + 인터페이스 바인딩
namespace acl
{

// ACL 한 줄의 주소 4종(네트워크 주소 + 반전 마스크)입니다.
//
// 서버가 보낸 acl_rules[] 를 벤더별 문법으로 옮길 때 공통으로 씁니다.
struct Entry
{
    std::string source;
    std::string source_wildcard;
    std::string destination;
    std::string destination_wildcard;
    std::string reason;
};

// 일괄 ACL(acl_apply) 노드에서 차단 목록을 읽습니다.
//
// ⚠️ 주소가 하나라도 비면 그 줄을 <b>버립니다</b>. 빈 값을 그대로 문법에
//    넣으면 `deny ip   ` 처럼 되어 장치가 전체를 거부하거나, 더 나쁘게는
//    "any any" 로 해석되어 트래픽이 전부 끊깁니다.
//
// 두 형태를 모두 받습니다.
//   - { "match_criteria": { "ip_saddr": [...], ... }, "action": ["deny"] }
//   - { "ip_saddr": [...], ... }   (조건부가 곧 항목인 형태)
inline std::vector<Entry> ReadEntries(const nlohmann::json& policy)
{
    std::vector<Entry> entries;
    const auto rules = policy.find("acl_rules");
    if (rules == policy.end() || !rules->is_array())
    {
        return entries;
    }

    for (const auto& rule : *rules)
    {
        const auto match = rule.find("match_criteria");
        const nlohmann::json& source_json = (match != rule.end()) ? *match : rule;

        Entry entry;
        entry.source = policy_json::AsString(source_json, "ip_saddr");
        entry.source_wildcard = policy_json::AsString(source_json, "ip_saddr_wildcard");
        entry.destination = policy_json::AsString(source_json, "ip_daddr");
        entry.destination_wildcard = policy_json::AsString(source_json, "ip_daddr_wildcard");
        entry.reason = policy_json::AsString(rule, "reason");

        if (entry.source.empty() || entry.destination.empty())
        {
            std::cerr << "[POLICY] dropping ACL entry with empty address (src='"
                      << entry.source << "' dst='" << entry.destination << "')\n";
            continue;
        }
        entries.push_back(std::move(entry));
    }
    return entries;
}

// 일괄 ACL 을 적용할 ACL 이름을 읽습니다. (rule_target.acl_name)
inline std::string ReadName(const nlohmann::json& policy)
{
    const auto target = policy.find("rule_target");
    if (target == policy.end())
    {
        return {};
    }
    return policy_json::AsString(*target, "acl_name");
}

// 일괄 ACL 을 걸 대상(인터페이스/브리지)을 읽습니다.
//
// ⚠️ OVS 는 ovs-ofctl 의 대상이 <b>브리지</b>이므로 bridge_name 을 먼저 봅니다.
//    라우터(ACL 을 걸 인터페이스)는 applied_interface 만 있습니다.
inline std::string ReadApplyTarget(const nlohmann::json& policy)
{
    const auto target = policy.find("rule_target");
    if (target != policy.end())
    {
        const std::string bridge = policy_json::AsString(*target, "bridge_name");
        if (!bridge.empty())
        {
            return bridge;
        }
        const std::string interface_name = policy_json::AsString(*target, "applied_interface");
        if (!interface_name.empty())
        {
            return interface_name;
        }
    }
    return policy_json::AsString(policy, "bridge_name",
                                 policy_json::AsString(policy, "applied_interface"));
}

// Cisco IOS-XE 라우터의 차단 ACL 명령 목록을 만듭니다.
//
// ⚠️ 마지막에 `permit ip any any` 를 <b>반드시</b> 넣습니다.
//
//   IOS 확장 ACL 은 모든 줄을 훑고 매칭되는 줄이 없으면 <b>암묵적 deny any</b>
//   로 떨어집니다. 그런데 이 ACL 을 인터페이스에 걸면 그 인터페이스로 들어오는
//   트래픽 전체가 이 ACL 의 판정을 받습니다. 그래서 차단 목록만 넣고 permit 을
//   생략하면 차단 대상이 아닌 트래픽까지 막혀 <b>인터페이스가 죽습니다.</b>
//   라우터에서는 곧 관리 접속·텔레메트리 두절이고, 프로버가 서버에 보고하지
//   못해 스스로 고립됩니다.
//
//   `permit ip any any` 를 넣으면 이 ACL 은 이름 그대로 <b>차단 목록</b> 으로
//   동작합니다: deny 줄에 맞는 것만 막고 나머지는 통과합니다. ACL 은 첫 매칭에서
//   결정되므로 허용 목록에 있는 상대는 deny 줄에 매칭되지 않아 통과합니다.
//
// 마지막에 `end` 를 붙여 설정 모드에서 빠져나옵니다. 조회 명령이 뒤따르는
// 경로에서 설정 모드에 갇히면 `show ...` 가 거부되기 때문입니다.
//
// @param acl_name       ACL 이름 (예: SONAR-CSO)
// @param interface_name ACL 을 걸 인터페이스 (비어 있으면 만들기만 하고 걸지 않음)
// @param entries        차단할 주소 쌍 목록
// @return IOS CLI 명령 목록 (한 번의 dohost 호출에 이어 붙일 것)
inline std::vector<std::string> BuildIosAclCommands(const std::string& acl_name,
                                                    const std::string& interface_name,
                                                    const std::vector<Entry>& entries)
{
    std::vector<std::string> commands = {"configure terminal"};

    // 이전 규칙을 먼저 지웁니다. 빈 목록이면 여기서 끝납니다 — 운영자가
    // 마지막 금지 연결을 지운 경우가 정확히 이 경우입니다.
    //
    // ⚠️ 인터페이스 바인딩도 함께 풉니다. ACL 만 지우면 인터페이스에
    //    없는 ACL 을 가리키는 설정이 남아, IOS 가 버전에 따라 그 줄을
    //    조용히 제거하거나 남겨 두었다가 나중에 되살립니다.
    if (!interface_name.empty())
    {
        commands.push_back("interface " + interface_name);
        commands.push_back("no ip access-group " + acl_name + " in");
        commands.push_back("exit");
    }
    commands.push_back("no ip access-list extended " + acl_name);

    if (!entries.empty())
    {
        commands.push_back("ip access-list extended " + acl_name);
        for (const auto& entry : entries)
        {
            // ⚠️ 반전 마스크(0.0.0.255)입니다. 서브넷 마스크를 그대로 넣으면
            //    매칭되는 주소가 사실상 없어져 <b>차단이 조용히 사라집니다.</b>
            commands.push_back("deny ip " + entry.source + " " + entry.source_wildcard + " " +
                               entry.destination + " " + entry.destination_wildcard);
        }
        commands.push_back("permit ip any any");
        commands.push_back("exit");

        if (!interface_name.empty())
        {
            commands.push_back("interface " + interface_name);
            commands.push_back("ip access-group " + acl_name + " in");
            commands.push_back("exit");
        }
    }

    commands.push_back("end");
    return commands;
}

// FRR(vtysh) 라우터의 차단 ACL 명령 목록을 만듭니다.
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
//
// ⚠️ IOS 와 같은 이유로 `permit ip any any` 를 반드시 넣습니다. FRR 도
//    매칭되는 줄이 없으면 암묵적 deny 로 떨어지므로, 이 ACL 을 인터페이스에
//    걸면 차단 대상이 아닌 트래픽까지 막혀 인터페이스가 죽습니다.
//
// @param acl_name       ACL 이름
// @param interface_name ACL 을 걸 인터페이스 (비어 있으면 걸지 않음)
// @param entries        차단할 주소 쌍 목록
// @return vtysh 명령 목록
inline std::vector<std::string> BuildFrrAclCommands(const std::string& acl_name,
                                                    const std::string& interface_name,
                                                    const std::vector<Entry>& entries)
{
    std::vector<std::string> lines = {"configure terminal"};

    if (!interface_name.empty())
    {
        lines.push_back("interface " + interface_name);
        lines.push_back("no ip access-group " + acl_name + " in");
        lines.push_back("exit");
    }
    lines.push_back("no access-list " + acl_name);

    if (!entries.empty())
    {
        for (const auto& entry : entries)
        {
            lines.push_back("access-list " + acl_name + " deny ip " + entry.source + " " +
                            entry.source_wildcard + " " + entry.destination + " " +
                            entry.destination_wildcard);
        }
        lines.push_back("access-list " + acl_name + " permit ip any any");

        if (!interface_name.empty())
        {
            lines.push_back("interface " + interface_name);
            lines.push_back("ip access-group " + acl_name + " in");
            lines.push_back("exit");
        }
    }
    return lines;
}

}  // namespace acl

#endif  // SONAR_VALIDATOR_PROBER_ACL_COMMANDS_HPP_
