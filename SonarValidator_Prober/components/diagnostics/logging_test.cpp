#include "utils/log.hpp"
#include "components/policy/policy_actions.hpp"
#include <cassert>
#include <regex>
#include <thread>
#include <vector>

using namespace sonar::logging;
using namespace std::chrono_literals;
class RecordingDevice : public DeviceCommandExecutor {
public:
    int queries = 0;
    bool RunCommand(const std::string&) override { return true; }
    std::string RunCommandOutput(const std::string&) override { ++queries; return "RAW_DEVICE_PAYLOAD_SENTINEL"; }
    std::string ExecuteIosCli(const std::vector<std::string>&) override { ++queries; return "RAW_DEVICE_PAYLOAD_SENTINEL"; }
    std::string QueryAristaCli(const std::string&) override { ++queries; return "RAW_DEVICE_PAYLOAD_SENTINEL"; }
};

int main() {
    setenv("SONAR_LOG_LEVEL", "INFO", 1);
    assert(ParseLevel("debug") == Level::Debug);
    assert(ParseLevel("invalid") == Level::Info);
    assert(ParseLevel("OFF") == Level::Off);
    assert(SingleLine("\nhello\r\nworld\t\033[31m", 100) == "hello world [31m");
    const auto shortened = SingleLine(std::string(4000, 'x'), 320);
    assert(shortened.size() == 323);
    assert(SingleLine("가나다", 4) == "가...");
    RepeatGate gate;
    const auto now = std::chrono::steady_clock::now();
    assert(gate.Check("one", "offline", now).emit);
    assert(!gate.Check("one", "offline", now + 2s).emit);
    assert(!gate.Check("one", "offline", now + 3s).emit);
    auto later = gate.Check("one", "offline", now + 60s);
    assert(later.emit && later.skipped == 2);
    assert(gate.Check("one", "new failure", now + 61s).emit);
    assert(gate.Check("two", "new failure", now + 61s).emit);
    int evaluated = 0;
    SV_LOG_DEBUG << ++evaluated;
    assert(evaluated == 0);
    std::ostringstream captured;
    auto* original = std::cerr.rdbuf(captured.rdbuf());
    std::vector<std::thread> threads;
    for (int n = 0; n < 4; ++n) threads.emplace_back([n] {
        for (int i = 0; i < 20; ++i) { SV_LOG_INFO << "operation " << n << '/' << i << "\nnext"; }
    });
    for (auto& t : threads) t.join();
    RecordingDevice device;
    const bool queried = PolicyActions(device).ApplyCiscoRouterPolicy({
        {"command", "get"}, {"targets", nlohmann::json::array({{
            {"command_syntax", nlohmann::json::array({"show ip route"})}
        }})}
    });
    assert(queried && device.queries == 1);
    assert(captured.str().find("RAW_DEVICE_PAYLOAD_SENTINEL") == std::string::npos);
    std::cerr.rdbuf(original);
    std::istringstream lines(captured.str());
    std::string line;
    int count = 0;
    std::regex format(R"(^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}[+-]\d{4} INFO operation [0-3]/[0-9]+ next$)");
    while (std::getline(lines, line)) { assert(std::regex_match(line, format)); ++count; }
    assert(count == 80);
}
