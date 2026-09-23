#include "firewall.hpp"

#include <cctype>
#include <sstream>
#include <utility>

namespace {

std::size_t FindActionPosition(const std::string& text)
{
    const std::string keywords[] = {"accept", "drop", "reject"};
    std::size_t best = std::string::npos;

    for (const std::string& keyword : keywords) {
        const std::size_t pos = text.rfind(keyword);
        if (pos != std::string::npos &&
            (pos + keyword.size() == text.size() ||
             std::isspace(static_cast<unsigned char>(text[pos + keyword.size()])) != 0) &&
            (pos == 0 || std::isspace(static_cast<unsigned char>(text[pos - 1])) != 0)) {
            if (best == std::string::npos || pos > best) {
                best = pos;
            }
        }
    }

    return best;
}

std::string Trim(const std::string& input)
{
    std::size_t start = 0;
    while (start < input.size() && std::isspace(static_cast<unsigned char>(input[start])) != 0) {
        ++start;
    }

    std::size_t end = input.size();
    while (end > start && std::isspace(static_cast<unsigned char>(input[end - 1])) != 0) {
        --end;
    }

    return input.substr(start, end - start);
}

std::string StripTrailingSemicolon(const std::string& text)
{
    std::string value = Trim(text);
    if (!value.empty() && value.back() == ';') {
        value.pop_back();
    }
    return Trim(value);
}

std::string ExtractBefore(const std::string& text, const std::string& token)
{
    const std::size_t pos = text.find(token);
    if (pos == std::string::npos) {
        return "";
    }
    return Trim(text.substr(0, pos));
}

std::string ExtractAfter(const std::string& text, const std::string& token)
{
    const std::size_t pos = text.find(token);
    if (pos == std::string::npos) {
        return "";
    }
    return Trim(text.substr(pos + token.size()));
}

}  // namespace

void Firewall::parseNftablesRuleset(const std::string& raw_output)
{
    tables_.clear();

    std::istringstream stream(raw_output);
    std::string line;
    std::size_t current_table_index = static_cast<std::size_t>(-1);
    std::size_t current_chain_index = static_cast<std::size_t>(-1);

    auto parse_rule = [](const std::string& text) {
        NftRule rule;
        rule.raw = Trim(text);
        const std::size_t action_pos = FindActionPosition(rule.raw);

        if (action_pos != std::string::npos) {
            const std::string action =
                rule.raw.compare(action_pos, 6, "accept") == 0 ? "accept" :
                rule.raw.compare(action_pos, 4, "drop") == 0 ? "drop" :
                rule.raw.compare(action_pos, 6, "reject") == 0 ? "reject" : "";

            if (!action.empty()) {
                rule.statement = Trim(rule.raw.substr(0, action_pos));
                rule.match = rule.statement;
                rule.action = action;
            }
        }

        if (rule.action.empty()) {
            rule.statement = Trim(rule.raw);
            rule.match = rule.statement;
            rule.action = "set";
        }

        const std::size_t ct_state_pos = rule.match.find("ct state ");
        if (ct_state_pos != std::string::npos) {
            const std::size_t value_start = ct_state_pos + std::string("ct state ").size();
            std::size_t value_end = value_start;
            while (value_end < rule.match.size() &&
                   !std::isspace(static_cast<unsigned char>(rule.match[value_end])) &&
                   rule.match[value_end] != ';') {
                ++value_end;
            }
            rule.connection_state = Trim(rule.match.substr(value_start, value_end - value_start));
        }

        return rule;
    };

    while (std::getline(stream, line)) {
        line = Trim(line);
        if (line.empty()) {
            continue;
        }

        if (line == "}") {
            if (current_table_index != static_cast<std::size_t>(-1) &&
                current_chain_index != static_cast<std::size_t>(-1) &&
                current_chain_index < tables_[current_table_index].chains.size()) {
                current_chain_index = static_cast<std::size_t>(-1);
                continue;
            }

            current_chain_index = static_cast<std::size_t>(-1);
            current_table_index = static_cast<std::size_t>(-1);
            continue;
        }

        if (line.rfind("table ", 0) == 0) {
            std::string rest = Trim(line.substr(6));
            const std::size_t family_end = rest.find(' ');
            if (family_end == std::string::npos) {
                continue;
            }

            NftTable table;
            table.family = Trim(rest.substr(0, family_end));
            std::string name_and_brace = Trim(rest.substr(family_end + 1));
            if (!name_and_brace.empty() && name_and_brace.back() == '{') {
                name_and_brace.pop_back();
            }
            table.name = Trim(name_and_brace);

            tables_.push_back(std::move(table));
            current_table_index = tables_.size() - 1;
            current_chain_index = static_cast<std::size_t>(-1);
            continue;
        }

        if (line.rfind("chain ", 0) == 0) {
            if (current_table_index == static_cast<std::size_t>(-1)) {
                continue;
            }

            std::string rest = Trim(line.substr(6));
            const std::size_t name_end = rest.find(' ');
            if (name_end == std::string::npos) {
                continue;
            }

            std::string chain_name = Trim(rest.substr(0, name_end));
            std::string chain_rest = Trim(rest.substr(name_end + 1));
            if (!chain_rest.empty() && chain_rest.back() == '{') {
                chain_rest.pop_back();
            }
            if (Trim(chain_rest).empty()) {
                tables_[current_table_index].chains.push_back(NftChain{
                    tables_[current_table_index].family,
                    chain_name,
                    "",
                    "",
                    "",
                    "",
                    {}});
                current_chain_index = tables_[current_table_index].chains.size() - 1;
            }
            continue;
        }

        if (current_table_index == static_cast<std::size_t>(-1) ||
            current_chain_index == static_cast<std::size_t>(-1)) {
            continue;
        }

        NftChain& current_chain = tables_[current_table_index].chains[current_chain_index];

        if (line.rfind("type ", 0) == 0) {
            current_chain.type = ExtractAfter(line, "type ");
            const std::size_t hook_pos = current_chain.type.find(" hook ");
            if (hook_pos != std::string::npos) {
                const std::string hook_part = current_chain.type.substr(hook_pos + 6);
                current_chain.hook = ExtractBefore(hook_part, " priority ");
                const std::string remainder = Trim(hook_part.substr(current_chain.hook.size() + 10));
                current_chain.priority = StripTrailingSemicolon(ExtractBefore(remainder, ";"));
            }
            continue;
        }

        if (line.rfind("policy ", 0) == 0) {
            current_chain.policy = StripTrailingSemicolon(ExtractAfter(line, "policy "));
            continue;
        }

        if (line.rfind("hook ", 0) == 0) {
            const std::string rest = ExtractAfter(line, "hook ");
            const std::size_t space = rest.find(' ');
            if (space != std::string::npos) {
                current_chain.hook = Trim(rest.substr(0, space));
                const std::string remainder = Trim(rest.substr(space));
                if (remainder.rfind("priority ", 0) == 0) {
                    current_chain.priority = StripTrailingSemicolon(ExtractAfter(remainder, "priority "));
                }
            }
            continue;
        }

        if (line.rfind("priority ", 0) == 0) {
            current_chain.priority = StripTrailingSemicolon(ExtractAfter(line, "priority "));
            continue;
        }

        const NftRule rule = parse_rule(line);

        current_chain.rules.push_back(std::move(rule));
    }
}
