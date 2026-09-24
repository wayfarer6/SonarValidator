#include "components/device/firewall/firewall_interface/firewall.hpp"

#include <cassert>
#include <string>

int main()
{
    const std::string ruleset = R"(
        table ip filter {
            chain input {
                type filter hook input priority 0; policy accept;
                ct state established,related accept
                tcp dport 22 accept
                ip saddr 10.0.0.0/8 drop
            }
        }
    )";

    Firewall firewall;
    firewall.parseNftablesRuleset(ruleset);

    assert(firewall.tableCount() == 1);
    assert(firewall.tables()[0].name == "filter");
    assert(firewall.tables()[0].family == "ip");
    assert(firewall.tables()[0].chains.size() == 1);
    assert(firewall.tables()[0].chains[0].name == "input");
    assert(firewall.tables()[0].chains[0].rules.size() == 3);
    assert(firewall.tables()[0].chains[0].rules[0].match == "ct state established,related");
    assert(firewall.tables()[0].chains[0].rules[0].action == "accept");
    assert(firewall.tables()[0].chains[0].rules[0].connection_state == "established,related");
    assert(firewall.tables()[0].chains[0].rules[1].match == "tcp dport 22");
    assert(firewall.tables()[0].chains[0].rules[1].action == "accept");
    assert(firewall.tables()[0].chains[0].rules[2].match == "ip saddr 10.0.0.0/8");
    assert(firewall.tables()[0].chains[0].rules[2].action == "drop");

    return 0;
}
