#include <string>

#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>

namespace beast = boost::beast;
namespace http = beast::http;
namespace net = boost::asio;
namespace websocket = beast::websocket;
using tcp = boost::asio::ip::tcp;


/*

Agent의 커뮤니케이션 서비스 설계

- 하나의 에이전트는 일단 하나의 중앙서버에 대해 1개의 세션만 갖습니다.
- 모든 네트워크 관련 작업은 커뮤티케이션 서비스가 처리합니다.
- 멀티 세션을 갖지 않는 구조이기에 stream과 , 네트워크 작업등을 하나의 io_context 라는 큐에 담고 수행합니다.
- 세션을 닫는 것은 io_context의 종료 시점에 처리됩니다. 

*/

class TelemetryService {
private:
    std::string host_;
    int port_;
    std::string target_;
    net::io_context ioc_;
    tcp::resolver resolver_;
    websocket::stream<beast::tcp_stream> stream_;
    bool connected_;

    void initialize(std::string host, int port, std::string target);

public:
    TelemetryService();
    TelemetryService(std::string host, int port, std::string target);
    ~TelemetryService();

    bool connect();
    bool sendText(const std::string &message);
    std::string receiveText();
    bool sendRequest(std::string &request, std::string &target);
};

using CommunicationService = TelemetryService;