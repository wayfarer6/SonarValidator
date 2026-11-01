#include "components/advice/advice_job.hpp"
#include <filesystem>
#include <iostream>
#include <unistd.h>
#include <stdexcept>
using advice::Json;
void Check(bool ok,const char* text){if(!ok)throw std::runtime_error(text);std::cout<<"[ok] "<<text<<'\n';}
int main(){
    std::string path="/tmp/sonar-advice-job-"+std::to_string(getpid())+".sqlite";
    sqlite3* raw=nullptr;Check(sqlite3_open(path.c_str(),&raw)==SQLITE_OK,"open isolated database");DbHandle handle(raw);
    ProberConfig config("review-agent","review-agent","","",DeviceType::kRouter,"Cisco IOS XE",0,"198.18.0.1",3000);config.SetManagementPrefixes("198.18.0.0/24");
    Json message={{"type","advice-command"},{"execution_id","11111111-1111-1111-1111-111111111111"},{"agent_id","review-agent"},{"operation","inspect_routes"},{"arguments",Json::object()},{"expires_at",std::time(nullptr)+100}};
    int calls=0;Json last;auto runner=[&](auto,const auto&){++calls;return command_runner::Result{"Codes: C - connected\n",0,true};};auto send=[&](const Json& value){last=value;};
    {DatabaseService database(handle);advice::Handle(message,config,database,runner,send,false);Check(last["status"]=="SUCCEEDED"&&calls==1,"new job executes once");advice::Handle(message,config,database,runner,send,false);Check(last["status"]=="SUCCEEDED"&&calls==1,"duplicate job reuses durable result");}
    handle.reset();raw=nullptr;sqlite3_open(path.c_str(),&raw);handle.reset(raw);
    {DatabaseService database(handle);advice::Handle(message,config,database,runner,send,false);Check(last["status"]=="SUCCEEDED"&&calls==1,"reopened database prevents replay after restart");
      auto changed=message;changed["operation"]="inspect_interfaces";advice::Handle(changed,config,database,runner,send,false);Check(last["status"]=="REJECTED"&&calls==1,"same ID cannot execute a different request");
      auto busy=message;busy["execution_id"]="22222222-2222-2222-2222-222222222222";advice::Handle(busy,config,database,runner,send,true);Check(last["status"]=="REJECTED"&&calls==1,"interactive terminal cannot overlap maintenance job");
      auto expired=busy;expired["expires_at"]=std::time(nullptr)-1;advice::Handle(expired,config,database,runner,send,false);Check(last["status"]=="REJECTED"&&calls==1,"expired command is not executed");
      auto foreign=busy;foreign["agent_id"]="other-agent";advice::Handle(foreign,config,database,runner,send,false);Check(last["status"]=="REJECTED"&&calls==1,"agent identity is checked locally");
      database.Close();advice::Handle(busy,config,database,runner,send,false);Check(last["status"]=="REJECTED"&&calls==1,"database failure prevents execution");}
    handle.reset();std::filesystem::remove(path);
}
