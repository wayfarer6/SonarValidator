#include "components/device/firewall/firewall_interface/firewall.hpp"

#include "components/parser/cli_output_parser.hpp"

#include <nlohmann/json.hpp>

#include <cstddef>
#include <string>
#include <utility>
#include <vector>

namespace {

std::string AsString(const nlohmann::json& value)
{
    if (value.is_string()) {
        return value.get<std::string>();
    }
    if (value.is_null()) {
        return "";
    }
    return value.dump();
}

}  // namespace

void Firewall::parseNftablesRuleset(const std::string& raw_output)
{
    tables_.clear();

    // 수작업 라인 스캔 대신 공용 ANTLR 문법(NftablesRule.g4)에 위임한다.
    const nlohmann::json parsed = cli_parser::ParseFirewallRules(raw_output);
    if (!parsed.contains("tables") || !parsed["tables"].is_array()) {
        return;
    }

    for (const auto& table_json : parsed["tables"]) {
        NftTable table;
        table.family = AsString(table_json.value("family", nlohmann::json("")));
        table.name = AsString(table_json.value("name", nlohmann::json("")));

        if (table_json.contains("chains") && table_json["chains"].is_array()) {
            for (const auto& chain_json : table_json["chains"]) {
                NftChain chain;
                chain.family = table.family;
                chain.name = AsString(chain_json.value("name", nlohmann::json("")));
                chain.type = AsString(chain_json.value("type", nlohmann::json("")));
                chain.hook = AsString(chain_json.value("hook", nlohmann::json("")));
                chain.priority = AsString(chain_json.value("priority", nlohmann::json("")));
                chain.policy = AsString(chain_json.value("policy", nlohmann::json("")));

                if (chain_json.contains("rules") && chain_json["rules"].is_array()) {
                    for (const auto& rule_json : chain_json["rules"]) {
                        NftRule rule;
                        rule.raw = AsString(rule_json.value("raw", nlohmann::json("")));
                        rule.match = AsString(rule_json.value("match", nlohmann::json("")));
                        rule.statement = rule.match;
                        rule.action = AsString(rule_json.value("action", nlohmann::json("")));
                        if (rule.action.empty()) {
                            rule.action = "set";
                        }

                        if (rule_json.contains("match_pairs") &&
                            rule_json["match_pairs"].is_object()) {
                            const auto& pairs = rule_json["match_pairs"];
                            const auto state = pairs.find("ct state");
                            if (state != pairs.end()) {
                                rule.connection_state = AsString(*state);
                            }
                        }

                        chain.rules.push_back(std::move(rule));
                    }
                }

                table.chains.push_back(std::move(chain));
            }
        }

        tables_.push_back(std::move(table));
    }
}
