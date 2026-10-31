#include "components/terminal/ios_snapshot.hpp"
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <utime.h>
namespace fs = std::filesystem;
void Check(bool value,const char* message) { if(!value) throw std::runtime_error(message); }
void Write(const fs::path& path,const std::string& text,std::time_t mtime)
{
    std::ofstream(path,std::ios::binary)<<text;
    utimbuf times{mtime,mtime};
    Check(utime(path.c_str(),&times)==0,"timestamp");
}
int main()
{
    char name[]="/tmp/sonar-ios-snapshot-XXXXXX";
    Check(mkdtemp(name)!=nullptr,"temporary directory");
    fs::path root(name);fs::create_directory(root/"ios_cli");
    const std::string header="Interface\tIP-Address OK? Method Status Protocol\n";
    const std::string table=header+"GigabitEthernet1 192.168.148.181 YES NVRAM up up\n";
    const auto file=root/"ios_cli/show_ip_interface_brief.out";
    const auto now=std::time(nullptr);
    try
    {
        Write(file,table,now);
        Check(ios_snapshot::Read(root,"show ip interface brief",now).starts_with(header),"IOS header was dropped");
        Check(ios_snapshot::Read(root," SHOW IP INTERFACE BRIEF ",now).starts_with(header),"canonical command lookup");
        Check(!ios_snapshot::Read(root,"show ip interface brief",now+300).empty(),"TTL boundary");
        Check(ios_snapshot::Read(root,"show ip interface brief",now+301).empty(),"stale file accepted");
        Check(ios_snapshot::Read(root,"show ip interface brief",now-1).empty(),"future timestamp accepted");
        Check(ios_snapshot::Read(root,"show ip interface brief ; configure terminal",now).empty(),"configuration obtained cached output");
        int calls=0;
        auto result=ios_snapshot::ReadOrExecute(root,"show ip interface brief",[&]{++calls;return std::string("direct\n");});
        Check(calls==0 && result.text.starts_with(header),"fresh EEM file did not avoid direct command");
        result=ios_snapshot::ReadOrExecute(root,"configure terminal",[&]{++calls;return std::string("direct\n");});
        Check(calls==1 && result.text=="direct\n","configuration must use direct execution");
        for(const auto& bad:std::vector<std::string>{"",table.substr(0,table.size()-1),"wrong header\n",table+"% Authorization failed.\n",table+"Unexpected Error\n",table+std::string(1,'\0')+"\n"})
        {
            Write(file,bad,now);
            Check(ios_snapshot::Read(root,"show ip interface brief",now).empty(),"invalid snapshot accepted");
        }
        Write(file,table,now);fs::resize_file(file,ios_snapshot::kMaxBytes+1);
        Check(ios_snapshot::Read(root,"show ip interface brief",now).empty(),"oversized file accepted");
        fs::remove(file);fs::create_symlink(root/"missing",file);
        Check(ios_snapshot::Read(root,"show ip interface brief",now).empty(),"symlink accepted");fs::remove(file);
        Check(mkfifo(file.c_str(),0600)==0,"FIFO setup");
        Check(ios_snapshot::Read(root,"show ip interface brief",now).empty(),"FIFO accepted");fs::remove(file);
        result=ios_snapshot::ReadOrExecute(root,"show ip interface brief",[&]{++calls;return std::string("Unexpected Error\n");});
        Check(result.failed && result.session_unavailable && result.text.empty(),"missing snapshot masks direct error");
        Write(root/"ios_cli/show_ip_route.out","Codes: L - local, C - connected\nC 10.10.20.0/24 is directly connected, GigabitEthernet2\n",now);
        Write(root/"ios_cli/show_ip_arp.out","Protocol Address Age Hardware Type Interface\nInternet 10.10.20.1 - aabb.ccdd.eeff ARPA Gi2\n",now);
        Check(ios_snapshot::Read(root,"show ip route",now).starts_with("Codes:"),"route header missing");
        Check(ios_snapshot::Read(root,"show ip arp",now).starts_with("Protocol"),"ARP header missing");
        fs::remove_all(root);std::cout<<"[ok] EEM headers/preference, TTL/future, invalid/truncated/oversized files, FIFO/symlink, config exclusion\n";return 0;
    }
    catch(const std::exception& error) {fs::remove_all(root);std::cerr<<"[FAIL] "<<error.what()<<'\n';return 1;}
}
