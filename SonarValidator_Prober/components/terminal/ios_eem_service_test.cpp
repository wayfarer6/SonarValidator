#include "module/management_module/management_service.hpp"
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <sys/stat.h>
namespace fs=std::filesystem;
void Check(bool value,const char* text){if(!value)throw std::runtime_error(text);}
void Write(const fs::path& p,const std::string& s){std::ofstream(p)<<s;}
std::string Read(const fs::path& p){std::ifstream f(p);return {std::istreambuf_iterator<char>(f),{}};}
int main()
{
 char dir[]="/tmp/sonar-ios-service-XXXXXX";Check(mkdtemp(dir)!=nullptr,"test directory");fs::path root(dir);
 try
 {
  fs::create_directory(root/"ios_cli");fs::create_directory(root/"bin");
  const auto calls=root/"calls",output=root/"output",dohost=root/"bin/dohost";
  Write(dohost,"#!/bin/sh\nprintf '%s\\n' \"$1\" >>\"$IOS_TEST_CALLS\"\ncat \"$IOS_TEST_OUTPUT\"\n");chmod(dohost.c_str(),0700);
  setenv("SONAR_DATA_DIR",root.c_str(),1);setenv("IOS_TEST_CALLS",calls.c_str(),1);setenv("IOS_TEST_OUTPUT",output.c_str(),1);
  const auto path=(root/"bin").string()+":/usr/bin:/bin";setenv("PATH",path.c_str(),1);
  const std::string table="Interface IP-Address OK? Method Status Protocol\nGigabitEthernet1 192.168.148.181 YES NVRAM up up\nGigabitEthernet2 10.10.20.1 YES NVRAM up up\n";
  Write(root/"ios_cli/show_ip_interface_brief.out",table);Write(output,"Unexpected Error\n");
  ManagementService service("127.0.0.1",9,"/unused");
  Check(service.ExecuteIosCli({"show ip interface brief"})==table,"real service lost EEM header");
  Check(!fs::exists(calls),"real service spawned dohost despite valid snapshot");
  fs::rename(root/"ios_cli/show_ip_interface_brief.out",root/"saved");Write(output,table);
  Check(service.ExecuteIosCli({"show ip interface brief"})==table,"direct fallback failed");
  Check(!Read(calls).empty(),"missing EEM file did not invoke direct fallback");
  Write(output,"");Check(service.ExecuteIosCli({"show version"}).empty(),"blank direct output reported as success");
  fs::rename(root/"saved",root/"ios_cli/show_ip_interface_brief.out");Write(output,"Unexpected Error\n");
  const nlohmann::json policy={{"command","apply"},{"subnet_cidr","10.10.20.0/24"},{"rule_target",{{"acl_name","SONAR_EEM_TEST"}}}};
  Check(!service.ApplyPolicyCommand(PolicyCommand{DeviceType::kRouter, "Cisco", PolicyAction::kApply, policy}),"failed CLI application reported as applied");
  Check(Read(calls).find("interface GigabitEthernet2")!=std::string::npos,"header loss prevented subnet interface resolution");
  Check(!fs::exists(root/"ios_apply/acl_desired.cmd"),"failed ACL left an untracked asynchronous request");
  Write(output,"configured\n");
  Check(service.ExecuteIosCli({"configure terminal","interface GigabitEthernet2","no shutdown"})=="configured\n","direct configuration regression");
  std::stop_source stop;service.SetStopToken(stop.get_token());stop.request_stop();
  Check(service.ExecuteIosCli({"show ip interface brief"}).empty(),"stop request ignored while reading snapshot");
  fs::remove_all(root);std::cout<<"[ok] real ManagementService: EEM priority/header, direct fallback, blank output, interface binding, pending != applied, cancellation\n";return 0;
 }
 catch(const std::exception& e){fs::remove_all(root);std::cerr<<"[FAIL] "<<e.what()<<'\n';return 1;}
}
