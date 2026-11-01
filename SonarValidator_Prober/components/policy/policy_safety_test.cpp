#include "components/policy/policy_actions.hpp"
#include "components/policy/ios_acl_target.hpp"
#include <functional>
#include <iostream>
#include <stdexcept>

namespace {
class Device final : public DeviceCommandExecutor {
public:
    std::vector<std::string> changes;
    std::vector<std::vector<std::string>> ios;
    bool succeed = true;
    std::string brief, binding, rules;
    bool RunCommand(const std::string& command) override {
        changes.push_back(command);
        return succeed;
    }
    std::string RunCommandOutput(const std::string&) override {
        return "OFPST_FLOW reply:\n cookie=0x534f4e41,nw_src=10.0.1.0/24,nw_dst=10.0.2.0/24 actions=drop\n";
    }
    std::string ExecuteIosCli(const std::vector<std::string>& commands) override {
        ios.push_back(commands);
        if (commands.front() == "show ip interface brief") return brief;
        if (commands.front().starts_with("show running-config")) return binding;
        if (commands.front().starts_with("show ip access-lists")) return rules;
        return "Router#";
    }
    std::string QueryAristaCli(const std::string&) override { return {}; }
};
void Require(bool ok, const char* label) {
    if (!ok) throw std::runtime_error(label);
}
}
int main() {
    unsigned int address = 0;
    Require(!ios_acl_target::ParseIpv4("999999999999999999999.1.1.1", address), "oversized octet must reject before integer overflow");
    Require(ios_acl_target::IpInCidr("10.0.1.1", "10.0.1.0/24"), "valid target membership");
    Require(!ios_acl_target::IpInCidr("10.0.2.1", "10.0.1.0/24"), "unrelated subnet must not match");
    const nlohmann::json ovs = {{"command", "apply"}, {"bridge_name", "br-data"},
        {"acl_rules", {{{"ip_saddr", "10.0.1.0/24"}, {"ip_daddr", "10.0.2.0/24"}}}}};
    Device device;
    PolicyActions actions(device);
    Require(actions.ApplyOpenVSwitchPolicy(ovs), "OVS atomic replacement failed");
    Require(device.changes.size() == 1 && device.changes[0].find("OpenFlow14 bundle") != std::string::npos,
            "OVS replacement must be one bundle, with no prior flow deletion");
    device.changes.clear(); device.succeed = false;
    Require(!actions.ApplyOpenVSwitchPolicy(ovs) && device.changes.size() == 1,
            "failed OVS bundle must report failure without a fallback deletion");

    const nlohmann::json ios = {{"command", "apply"}, {"subnet_cidr", "10.0.1.0/24"},
        {"rule_target", {{"acl_name", "SONAR-CSO"}, {"applied_interface", "GigabitEthernet2"}}},
        {"acl_rules", {{{"ip_saddr", "10.0.1.0"}, {"ip_saddr_wildcard", "0.0.0.255"},
                        {"ip_daddr", "10.0.2.0"}, {"ip_daddr_wildcard", "0.0.0.255"}}}}};
    device.ios.clear();
    Require(!actions.ApplyCiscoRouterPolicy(ios) && device.ios.size() == 1,
            "missing interface inventory must reject IOS ACL before any change");
    device.brief = "Interface IP-Address OK? Method Status Protocol\nGigabitEthernet2 10.0.1.1 YES manual up up\n";
    Require(!actions.ApplyCiscoRouterPolicy(ios), "IOS command output alone must not confirm ACL application");
    device.binding = "interface GigabitEthernet2\n ip access-group SONAR-CSO in\n";
    device.rules = "Extended IP access list SONAR-CSO\n deny ip 10.0.1.0 0.0.0.255 10.0.2.0 0.0.0.255\n permit ip any any\n";
    Require(actions.ApplyCiscoRouterPolicy(ios), "confirmed IOS binding and rules should succeed");
    std::cout << "Policy safety checks PASS\n";
}
