#include "utils/log.hpp"
#include "components/policy/quarantine_handler.hpp"
#include "components/policy/interface_quarantine.hpp"
#include "utils/runtime_environment.hpp"

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

bool HandleCommand(const ProberConfig& config, ManagementService& mgmt,
                   const nlohmann::json& message) {
    if (!IsQuarantineCommand(message)) return false;
    const auto& payload = envelope::Payload(message);
    const std::string agent = config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();
    mgmt.SendEnvelope(envelope::Make(envelope::kAck, agent,
        envelope::DeviceTypeToString(config.GetDeviceType()), envelope::CorrelationId(message),
        {{"action",policy_json::AsString(payload,"action")},{"ok",false},
         {"request_id",policy_json::AsString(payload,"request_id")},
         {"detail","Versioned quarantine plan required; legacy interface commands are disabled"}}));
    return true;
}

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


namespace {
bool ReconcileNodeFirewall(bool isolate,const ProberConfig& config,ManagementService& mgmt,std::string& detail) {
    if (mgmt.RunCommand("command -v ovs-vsctl >/dev/null 2>&1")) {
        std::istringstream in(mgmt.RunCommandOutput("ovs-vsctl list-br"));std::string bridge;
        std::vector<std::string> bridges;
        const auto routes=nlohmann::json::parse(mgmt.RunCommandOutput("ip -j route get "+Quote(config.GetServerIpv4())),nullptr,false);
        if(!routes.is_array() || routes.empty() || !routes[0].contains("dev")) {detail="Management route discovery failed";return false;}
        while(std::getline(in,bridge)) if(!Trim(bridge).empty()) {
            bridge=Trim(bridge);
            if(bridge==routes[0]["dev"].get<std::string>()) {detail="Management bridge cannot be isolated";return false;}
            bridges.push_back(bridge);
        }
        if(bridges.empty()) {detail="No OVS data bridges found";return false;}
        for(const auto& br:bridges) {
            std::string body="flow delete cookie=0x534f4e41524e/0xffffffffffffffff\n";
            if(isolate) body+="flow add cookie=0x534f4e41524e,table=0,priority=65501,actions=drop\n";
            if(!mgmt.RunCommand("printf %s "+Quote(body)+" | ovs-ofctl -O OpenFlow14 bundle "+Quote(br)+" -")) return false;
            const auto output=mgmt.RunCommandOutput("ovs-ofctl -O OpenFlow14 dump-flows "+Quote(br)+" cookie=0x534f4e41524e/0xffffffffffffffff");
            if(output.empty() || (output.find("actions=drop")!=std::string::npos)!=isolate) {detail="OVS node quarantine readback failed";return false;}
        }
        detail=isolate?"OVS data bridge quarantine verified":"OVS node quarantine released and verified";return true;
    }
    if(!mgmt.RunCommand("command -v nft >/dev/null 2>&1")) {
        if(!mgmt.RunCommand("command -v iptables-restore >/dev/null 2>&1")) {
            detail="IPv4 node quarantine requires nftables, iptables or OVS";return false;
        }
        if(!isolate) {
            const bool ok=mgmt.RunCommand("for hook in INPUT FORWARD OUTPUT; do while iptables -w -C \"$hook\" -j SONAR_NODE_QUARANTINE 2>/dev/null; do iptables -w -D \"$hook\" -j SONAR_NODE_QUARANTINE || exit 1; done; done; if iptables -w -S SONAR_NODE_QUARANTINE >/dev/null 2>&1; then iptables -w -F SONAR_NODE_QUARANTINE && iptables -w -X SONAR_NODE_QUARANTINE; fi");
            detail=ok?"Owned IPv4 node quarantine rules removed":"IPv4 node quarantine release failed";return ok;
        }
        const auto route=nlohmann::json::parse(mgmt.RunCommandOutput("ip -j route get "+Quote(config.GetServerIpv4())),nullptr,false);
        if(!route.is_array() || route.empty() || !route[0].contains("dev")) {detail="Management route discovery failed";return false;}
        std::vector<std::string> prefixes;
        std::istringstream in(config.GetManagementPrefixes());std::string prefix;
        while(std::getline(in,prefix,',')) {
            prefix=Trim(prefix);std::uint32_t address;int length;
            if(!Cidr(prefix,address,length)) {detail="Invalid management prefix";return false;}
            prefixes.push_back(prefix);
        }
        std::uint32_t address;int length;
        if(prefixes.empty() || !Cidr(config.GetServerIpv4()+"/32",address,length)) {detail="Management destination is required";return false;}
        prefixes.push_back(config.GetServerIpv4()+"/32");
        std::string body="*filter\n:SONAR_NODE_QUARANTINE - [0:0]\n-F SONAR_NODE_QUARANTINE\n";
        body+="-A SONAR_NODE_QUARANTINE -i lo -j RETURN\n-A SONAR_NODE_QUARANTINE -o lo -j RETURN\n";
        for(const auto& cidr:prefixes)
            body+="-A SONAR_NODE_QUARANTINE -s "+cidr+" -j RETURN\n-A SONAR_NODE_QUARANTINE -d "+cidr+" -j RETURN\n";
        const auto gateway=route[0].value("gateway","");
        if(!gateway.empty() && Cidr(gateway+"/32",address,length))
            body+="-A SONAR_NODE_QUARANTINE -s "+gateway+" -p tcp --dport 22 -j RETURN\n-A SONAR_NODE_QUARANTINE -d "+gateway+" -p tcp --sport 22 -j RETURN\n";
        body+="-A SONAR_NODE_QUARANTINE -j DROP\nCOMMIT\n";
        bool ok=mgmt.RunCommand("printf %s "+Quote(body)+" | iptables-restore -w --noflush");
        for(const std::string hook:{"INPUT","FORWARD","OUTPUT"}) {
            std::istringstream rules(mgmt.RunCommandOutput("iptables -w -S "+hook));
            std::string line,first;
            while(std::getline(rules,line)) if(line.starts_with("-A ")) {first=line;break;}
            if(first!="-A "+hook+" -j SONAR_NODE_QUARANTINE")
                ok=ok && mgmt.RunCommand("iptables -w -I "+hook+" 1 -j SONAR_NODE_QUARANTINE");
        }
        ok=ok && mgmt.RunCommand("iptables -w -C SONAR_NODE_QUARANTINE -j DROP");
        detail=ok?"IPv4 node quarantine verified; routes, NAT and management preserved":"IPv4 node quarantine apply or readback failed";
        return ok;
    }
    if(!isolate) {
        const bool ok=mgmt.RunCommand("if nft list table inet sonar_node_quarantine >/dev/null 2>&1; then nft delete table inet sonar_node_quarantine; fi");
        detail=ok?"Node quarantine released":"Node quarantine release failed";return ok;
    }
    const auto routes=nlohmann::json::parse(mgmt.RunCommandOutput("ip -j route get "+Quote(config.GetServerIpv4())),nullptr,false);
    if(!routes.is_array() || routes.empty() || !routes[0].contains("dev")) {detail="Management route discovery failed";return false;}
    std::vector<std::string> prefixes;
    std::istringstream in(config.GetManagementPrefixes());std::string prefix;
    while(std::getline(in,prefix,',')) {prefix=Trim(prefix);std::uint32_t address;int length;
        if(!Cidr(prefix,address,length)) {detail="Invalid management prefix";return false;}prefixes.push_back(prefix);}
    std::uint32_t server;int length;
    if(prefixes.empty() || !Cidr(config.GetServerIpv4()+"/32",server,length)) {detail="Management destination is required";return false;}
    prefixes.push_back(config.GetServerIpv4()+"/32");
    const std::string gateway=routes[0].value("gateway","");
    std::string body="add table inet sonar_node_quarantine\n";
    for(const std::string chain:{"input","forward","output"}) {
        body+="add chain inet sonar_node_quarantine "+chain+" { type filter hook "+chain+" priority -210; policy accept; }\nflush chain inet sonar_node_quarantine "+chain+"\n";
        if(chain=="input") body+="add rule inet sonar_node_quarantine input iifname lo accept\n";
        if(chain=="output") body+="add rule inet sonar_node_quarantine output oifname lo accept\n";
        for(const auto& cidr:prefixes) {
            if(chain!="output") body+="add rule inet sonar_node_quarantine "+chain+" ip saddr "+cidr+" accept\n";
            if(chain!="input") body+="add rule inet sonar_node_quarantine "+chain+" ip daddr "+cidr+" accept\n";
        }
        // Lab3 SSH arrives through the firewall jump host on the data gateway.
        if(!gateway.empty() && Cidr(gateway+"/32",server,length)) {
            if(chain=="input") body+="add rule inet sonar_node_quarantine input ip saddr "+gateway+" tcp dport 22 accept\n";
            if(chain=="output") body+="add rule inet sonar_node_quarantine output ip daddr "+gateway+" tcp sport 22 accept\n";
        }
        body+="add rule inet sonar_node_quarantine "+chain+" counter drop\n";
    }
    const bool ok=mgmt.RunCommand("printf %s "+Quote(body)+" | nft -f -")
            && mgmt.RunCommand("nft list table inet sonar_node_quarantine >/dev/null 2>&1");
    detail=ok?"Node data quarantine installed; management exceptions preserved":"Node quarantine transaction or readback failed";
    return ok;
}
}

