#include "components/terminal/ios_eem_bridge.hpp"
#include <cassert>
#include <iostream>
#include <unistd.h>
int main(){
    char dir[]="/tmp/sonar-eem-bridge-XXXXXX";assert(mkdtemp(dir));std::filesystem::path root(dir);setenv("SONAR_IOS_EEM_DIR",dir,1);
    try{
        std::jthread responder([&](std::stop_token stop){
            while(!stop.stop_requested()){
                std::ifstream request(root/"request");std::string token;std::getline(request,token);
                if(!token.empty()){
                    std::filesystem::remove(root/"request");
                    // An old or partially written response must not satisfy a new command.
                    {std::ofstream out(root/"response");out<<"old\nOK\nstale\nEND old\n";}
                    std::this_thread::sleep_for(std::chrono::milliseconds(30));
                    {std::ofstream out(root/"response");out<<token<<"\nOK\nconfigured\n";}
                    std::this_thread::sleep_for(std::chrono::milliseconds(30));
                    {std::ofstream out(root/"response",std::ios::app);out<<"END "<<token<<"\n";}
                    break;
                }std::this_thread::sleep_for(std::chrono::milliseconds(5));
            }
        });
        assert(ios_eem_bridge::Execute({"show clock"},{},std::chrono::seconds(2))=="configured\n");responder.join();
        assert(ios_eem_bridge::Execute({"show clock"},{},std::chrono::milliseconds(80)).empty());
        assert(!std::filesystem::exists(root/"request"));
        std::stop_source stop;stop.request_stop();
        assert(ios_eem_bridge::Execute({"show clock"},stop.get_token(),std::chrono::milliseconds(80)).empty());
        assert(ios_eem_bridge::Execute({"show clock\nreload"},{},std::chrono::milliseconds(80)).empty());
        std::filesystem::remove_all(root);std::cout<<"[ok] correlated complete replies, timeout cleanup, cancellation, command framing\n";
    }catch(...){std::filesystem::remove_all(root);throw;}
}
