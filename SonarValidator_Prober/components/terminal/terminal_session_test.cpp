#include "components/terminal/terminal_session.hpp"
#include <chrono>
#include <cerrno>
#include <filesystem>
#include <iostream>
#include <regex>
#include <stdexcept>
#include <thread>
#include <fcntl.h>
#include <signal.h>
#include <sys/wait.h>
#include <unistd.h>
using namespace std::chrono_literals;
void Check(bool condition, const char* message) { if (!condition) throw std::runtime_error(message); }
std::string Read(TerminalSession& shell, const std::string& marker)
{
    std::string result;
    const auto deadline=std::chrono::steady_clock::now()+2s;
    while(shell.IsOpen() && std::chrono::steady_clock::now()<deadline)
    {
        result+=shell.ReadAvailable(50ms);
        if(result.find(marker)!=std::string::npos) break;
    }
    return result;
}
int main()
{
    try
    {
        const auto count_fds=[] { return std::distance(std::filesystem::directory_iterator("/proc/self/fd"), std::filesystem::directory_iterator{}); };
        const auto baseline=count_fds();
        const int original=open("/dev/null",O_RDONLY);
        Check(original>=0,"marker FD open");
        Check(dup2(original,200)==200,"marker FD duplicate");
        TerminalSession shell;
        Check(shell.Open({"/bin/sh","-c","test -t 0 && test ! -e /proc/self/fd/200 && echo ISOLATED; read -r line; printf 'received:%s\\n' \"$line\""}),"spawn PTY");
        Check(Read(shell,"ISOLATED").find("ISOLATED")!=std::string::npos,"PTY or FD isolation failed");
        Check(shell.Resize(100,30),"PTY resize");
        Check(shell.WriteRaw("terminal-input\n"),"PTY input");
        Check(Read(shell,"received:terminal-input").find("received:terminal-input")!=std::string::npos,"PTY output");
        shell.Close(); close(original); close(200);
        Check(!shell.Open({"/sonar/nonexistent-command"}),"spawn failure reported as success");
        for(int n=0;n<30;++n) { Check(shell.Open({"/bin/sh","-c","exit 0"}),"repeated spawn"); shell.Close(); }
        Check(shell.Open({"/bin/sh","-c","printf finished"}),"EOF shell");
        Read(shell,"finished");
        const auto eof_deadline=std::chrono::steady_clock::now()+2s;
        while(shell.IsOpen() && std::chrono::steady_clock::now()<eof_deadline) shell.ReadAvailable(20ms);
        Check(!shell.IsOpen(),"shell exit not detected"); shell.Close();
        Check(shell.Open({"/bin/bash","--noprofile","--norc","-c",
            "set -m; trap '' TERM HUP; sleep 60 & printf 'PIDS:%s:%s\\n' $$ $!; wait"}),"job-control shell");
        const std::string pids=Read(shell,"PIDS:")+shell.ReadAvailable(50ms);
        std::smatch match; Check(std::regex_search(pids,match,std::regex("PIDS:([0-9]+):([0-9]+)")),"job PIDs");
        const pid_t job=std::stoi(match[2]);
        const auto start=std::chrono::steady_clock::now(); shell.Close();
        Check(std::chrono::steady_clock::now()-start<1s,"termination not bounded");
        const auto job_deadline=std::chrono::steady_clock::now()+2s;
        while(kill(job,0)==0 && std::chrono::steady_clock::now()<job_deadline) std::this_thread::sleep_for(10ms);
        Check(kill(job,0)<0 && errno==ESRCH,"background job survived close");
        Check(shell.Open({"/bin/bash","--noprofile","--norc","-c",
            "set -m; (trap '' HUP TERM; exec sleep 60) & printf 'ORPHAN:%s\\n' $!; sleep 0.1; exit"}),"exiting job-control shell");
        const auto orphan_output=Read(shell,"ORPHAN:")+shell.ReadAvailable(50ms);
        Check(std::regex_search(orphan_output,match,std::regex("ORPHAN:([0-9]+)")),"orphan PID");
        const auto exit_deadline=std::chrono::steady_clock::now()+500ms;
        while(shell.IsOpen() && std::chrono::steady_clock::now()<exit_deadline) shell.ReadAvailable(20ms);
        Check(!shell.IsOpen(),"exited leader with background PTY holder was not detected");
        const pid_t orphan=std::stoi(match[1]);
        shell.Close();
        const auto orphan_deadline=std::chrono::steady_clock::now()+2s;
        while(kill(orphan,0)==0 && std::chrono::steady_clock::now()<orphan_deadline) std::this_thread::sleep_for(10ms);
        Check(kill(orphan,0)<0 && errno==ESRCH,"background PTY holder survived exited leader cleanup");
        Check(shell.Open({"/bin/sh","-c","stty raw -echo; printf READY; exec sleep 60"}),"blocked-input shell");
        Check(Read(shell,"READY").find("READY")!=std::string::npos,"raw PTY not ready");
        const auto write_start=std::chrono::steady_clock::now();
        Check(!shell.WriteRaw(std::string(1024*1024,'x')),"backpressure not detected");
        Check(std::chrono::steady_clock::now()-write_start<1s,"write blocked event loop"); shell.Close();
        Check(count_fds()==baseline,"PTY FD leaked");
        int status=0; errno=0; Check(waitpid(-1,&status,WNOHANG)==-1 && errno==ECHILD,"unreaped direct child");
        std::cout<<"[ok] PTY IO/resize/EOF/leader exit with background holder, FD isolation, 30 reconnects, job cleanup, bounded backpressure, no FD/child leaks\n";
        return 0;
    }
    catch(const std::exception& error) { std::cerr<<"[FAIL] "<<error.what()<<'\n';return 1; }
}
