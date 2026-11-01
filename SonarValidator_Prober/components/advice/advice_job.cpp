#include "components/advice/advice_job.hpp"
#include "components/terminal/arista_cli.hpp"
#include "components/terminal/ios_cli.hpp"
#include "components/terminal/ios_snapshot.hpp"
#include "utils/runtime_environment.hpp"
#include <chrono>
#include <ctime>
#include <optional>
#include <regex>
#include <sqlite3.h>

namespace advice {
namespace {
constexpr const char* kSchema="CREATE TABLE IF NOT EXISTS advice_jobs (execution_id TEXT PRIMARY KEY,request_hash TEXT NOT NULL,result_json TEXT NOT NULL,finished INTEGER NOT NULL,created_at INTEGER NOT NULL)";
struct Statement {sqlite3_stmt* value=nullptr;~Statement(){sqlite3_finalize(value);}};
bool Bind(sqlite3_stmt* statement,int index,const std::string& value){return sqlite3_bind_text(statement,index,value.c_str(),static_cast<int>(value.size()),SQLITE_TRANSIENT)==SQLITE_OK;}
struct BeginResult {bool created=false;bool ok=false;std::string request_hash;Json cached;};
BeginResult Begin(DatabaseService& database,const std::string& id,const std::string& hash) {
    BeginResult result;
    database.Execute([&](DbHandle& handle){
        auto db=handle.get();if(!db||sqlite3_exec(db,kSchema,nullptr,nullptr,nullptr)!=SQLITE_OK)return DatabaseResult{};
        Statement select;
        if(sqlite3_prepare_v2(db,"SELECT request_hash,result_json,finished FROM advice_jobs WHERE execution_id=?",-1,&select.value,nullptr)!=SQLITE_OK||!Bind(select.value,1,id))return DatabaseResult{};
        if(sqlite3_step(select.value)==SQLITE_ROW){
            result.request_hash=reinterpret_cast<const char*>(sqlite3_column_text(select.value,0));
            result.cached=Json::parse(reinterpret_cast<const char*>(sqlite3_column_text(select.value,1)),nullptr,false);result.ok=!result.cached.is_discarded();return DatabaseResult{};
        }
        sqlite3_exec(db,"DELETE FROM advice_jobs WHERE created_at < CAST(strftime('%s','now') AS INTEGER)-180",nullptr,nullptr,nullptr);
        Statement count;
        if(sqlite3_prepare_v2(db,"SELECT count(*) FROM advice_jobs",-1,&count.value,nullptr)!=SQLITE_OK||sqlite3_step(count.value)!=SQLITE_ROW||sqlite3_column_int(count.value,0)>=64)return DatabaseResult{};
        Statement insert;
        if(sqlite3_prepare_v2(db,"INSERT INTO advice_jobs(execution_id,request_hash,result_json,finished,created_at) VALUES(?,?,?,0,?)",-1,&insert.value,nullptr)!=SQLITE_OK)return DatabaseResult{};
        auto running=Json{{"status","UNCONFIRMED"},{"verified",false},{"changed",true},{"output",""},{"detail","This execution was previously started; automatic replay is blocked."}}.dump();
        if(!Bind(insert.value,1,id)||!Bind(insert.value,2,hash)||!Bind(insert.value,3,running)||sqlite3_bind_int64(insert.value,4,std::time(nullptr))!=SQLITE_OK)return DatabaseResult{};
        result.created=result.ok=sqlite3_step(insert.value)==SQLITE_DONE;return DatabaseResult{};
    });return result;
}
bool Finish(DatabaseService& database,const std::string& id,const Json& value) {
    bool ok=false;database.Execute([&](DbHandle& handle){auto db=handle.get();if(!db)return DatabaseResult{};
        Statement update;if(sqlite3_prepare_v2(db,"UPDATE advice_jobs SET result_json=?,finished=1 WHERE execution_id=?",-1,&update.value,nullptr)!=SQLITE_OK)return DatabaseResult{};
        if(!Bind(update.value,1,value.dump())||!Bind(update.value,2,id))return DatabaseResult{};
        ok=sqlite3_step(update.value)==SQLITE_DONE&&sqlite3_changes(db)==1;

        return DatabaseResult{};
    });return ok;
}
Json Reject(const std::string& detail){return {{"status","REJECTED"},{"verified",false},{"changed",false},{"output",""},{"detail",detail}};}
}
Runner NativeRunner(const ProberConfig& config,std::stop_token stop) {
    return [&config,stop](Channel channel,const std::vector<std::string>& commands){
        std::string command;
        if(channel==Channel::IosSnapshot&&commands.size()==1){
            auto cached=ios_snapshot::Read(runtime_environment::DataDirectory(),commands[0]);
            if(!cached.empty())return command_runner::Result{"[EEM snapshot; device source refresh is independent]\n"+cached,0,true};
        }
        if(channel==Channel::Ios||channel==Channel::IosSnapshot)command="dohost "+runtime_environment::ShellQuote(ios_cli::BuildScript(commands))+" 2>&1";
        else if(channel==Channel::Eos)command=arista_cli::BuildCommand(commands);
        else if(channel==Channel::Nft)command="printf %s "+runtime_environment::ShellQuote(commands.at(0))+" | nft -f - 2>&1";
        else command=commands.at(0)+" 2>&1";
        if(command.empty())return command_runner::Result{"Invalid invocation",-1,false};
        auto result=command_runner::RunWithStatus(command,std::chrono::seconds(15),1024*1024,stop);
        if(channel==Channel::Ios||channel==Channel::IosSnapshot){auto check=ios_cli::CheckOutput(result.output);if(check.failed&&!result.output.empty())result.exit_code=1;}
        return result;
    };
}
void Handle(const Json& message,const ProberConfig& config,DatabaseService& database,const Runner& run,const Sender& send,bool busy) {
    std::string id=message.value("execution_id",std::string{});
    auto reply=[&](Json result){result["type"]="advice-command-result";result["execution_id"]=id;send(Json::parse(result.dump(-1,' ',false,Json::error_handler_t::replace)));};
    if(!std::regex_match(id,std::regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))){reply(Reject("Invalid execution ID"));return;}
    const auto agent=config.GetAgentId().empty()?config.GetAgentName():config.GetAgentId();
    if(message.value("agent_id",std::string{})!=agent){reply(Reject("Execution belongs to another agent"));return;}
    if(!message.contains("expires_at")||!message["expires_at"].is_number_integer()){reply(Reject("Missing execution expiry"));return;}
    auto now=std::time(nullptr),expires=message["expires_at"].get<std::int64_t>();
    if(expires<now||expires>now+180){reply(Reject("Execution expired or clocks are not synchronized"));return;}
    if(busy){reply(Reject("Close the interactive terminal before executing a proposal"));return;}
    bool started=false;
    try {
        auto plan=Compile(message.at("operation").get<std::string>(),config.GetProductName(),message.at("arguments"),
            config.GetManagementPrefixes()+","+message.value("management_prefix",std::string{}),config.GetServerIpv4());
        const auto server=message.value("management_server",std::string{});
        if(!server.empty()&&server!=config.GetServerIpv4())
            (void)Compile(plan.operation,config.GetProductName(),plan.arguments,server+"/32",config.GetServerIpv4());
        auto hash=Hash(message.dump());auto begun=Begin(database,id,hash);
        if(!begun.ok){reply(Reject("Cannot persist execution receipt; no command was sent"));return;}
        if(!begun.created){reply(begun.request_hash==hash?begun.cached:Reject("Execution ID was already used with another request"));return;}
        started=true;
        send({{"type","advice-command-started"},{"execution_id",id}});
        auto result=Execute(plan,run);
        result=Json::parse(result.dump(-1,' ',false,Json::error_handler_t::replace));
        if(!Finish(database,id,result)){result["status"]="UNCONFIRMED";result["verified"]=false;result["detail"]="Command finished but its durable result could not be saved; inspect device state before retrying.";}
        reply(result);
    }catch(const std::exception& error){
        auto result=Reject(error.what());
        if(started){result["status"]="UNCONFIRMED";result["changed"]=true;(void)Finish(database,id,result);}
        reply(result);
    }
}
}
