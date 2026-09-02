#ifndef SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_
#define SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_

#include <string>

#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>
#include "switch.hpp"

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
    bool sendText(const std::string &message);
    std::string receiveText();
    bool applyPolicy(const std::string& policy_name, const std::string& payload);
    bool fetchPolicy(const std::string& policy_name, std::string& payload);



private:
    std::string host_{};
    int port_{0};
    std::string target_{};
    net::io_context ioc_;
    tcp::resolver resolver_;
    websocket::stream<beast::tcp_stream> stream_;
    bool connected_;
};

#endif  // SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_
