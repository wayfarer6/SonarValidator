#include "components/advice/advice_executor.hpp"
#include <algorithm>
#include <arpa/inet.h>
#include <cctype>
#include <iomanip>
#include <openssl/sha.h>
#include <regex>
#include <set>
#include <sstream>
#include <stdexcept>

namespace advice {
namespace {
std::string Text(const Json& a,const std::string& key,const char* pattern) {
    if(!a.contains(key)||!a[key].is_string()) throw std::invalid_argument("Invalid string argument: "+key);
    auto s=a[key].get<std::string>();
    if(s.size()>128||!std::regex_match(s,std::regex(pattern)))throw std::invalid_argument("Invalid string argument: "+key);
    return s;
}
int Integer(const Json& a,const std::string& key,int min,int max) {
    if(!a.contains(key)||!a[key].is_number_integer())throw std::invalid_argument("Invalid integer: "+key);
    auto n=a[key].get<std::int64_t>();if(n<min||n>max)throw std::invalid_argument("Integer out of range: "+key);return static_cast<int>(n);
}
void Keys(const Json& a,std::set<std::string> allowed) {
    if(!a.is_object())throw std::invalid_argument("Arguments must be an object");
    for(auto it=a.begin();it!=a.end();++it)if(!allowed.count(it.key()))throw std::invalid_argument("Unknown argument: "+it.key());
}
struct Network { std::uint32_t address; int prefix; };
Network ParseNetwork(const std::string& text) {
    if(!std::regex_match(text,std::regex(R"([0-9]{1,3}(\.[0-9]{1,3}){3}/[0-9]{1,2})")))throw std::invalid_argument("Invalid IPv4 CIDR");
    auto slash=text.find('/');int prefix=std::stoi(text.substr(slash+1));in_addr address{};
    if(prefix>32||inet_pton(AF_INET,text.substr(0,slash).c_str(),&address)!=1)throw std::invalid_argument("Invalid IPv4 CIDR");
    auto mask=prefix==0?0u:0xffffffffu<<(32-prefix);return {ntohl(address.s_addr)&mask,prefix};
}
std::string Cidr(const Network& n) {
    return std::to_string(n.address>>24)+"."+std::to_string((n.address>>16)&255)+"."+std::to_string((n.address>>8)&255)+"."+std::to_string(n.address&255)+"/"+std::to_string(n.prefix);
}
bool Overlap(const Network& a,const Network& b) {int prefix=std::min(a.prefix,b.prefix);auto mask=prefix==0?0u:0xffffffffu<<(32-prefix);return(a.address&mask)==(b.address&mask);}
std::string Trim(std::string s) {auto start=s.find_first_not_of(" \r\n\t");return start==std::string::npos?"":s.substr(start,s.find_last_not_of(" \r\n\t")-start+1);}
bool Ok(const command_runner::Result& r) {return r.completed&&r.exit_code==0;}
Json Result(const std::string& state,bool verified,bool changed,const std::string& output,const std::string& detail) {
    std::istringstream lines(output);std::string line,clean;
    while(std::getline(lines,line)) {auto lower=line;std::transform(lower.begin(),lower.end(),lower.begin(),[](unsigned char c){return std::tolower(c);});
        clean+=(lower.find("password")!=std::string::npos||lower.find("secret")!=std::string::npos||lower.find("community")!=std::string::npos)?"[credential line redacted]\n":line+"\n";}
    if(clean.size()>18000)clean.resize(18000);
    return {{"status",state},{"verified",verified},{"changed",changed},{"output",clean},{"detail",detail}};
}
Channel DeviceChannel(const Plan& p,bool cached=false) {return p.profile=="ios"?(cached?Channel::IosSnapshot:Channel::Ios):p.profile=="eos"?Channel::Eos:Channel::Shell;}
bool Description(const std::string& output,const std::string& iface,const std::string& description) {
    bool found=false;std::istringstream lines(output);std::string line;
    while(std::getline(lines,line)){line=Trim(line);if(line=="interface "+iface)found=true;if(found&&line=="description "+description)return true;}return false;
}
bool Vlan(const std::string& output,int id,const std::string& name) {
    std::istringstream lines(output);std::string line;
    while(std::getline(lines,line)){std::istringstream fields(line);int actual;std::string actual_name;if(fields>>actual>>actual_name&&actual==id&&actual_name==name)return true;}return false;
}
std::string RightCidr(const Json& node) {
    if(node.is_string()) {auto s=node.get<std::string>();return Cidr(ParseNetwork(s.find('/')==std::string::npos?s+"/32":s));}
    if(node.is_object()&&node.contains("prefix")) {auto p=node["prefix"];return Cidr(ParseNetwork(p.at("addr").get<std::string>()+"/"+std::to_string(p.at("len").get<int>())));}
    return {};
}
bool VerifyNft(const Plan& p,const std::string& output) {
    try {
        auto document=Json::parse(output);auto table=p.verification[0].substr(std::string("nft -j list table inet ").size());
        bool chain=false,rule=false;int chains=0,rules=0;
        for(const auto& object:document.at("nftables")) {
            if(object.contains("chain")){++chains;const auto& c=object["chain"];if(c.value("family","")=="inet"&&c.value("table","")==table&&c.value("name","")=="forward"&&c.value("hook","")=="forward"&&c.value("prio",0)==-150&&c.value("policy","")=="accept")chain=true;}
            if(!object.contains("rule"))continue;++rules;const auto& r=object["rule"];
            if(r.value("table","")!=table||r.value("chain","")!="forward"||r.value("comment","")!="sonar-ai-managed")continue;
            bool src=false,dst=false,drop=false,protocol=p.arguments.at("protocol").get<std::string>()=="any",port=p.arguments["port"]==0;
            int matches=0;
            for(const auto& e:r.at("expr")) {
                if(!e.contains("match")&&!e.contains("drop")&&!e.contains("counter"))return false;
                if(e.contains("drop"))drop=true;
                if(!e.contains("match"))continue;++matches;const auto& m=e["match"];if(m.value("op","")!="==")continue;
                const auto& left=m.at("left");const auto& right=m.at("right");
                if(left.contains("payload")){const auto& payload=left["payload"];auto field=payload.value("field","");auto proto=payload.value("protocol","");
                    if(proto=="ip"&&field=="saddr")src=RightCidr(right)==p.arguments.at("source").get<std::string>();
                    if(proto=="ip"&&field=="daddr")dst=RightCidr(right)==p.arguments.at("destination").get<std::string>();
                    if(proto==p.arguments.at("protocol").get<std::string>()&&field=="dport"&&right==p.arguments.at("port").get<int>()){port=true;protocol=true;}}
                if(left.contains("meta")&&left["meta"].value("key","")=="l4proto"&&right==p.arguments.at("protocol").get<std::string>())protocol=true;
            }
            const int expected_matches=p.arguments.at("protocol")=="any"?2:3;
            if(src&&dst&&drop&&protocol&&port&&matches==expected_matches)rule=true;
        }
        return chain&&rule&&chains==1&&rules==1;
    }catch(...){return false;}
}
}
std::string Hash(const std::string& text) {unsigned char digest[SHA256_DIGEST_LENGTH];SHA256(reinterpret_cast<const unsigned char*>(text.data()),text.size(),digest);std::ostringstream out;for(auto c:digest)out<<std::hex<<std::setw(2)<<std::setfill('0')<<static_cast<int>(c);return out.str();}
std::string Profile(std::string p) {
    std::transform(p.begin(),p.end(),p.begin(),[](unsigned char c){return std::tolower(c);});
    for(const char* key:{"cisco","ios","catalyst"})if(p.find(key)!=std::string::npos)return "ios";
    for(const char* key:{"arista","veos"})if(p.find(key)!=std::string::npos)return "eos";
    for(const char* key:{"openvswitch","open vswitch","ovs"})if(p.find(key)!=std::string::npos)return "ovs";
    for(const char* key:{"ubuntu","linux","nft","frr"})if(p.find(key)!=std::string::npos)return "linux";
    return "unsupported";
}
std::vector<std::string> Operations(const std::string& product,bool nft) {
    auto p=Profile(product);if(p=="unsupported")return {};
    std::vector<std::string> result{"inspect_interfaces","inspect_routes"};
    if(p=="ios"||p=="eos")result.push_back("set_interface_description");
    if(p=="eos"||p=="ovs")result.push_back("inspect_vlans");
    if(p=="eos")result.push_back("ensure_vlan");if(p=="linux"&&nft)result.push_back("block_forward_ipv4");return result;
}
Plan Compile(const std::string& operation,const std::string& product,Json a,const std::string& prefixes,const std::string& server) {
    auto profile=Profile(product);if(profile=="unsupported")throw std::invalid_argument("Unsupported device profile");
    Plan p{operation,profile,a,{},{},false};
    if(operation=="inspect_interfaces"||operation=="inspect_routes") {
        Keys(a,{});p.commands={operation=="inspect_interfaces"?(profile=="ios"?"show ip interface brief":profile=="eos"?"show interfaces status":"ip -j address show"):(profile=="ios"||profile=="eos"?"show ip route":"ip -j route show")};
    }else if(operation=="inspect_vlans"){
        Keys(a,{});if(profile!="eos"&&profile!="ovs")throw std::invalid_argument("VLAN inspection unsupported");p.commands={profile=="eos"?"show vlan":"ovs-vsctl --format=json --columns=name,tag,trunks list Port"};
    }else if(operation=="set_interface_description"){
        Keys(a,{"interface","description"});if(profile!="ios"&&profile!="eos")throw std::invalid_argument("Description update unsupported");
        auto iface=Text(a,"interface","[A-Za-z][A-Za-z0-9./:-]{0,63}"),description=Text(a,"description","[A-Za-z0-9][A-Za-z0-9 _.,:/()=-]{0,79}");
        p.commands={"configure terminal","interface "+iface,"description "+description,"end"};p.verification={"show running-config interface "+iface};p.write=true;
    }else if(operation=="ensure_vlan"){
        Keys(a,{"vlan_id","name"});if(profile!="eos")throw std::invalid_argument("VLAN creation unsupported");
        int id=Integer(a,"vlan_id",2,4094);auto name=Text(a,"name","[A-Za-z0-9][A-Za-z0-9_.-]{0,31}");p.arguments["vlan_id"]=id;
        p.commands={"configure terminal","vlan "+std::to_string(id),"name "+name,"end"};p.verification={"show vlan id "+std::to_string(id)};p.write=true;
    }else if(operation=="block_forward_ipv4"){
        Keys(a,{"source","destination","protocol","port"});if(profile!="linux")throw std::invalid_argument("nftables is only supported in Linux data-plane profiles");
        auto src=ParseNetwork(Text(a,"source",R"([0-9./]{1,20})")),dst=ParseNetwork(Text(a,"destination",R"([0-9./]{1,20})"));
        std::istringstream protected_prefixes(prefixes);std::string prefix;
        while(std::getline(protected_prefixes,prefix,','))if(!Trim(prefix).empty()){auto management=ParseNetwork(Trim(prefix));if(Overlap(src,management)||Overlap(dst,management))throw std::invalid_argument("Management network is protected");}
        if(!server.empty()){auto management=ParseNetwork(server+"/32");if(Overlap(src,management)||Overlap(dst,management))throw std::invalid_argument("Backend address is protected");}
        auto protocol=Text(a,"protocol","any|tcp|udp|icmp");int port=Integer(a,"port",0,65535);
        if(port>0&&protocol!="tcp"&&protocol!="udp")throw std::invalid_argument("Only TCP/UDP have transport ports");
        auto source=Cidr(src),destination=Cidr(dst);p.arguments["source"]=source;p.arguments["destination"]=destination;p.arguments["port"]=port;
        auto table="sonar_ai_"+Hash(source+"|"+destination+"|"+protocol+"|"+std::to_string(port)).substr(0,12);
        auto match="ip saddr "+source+" ip daddr "+destination;
        if(port>0)match+=" "+protocol+" dport "+std::to_string(port);else if(protocol!="any")match+=" meta l4proto "+protocol;
        p.commands={"table inet "+table+" { chain forward { type filter hook forward priority -150; policy accept; "+match+" counter drop comment \"sonar-ai-managed\"; } }"};p.verification={"nft -j list table inet "+table};p.write=true;
    }else throw std::invalid_argument("Unsupported action operation");
    return p;
}
Json Execute(const Plan& p,const Runner& run) {
    auto channel=DeviceChannel(p);if(!p.write){auto r=run(DeviceChannel(p,true),p.commands);return Result(Ok(r)?"SUCCEEDED":"FAILED",Ok(r),false,r.output,Ok(r)?"Diagnostic command completed; this does not prove policy compliance.":"Diagnostic command failed or timed out.");}
    auto query=p.operation=="block_forward_ipv4"?Channel::Shell:channel;
    auto before=run(query,p.verification);
    if(p.operation=="set_interface_description"){
        auto iface=p.arguments.at("interface").get<std::string>(),description=p.arguments.at("description").get<std::string>();
        bool target=false;std::istringstream lines(before.output);std::string line;while(std::getline(lines,line))if(Trim(line)=="interface "+iface)target=true;
        if(!Ok(before)||!target)return Result("REJECTED",false,false,before.output,"Cannot freshly read the target interface; no configuration was sent.");
        if(Description(before.output,iface,description))return Result("SUCCEEDED",true,false,before.output,"The requested description is already present.");
    }else if(p.operation=="ensure_vlan"){
        if(Ok(before)&&Vlan(before.output,p.arguments.at("vlan_id").get<int>(),p.arguments.at("name").get<std::string>()))return Result("SUCCEEDED",true,false,before.output,"The requested VLAN is already present.");
        auto lower=before.output;std::transform(lower.begin(),lower.end(),lower.begin(),[](unsigned char c){return std::tolower(c);});
        if(lower.find("not found")==std::string::npos&&lower.find("does not exist")==std::string::npos)return Result("REJECTED",false,false,before.output,"VLAN exists with different settings or its absence could not be verified.");
    }else {
        if(Ok(before))return VerifyNft(p,before.output)?Result("SUCCEEDED",true,false,before.output,"The requested forwarding block is already present."):Result("REJECTED",false,false,before.output,"Existing table does not match the managed plan; it was preserved.");
        auto lower=before.output;std::transform(lower.begin(),lower.end(),lower.begin(),[](unsigned char c){return std::tolower(c);});
        if(lower.find("no such file")==std::string::npos&&lower.find("not found")==std::string::npos)return Result("REJECTED",false,false,before.output,"Cannot confirm the managed nftables table is absent.");
    }
    auto write=run(p.operation=="block_forward_ipv4"?Channel::Nft:channel,p.commands);
    auto after=run(query,p.verification);bool verified=false;
    if(Ok(after))verified=p.operation=="set_interface_description"?Description(after.output,p.arguments.at("interface").get<std::string>(),p.arguments.at("description").get<std::string>()):p.operation=="ensure_vlan"?Vlan(after.output,p.arguments.at("vlan_id").get<int>(),p.arguments.at("name").get<std::string>()):VerifyNft(p,after.output);
    return Result(verified?"SUCCEEDED":"UNCONFIRMED",verified,true,write.output+"\n--- verification ---\n"+after.output,
        verified?"Runtime configuration was read back and matched. Startup configuration persistence is a separate action.":"Configuration may have changed, but fresh read-back did not confirm it. Do not automatically retry.");
}
}
