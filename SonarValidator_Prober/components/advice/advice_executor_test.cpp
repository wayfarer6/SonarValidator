#include "components/advice/advice_executor.hpp"
#include <fstream>
#include <iostream>
#include <stdexcept>
using advice::Json;
void Check(bool value,const char* label){if(!value)throw std::runtime_error(label);std::cout<<"[ok] "<<label<<'\n';}
int main(){
    std::ifstream file(SONAR_ADVICE_CONTRACT_PATH);Json fixtures;file>>fixtures;
    for(const auto& row:fixtures){auto p=advice::Compile(row["operation"],row["product"],row["arguments"],"198.18.0.0/24","198.18.0.1");Check(Json(p.commands)==row["commands"]&&Json(p.verification)==row["verification"],"shared backend/prober preview contract");}
    for(auto args:std::vector<Json>{{{"interface","Ethernet1; reload"},{"description","x"}},{{"interface","Ethernet1"},{"description","x\nshutdown"}}}){
        bool rejected=false;try{advice::Compile("set_interface_description","Arista",args,"","");}catch(...){rejected=true;}Check(rejected,"command injection is rejected");}
    auto nftArgs=fixtures.back()["arguments"];
    for(const char* product:{"Cisco IOS XE","Arista"}){bool rejected=false;try{advice::Compile("block_forward_ipv4",product,nftArgs,"","");}catch(...){rejected=true;}Check(rejected,"container namespace cannot masquerade as network device firewall");}
    auto protectedArgs=nftArgs;protectedArgs["destination"]="192.168.148.0/24";bool protected_target=false;try{advice::Compile("block_forward_ipv4","Linux nftables",protectedArgs,"192.168.144.0/20","192.168.148.180");}catch(...){protected_target=true;}Check(protected_target,"local management protection is independent of server validation");
    auto description=advice::Compile("set_interface_description","Cisco IOS XE",{{"interface","GigabitEthernet2"},{"description","Lab Transit"}},"","");
    int calls=0;auto success=advice::Execute(description,[&](auto,const auto&){++calls;return command_runner::Result{calls==1?"interface GigabitEthernet2\n description Before\n":calls==3?"interface GigabitEthernet2\n description Lab Transit\n":"",0,true};});Check(success["status"]=="SUCCEEDED"&&success["verified"]&&calls==3,"configuration success requires fresh read-back");
    calls=0;auto mismatch=advice::Execute(description,[&](auto,const auto&){++calls;return command_runner::Result{"interface GigabitEthernet2\n description Before\n",0,true};});Check(mismatch["status"]=="UNCONFIRMED"&&mismatch["changed"]==true,"successful CLI exit alone does not confirm application");
    calls=0;auto wrong=advice::Execute(description,[&](auto,const auto&){++calls;return command_runner::Result{"interface GigabitEthernet1\n",0,true};});Check(wrong["status"]=="REJECTED"&&calls==1,"missing target interface prevents all writes");
    auto vlan=advice::Compile("ensure_vlan","Arista",{{"vlan_id",3999},{"name","SONAR_TEST"}},"","");
    calls=0;auto existing=advice::Execute(vlan,[&](auto,const auto&){++calls;return command_runner::Result{"3999 SONAR_TEST active\n",0,true};});Check(existing["status"]=="SUCCEEDED"&&existing["changed"]==false&&calls==1,"matching VLAN is idempotent");
    calls=0;auto conflict=advice::Execute(vlan,[&](auto,const auto&){++calls;return command_runner::Result{"3999 OTHER active\n",0,true};});Check(conflict["status"]=="REJECTED"&&calls==1,"existing VLAN with other settings is preserved");
    auto nft=advice::Compile("block_forward_ipv4","Linux nftables",nftArgs,"198.18.0.0/24","198.18.0.1");
    auto table=nft.verification[0].substr(std::string("nft -j list table inet ").size());
    Json expression=Json::array();
    for(auto field:std::vector<std::pair<std::string,std::string>>{{"saddr","10.90.0.0"},{"daddr","10.91.0.0"}})expression.push_back({{"match",{{"op","=="},{"left",{{"payload",{{"protocol","ip"},{"field",field.first}}}}},{"right",{{"prefix",{{"addr",field.second},{"len",24}}}}}}}});
    expression.push_back({{"match",{{"op","=="},{"left",{{"payload",{{"protocol","tcp"},{"field","dport"}}}}},{"right",443}}}});expression.push_back({{"counter",{{"packets",0},{"bytes",0}}}});expression.push_back({{"drop",nullptr}});
    Json document={{"nftables",Json::array({{{"chain",{{"family","inet"},{"table",table},{"name","forward"},{"hook","forward"},{"prio",-150},{"policy","accept"}}}},{{"rule",{{"table",table},{"chain","forward"},{"comment","sonar-ai-managed"},{"expr",expression}}}}})}};
    auto nftExisting=advice::Execute(nft,[&](auto,const auto&){return command_runner::Result{document.dump(),0,true};});Check(nftExisting["status"]=="SUCCEEDED"&&nftExisting["changed"]==false,"nft rules are verified structurally");
    document["nftables"][1]["rule"]["expr"].push_back({{"accept",nullptr}});auto badNft=advice::Execute(nft,[&](auto,const auto&){return command_runner::Result{document.dump(),0,true};});Check(badNft["status"]=="REJECTED","extra verdicts invalidate managed-table verification");
}
