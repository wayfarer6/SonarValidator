#include "utils/log.hpp"
#ifndef SONAR_VALIDATOR_PROBER_ACL_COMMANDS_HPP_
#define SONAR_VALIDATOR_PROBER_ACL_COMMANDS_HPP_

#include <iostream>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "components/policy/policy_json.hpp"

namespace acl
{

struct Entry
{
    std::string source;
    std::string source_wildcard;
    std::string destination;
    std::string destination_wildcard;
    std::string reason;
};

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
            SV_LOG_WARN << "[POLICY] dropping ACL entry with empty address (src='"
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

inline std::vector<std::string> BuildIosAclCommands(const std::string& acl_name,
                                                    const std::string& interface_name,
                                                    const std::vector<Entry>& entries)
{
    std::vector<std::string> commands = {"configure terminal"};

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
