#ifndef SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_
#define SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_

#include <string>
#include <vector>
#include "network.hpp"
#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>
#include "switch/switch.hpp"
#include "firewall/firewall.hpp"
#include "router/routing_table.hpp"
#include "prober_config.hpp"
#include "terminal_session.hpp"
#include <thread>
namespace beast = boost::beast;
namespace net = boost::asio;
namespace websocket = beast::websocket;
using tcp = boost::asio::ip::tcp;


class ManagementService {
public:
    ManagementService();
    ManagementService(std::string host, int port, std::string target);
    ~ManagementService();

    bool connect();
    Json sendText(const std::string &message);
    std::string receiveText();
    bool applyPolicy(const std::string& policy_name, const std::string& payload);
    Json fetchPolicy(const std::string& policy_name, std::string& payload);
    Json fetchPolicy(const DeviceType device_type,const std::string& device_id);
    void CheckSwitchStatus();
    void CheckRouterStatus();
    void CheckFirewallStatus();
    bool replyToPolicy(const std::string& policy_name, const std::string& payload);
    bool processOpenVSwitchPolicy(const Json& policy_payload);
    bool processAristaSwitchPolicy(const Json& policy_payload);
    Json commandAristaSwitch(const std::string& comm );
    void commandAristaSwitch_no_return(const std::string& comm );

    // 공통 명령 실행 유틸
    bool RunCommand(const std::string& command);
    std::string RunCommandOutput(const std::string& command);

    // IOS-XE guestshell의 dohost 명령으로 IOS CLI를 실행합니다. (인증 불필요)
    std::string ExecuteIosCli(const std::vector<std::string>& cli_commands);

    // 벤더별 정책 적용 (policy_receiver에서 호출)
    bool ApplyOpenVSwitchPolicy(const Json& policy);
    bool ApplyAristaSwitchPolicy(const Json& policy);
    bool ApplyCiscoSwitchPolicy(const Json& policy);
    bool ApplyCiscoRouterPolicy(const Json& policy);
    bool ApplyFrrRouterPolicy(const Json& policy);
    bool ApplyNftablesPolicy(const Json& policy);

private:
    // 영속 CLI 세션(pty)으로 명령을 보내고 출력을 받습니다.
    std::string CliCommand(const std::vector<std::string>& argv, const std::string& command);

    std::string host_{};
    DeviceType device_type_{};
    int port_{0};
    std::string target_{};
    net::io_context ioc_;
    tcp::resolver resolver_;
    websocket::stream<beast::tcp_stream> stream_;
    bool connected_;
    std::thread management_thread_;
    TerminalSession cli_session_;
    std::string cli_program_;

};

#endif  // SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_
