#ifndef SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_
#define SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_

#include <string>

#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>
#include "switch/switch.hpp"
#include "firewall/firewall.hpp"
#include "router/routing_table.hpp"
#include "prober_config.hpp"
#include <thread>
namespace beast = boost::beast;
namespace net = boost::asio;
namespace websocket = beast::websocket;
using tcp = boost::asio::ip::tcp;

enum class DeviceType : uint8_t
{
    kSwitch,
    kVirtualMachine,
    kFirewall,
    kRouter
};

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

private:
    std::string host_{};
    DeviceType device_type_{};
    int port_{0};
    std::string target_{};
    net::io_context ioc_;
    tcp::resolver resolver_;
    websocket::stream<beast::tcp_stream> stream_;
    bool connected_;
    std::thread management_thread_;

};

#endif  // SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_