bool ReconcilePlan(const ProberConfig& config,ManagementService& mgmt,const nlohmann::json& plan) {
    if(!plan.is_object() || !plan.contains("revision") || !plan["revision"].is_string()
            || !plan.contains("targets") || !plan["targets"].is_array()) return false;
    const auto targets=plan["targets"];
    const auto node=plan.value("node",nlohmann::json::object());
    const bool isolated=node.value("action","")==kIsolate;
    std::string product=config.GetProductName();
    std::transform(product.begin(),product.end(),product.begin(),[](unsigned char c){return std::tolower(c);});
    const bool ios=product.find("cisco")!=std::string::npos || product.find("8000")!=std::string::npos;
    const bool eos=product.find("arista")!=std::string::npos || product.find("veos")!=std::string::npos;
    std::string detail;
    bool ok;
    if(ios || eos) {
        ok=interface_quarantine::Reconcile(mgmt,ios ? interface_quarantine::Platform::Ios : interface_quarantine::Platform::Eos,runtime_environment::DataDirectory(),targets,isolated,
                config.GetManagementPrefixes(),config.GetServerIpv4(),detail);
    } else {
        ok=ApplySubnetTargets(targets,config.GetManagementPrefixes(),config.GetServerIpv4(),
                [&](const std::string& cmd){return mgmt.RunCommand(cmd);},
                [&](const std::string& cmd){return mgmt.RunCommandOutput(cmd);},detail);
        if(ok && !node.empty()) ok=ReconcileNodeFirewall(isolated,config,mgmt,detail);
    }
    const std::string agent=config.GetAgentId().empty()?config.GetAgentName():config.GetAgentId();
    auto ack=nlohmann::json{{"action","subnet-quarantine"},{"revision",plan["revision"]},
        {"targets",targets},{"ok",ok},{"detail",detail}};
    mgmt.SendEnvelope(envelope::Make(envelope::kAck,agent,envelope::DeviceTypeToString(config.GetDeviceType()),
        envelope::NextCorrelationId(),ack));
    if(!node.empty()) {
        ack["action"]=node.value("action","");ack["request_id"]=node.value("request_id","");
        mgmt.SendEnvelope(envelope::Make(envelope::kAck,agent,envelope::DeviceTypeToString(config.GetDeviceType()),
            envelope::NextCorrelationId(),ack));
    }
    return ok;
}
} // namespace quarantine
