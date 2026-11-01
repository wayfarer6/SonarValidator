#include "components/terminal/arista_cli.hpp"
#include "components/policy/policy_actions.hpp"
#include "module/management_module/management_service.hpp"
#include <chrono>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <thread>
#include <sys/stat.h>
#include <unistd.h>

using namespace std::chrono_literals;
namespace fs = std::filesystem;
void Require(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
    std::cout << "[ok] " << message << '\n';
}

struct Fixture {
    fs::path dir;
    std::string old_path = std::getenv("PATH") ? std::getenv("PATH") : "";
    Fixture() {
        char pattern[] = "/tmp/sonar-arista-test-XXXXXX";
        const char* created = mkdtemp(pattern);
        if (!created) throw std::runtime_error("temporary FastCli fixture");
        dir = created;
        std::ofstream script(dir / "FastCli");
        script << R"(#!/bin/sh
[ "$1" = '-p' ] && [ "$2" = '15' ] && [ "$3" = '-M' ] && [ "$4" = '-c' ] || exit 2
case "$5" in
  *'show bad'*) printf 'prompt and partial output\n%% Invalid input\n'; exit 1 ;;
  *'show slow'*) printf 'BEGIN\n'; sleep 0.4; printf 'COMPLETE_TAIL\n' ;;
  *'show cancel'*) sleep 5 ;;
  *) printf '%s' "$5" ;;
esac
)";
        script.close();
        chmod((dir / "FastCli").c_str(), 0700);
        setenv("PATH", (dir.string() + ":" + old_path).c_str(), 1);
    }
    ~Fixture() { setenv("PATH", old_path.c_str(), 1); fs::remove_all(dir); }
};

class RecordingDevice final : public DeviceCommandExecutor {
public:
    bool success = true;
    std::vector<std::string> calls;
    std::string query;
    bool RunCommand(const std::string& command) override { calls.push_back(command); return success; }
    std::string RunCommandOutput(const std::string&) override { return {}; }
    std::string ExecuteIosCli(const std::vector<std::string>&) override { return {}; }
    std::string QueryAristaCli(const std::string&) override { return query; }
};

int main() {
    Fixture fixture;
    Require(arista_cli::BuildCommand({}).empty(), "empty batch rejected");
    Require(arista_cli::BuildCommand({"show vlan\nreload"}).empty(), "embedded CLI line rejected");
    Require(arista_cli::ValidVlan("1") && arista_cli::ValidVlan("4094") &&
            !arista_cli::ValidVlan("0") && !arista_cli::ValidVlan("4095") &&
            !arista_cli::ValidVlan("20;reload"), "VLAN range and input validated");
    ManagementService service;
    const auto start = std::chrono::steady_clock::now();
    const auto slow = service.QueryAristaCli("show slow");
    Require(slow.ends_with("COMPLETE_TAIL\n") && std::chrono::steady_clock::now() - start >= 350ms,
            "query waits past 300 ms for the complete response");
    Require(service.QueryAristaCli("show bad").empty(), "CLI failure rejects nonempty partial output");
    const std::string quoted = "show interfaces description | include '$(touch " +
        (fixture.dir / "unexpected").string() + ")'";
    Require(service.QueryAristaCli(quoted) == quoted + "\n" && !fs::exists(fixture.dir / "unexpected"),
            "quotes and shell substitution remain one literal CLI argument");
    std::stop_source stop;
    service.SetStopToken(stop.get_token());
    std::jthread cancel([&] { std::this_thread::sleep_for(50ms); stop.request_stop(); });
    const auto cancel_start = std::chrono::steady_clock::now();
    Require(service.QueryAristaCli("show cancel").empty() &&
            std::chrono::steady_clock::now() - cancel_start < 500ms, "stop cancels a running Arista query");
    cancel.join();

    RecordingDevice device;
    PolicyActions actions(device);
    Require(actions.ApplyAristaSwitchPolicy({{"command","create"},{"vlan_id",3999}}), "VLAN uses batch execution status");
    Require(device.calls.back().find("vlan 3999\nend\n") != std::string::npos, "config batch ends configuration mode");
    device.success = false;
    Require(!actions.ApplyAristaSwitchPolicy({{"command","off"},{"interface","Ethernet2"}}), "failed control is not reported as success");
    const auto count = device.calls.size();
    Require(!actions.ApplyAristaSwitchPolicy({{"command","on"},{"interface","Ethernet2\nreload"}}) &&
            !actions.ApplyAristaSwitchPolicy({{"command","create"},{"vlan_id",4095}}) &&
            !actions.ApplyAristaSwitchPolicy({{"command","create"},{"acl_name","unsafe;reload"}}) &&
            device.calls.size() == count, "invalid policy fields execute nothing");
    Require(!actions.ApplyAristaSwitchPolicy({{"command","get"}}), "failed query makes get fail");
}
