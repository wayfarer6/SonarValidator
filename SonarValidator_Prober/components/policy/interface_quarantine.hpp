#ifndef SONAR_INTERFACE_QUARANTINE_HPP_
#define SONAR_INTERFACE_QUARANTINE_HPP_
#include <arpa/inet.h>
#include <algorithm>
#include <vector>
#include "utils/atomic_json_file.hpp"
#include <filesystem>
#include <fstream>
#include <map>
#include <set>
#include <sstream>
#include <string>
#include <sys/stat.h>
#include <nlohmann/json.hpp>
#include "components/device/command_executor.hpp"
#include "components/terminal/arista_cli.hpp"
#include "components/terminal/ios_cli.hpp"

namespace interface_quarantine {
using Json = nlohmann::json;
struct Port { std::string address; bool up; bool protected_path; };
enum class Platform { Ios, Eos };
inline bool Contains(const std::string& cidr, const std::string& address) {
    const auto slash = cidr.find('/');
    if (slash == std::string::npos) return false;
    in_addr net{}, host{};
    const auto bits = cidr.substr(slash+1);
    if (bits.empty() || bits.size()>2 || !std::all_of(bits.begin(),bits.end(),::isdigit)) return false;
    const int n=std::stoi(bits); if(n<0 || n>32) return false;
    if(inet_pton(AF_INET,cidr.substr(0,slash).c_str(),&net)!=1 || inet_pton(AF_INET,address.c_str(),&host)!=1) return false;
    const auto mask=n==0?0u:0xffffffffu<<(32-n);
    return (ntohl(net.s_addr)&mask)==(ntohl(host.s_addr)&mask);
}
inline bool Protected(const std::string& address, const std::string& management, const std::string& server) {
    if (address==server) return true;
    std::istringstream in(management); std::string cidr;
    while(std::getline(in,cidr,',')) {
        const auto first=cidr.find_first_not_of(" \t");
        if(first!=std::string::npos && Contains(cidr.substr(first,cidr.find_last_not_of(" \t")-first+1),address)) return true;
    }
    return false;
}
inline std::map<std::string,Port> IosPorts(const std::string& output,const std::string& management,const std::string& server) {
    std::map<std::string,Port> ports; std::istringstream lines(output); std::string line;
    while(std::getline(lines,line)) {
        std::istringstream fields(line); std::string name,address,ok,method,status;
        if(!(fields>>name>>address>>ok>>method>>status) || (ok!="YES" && ok!="NO") || !arista_cli::ValidName(name)) continue;
        const bool protect=name.starts_with("VirtualPortGroup") || name.starts_with("Loopback") || Protected(address,management,server);
        ports[name]={address,status!="administratively",protect};
    }
    return ports;
}
inline bool Reconcile(DeviceCommandExecutor& device, Platform platform, const std::string& directory,
                      const Json& targets,bool isolateNode,const std::string& management,
                      const std::string& server,std::string& detail) {
    const bool ios = platform == Platform::Ios;
    const auto path=directory+"/quarantine_interfaces.json";
    auto discover=[&]() {
        if(ios) return IosPorts(device.ExecuteIosCli({"show ip interface brief","show clock"}),management,server);
        std::map<std::string,Port> ports;
        const auto j=Json::parse(device.QueryAristaCli("show interfaces status | json"),nullptr,false);
        if(j.is_object() && j.contains("interfaceStatuses")) for(auto it=j["interfaceStatuses"].begin();it!=j["interfaceStatuses"].end();++it) {
            if(!arista_cli::ValidName(it.key())) continue;
            ports[it.key()]={"",it.value().value("linkStatus","")!="disabled",!it.key().starts_with("Ethernet")};
        }
        return ports;
    };
    auto ports=discover();
    if(ports.empty()) {detail="Native device interface discovery failed";return false;}
    if(ios && std::none_of(ports.begin(),ports.end(),[&](const auto& entry){
            return Protected(entry.second.address,management,server);
        })) {detail="IOS management interface cannot be identified; no ports changed";return false;}
    std::set<std::string> desired;
    if(isolateNode) for(const auto& [name,port]:ports) if(!port.protected_path) desired.insert(name);
    if(!targets.is_array()) {detail="Invalid quarantine target list";return false;}
    Json vlanInfo;
    if(!ios && !targets.empty()) vlanInfo=Json::parse(device.QueryAristaCli("show vlan | json"),nullptr,false);
    for(const auto& target:targets) {
        if(!target.is_object() || !target.contains("cidr") || !target["cidr"].is_string()) return false;
        bool matched=false;
        for(const auto& [name,port]:ports) {
            bool match=ios && Contains(target["cidr"],port.address);
            if(!ios && target.contains("vlan_id") && target["vlan_id"].is_number_integer() && vlanInfo.is_object()) {
                const auto key=std::to_string(target["vlan_id"].get<int>());
                match=vlanInfo.contains("vlans") && vlanInfo["vlans"].contains(key)
                        && vlanInfo["vlans"][key].contains("interfaces") && vlanInfo["vlans"][key]["interfaces"].contains(name);
            }
            if(!match) continue;
            if(port.protected_path) {detail="GuestShell or management interface is protected";return false;}
            matched=true;desired.insert(name);
        }
        if(!matched) {detail="No data interface matches the selected subnet or VLAN";return false;}
    }
    if(isolateNode && desired.empty()) {detail="No isolatable data interface found";return false;}
    Json original=Json::object();
    if(std::filesystem::exists(path)) {
        std::ifstream in(path); original=Json::parse(in,nullptr,false);
        if(!original.is_object()) {detail="Saved quarantine interface state is invalid";return false;}
    }
    for(auto it=original.begin();it!=original.end();++it)
        if(!arista_cli::ValidName(it.key()) || !it.value().is_boolean() || !ports.count(it.key()) || ports.at(it.key()).protected_path) {
            detail="Saved interface state cannot be restored safely";return false;
        }
    for(const auto& name:desired) if(!original.contains(name)) original[name]=ports.at(name).up;
    // Persist the exact original admin states before changing any device interface.
    std::filesystem::create_directories(directory);
    if(!file_storage::WriteJsonAtomically(path,original)) {
        detail="Cannot persist original interface states";return false;
    }
    std::vector<std::string> commands{"configure terminal"};
    std::map<std::string,bool> expected;
    for(auto it=original.begin();it!=original.end();++it) {
        const bool up=desired.count(it.key())?false:it.value().get<bool>();expected[it.key()]=up;
        if(ports.at(it.key()).up!=up) {
            commands.push_back("interface "+it.key());commands.push_back(up?"no shutdown":"shutdown");
        }
    }
    commands.push_back("end");
    if(commands.size()>2) {
        const auto output=ios?device.ExecuteIosCli(commands):device.RunCommandOutput(arista_cli::BuildCommand(commands));
        if(ios_cli::CheckOutput(output).failed) {detail="Native device rejected interface change";return false;}
    }
    ports=discover();
    for(const auto& [name,up]:expected) if(!ports.count(name) || ports.at(name).up!=up) {
        detail="Interface admin state readback does not match requested state";return false;
    }
    for(auto it=original.begin();it!=original.end();) if(!desired.count(it.key())) it=original.erase(it);else ++it;
    if(!file_storage::WriteJsonAtomically(path,original)) {
        detail="Cannot save verified restoration state";return false;
    }
    detail=desired.empty()?"Original device interface states restored and verified":"Device data interface isolation verified";
    return true;
}
}
#endif
