#include "components/policy/interface_quarantine.hpp"
#include <cassert>
#include <iostream>
#include <unistd.h>
struct Device final : DeviceCommandExecutor {
    std::map<std::string,bool> ports{{"GigabitEthernet1",true},{"GigabitEthernet2",true},{"GigabitEthernet3",false},{"VirtualPortGroup0",true}};
    int changes=0;bool reject=false,ignore=false,unknownManagement=false;
    bool RunCommand(const std::string&) override{return false;}
    std::string RunCommandOutput(const std::string&) override{return {};}
    std::string QueryAristaCli(const std::string&) override{return {};}
    std::string ExecuteIosCli(const std::vector<std::string>& commands) override {
        if(commands.front().starts_with("show")) {
            std::string out="Interface IP-Address OK? Method Status Protocol\n";
            for(const auto& [name,up]:ports) {
                const auto ip=name=="GigabitEthernet1"?(unknownManagement?"198.51.100.1":"192.168.148.181")
                    :name=="GigabitEthernet2"?"10.10.20.1":name=="GigabitEthernet3"?"192.168.42.254":"192.168.100.1";
                out+=name+" "+ip+" YES NVRAM "+(up?"up up":"administratively down down")+"\n";
            }return out;
        }
        if(reject)return "% Invalid input\n";
        std::string selected;
        for(const auto& c:commands) {
            if(c.starts_with("interface "))selected=c.substr(10);
            if(c=="shutdown" || c=="no shutdown"){++changes;if(!ignore)ports[selected]=c=="no shutdown";}
        }return "Router(config)#\nRouter#\n";
    }
};
int main(){
    char name[]="/tmp/sonar-interface-quarantine-XXXXXX";assert(mkdtemp(name));std::filesystem::path root(name);
    try{
        Device d;std::string detail;const nlohmann::json empty=nlohmann::json::array();
        const auto reconcile=[&](const auto& targets,bool node){return interface_quarantine::Reconcile(d,interface_quarantine::Platform::Ios,root.string(),targets,node,"192.168.144.0/20","192.168.148.180",detail);};
        assert(reconcile(empty,true));assert(d.ports.at("GigabitEthernet1"));assert(d.ports.at("VirtualPortGroup0"));assert(!d.ports.at("GigabitEthernet2"));
        const auto changes=d.changes;assert(reconcile(empty,true));assert(d.changes==changes);
        // A new controller instance reloads the journal and restores only ports it changed.
        assert(reconcile(empty,false));assert(d.ports.at("GigabitEthernet2"));assert(!d.ports.at("GigabitEthernet3"));
        const nlohmann::json data={{{"cidr","10.10.20.0/24"}}};
        assert(reconcile(data,false));assert(!d.ports.at("GigabitEthernet2"));assert(reconcile(empty,false));
        const auto before=d.changes;
        assert(!reconcile(nlohmann::json{{{"cidr","192.168.100.0/24"}}},false));assert(d.changes==before);
        assert(!reconcile(nlohmann::json{{{"cidr","192.168.144.0/20"}}},false));assert(d.changes==before);
        d.unknownManagement=true;assert(!reconcile(empty,true));assert(d.changes==before);d.unknownManagement=false;
        d.ignore=true;assert(!reconcile(data,false));assert(detail.find("readback")!=std::string::npos);d.ignore=false;
        d.reject=true;assert(!reconcile(data,false));d.reject=false;assert(reconcile(empty,false));
        std::filesystem::remove_all(root);std::cout<<"[ok] isolation/release, management and VPG protection, idempotence, pre-down preservation, readback failure, rejection, journal recovery\n";
    }catch(...){std::filesystem::remove_all(root);throw;}
}
