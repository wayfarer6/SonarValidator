#include <chrono>
#include <string>

#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>

namespace beast = boost::beast;
namespace http = beast::http;
namespace net = boost::asio;
namespace websocket = beast::websocket;
using tcp = boost::asio::ip::tcp;


class TelemetryService {
private:
    std::string host_;                              // 서버 호스트
    int port_;                                      // 서버 포트
    std::string target_;                            // WebSocket 경로
    net::io_context ioc_;                           // Boost.Asio I/O 컨텍스트
    tcp::resolver resolver_;                        // DNS 리졸버
    websocket::stream<beast::tcp_stream> stream_;   // WebSocket 스트림
    bool connected_;                                // 연결 상태

    void initialize(std::string host, int port, std::string target);

public:
    TelemetryService();
    TelemetryService(std::string host, int port, std::string target);
    ~TelemetryService();

    bool connect();                                 // 서버에 WebSocket 연결
    bool sendText(const std::string &message);      // 텍스트 전송
    std::string receiveText();                      // 텍스트 수신(블로킹)
    // 짧은 시간 동안 서버 지시(제어 메시지)를 수신합니다.
    // 수신이 없으면 false, 있으면 message에 JSON 문자열을 담고 true를 반환합니다.
    bool tryReceiveText(std::string& message, std::chrono::milliseconds timeout);
    bool sendRequest(const std::string &request, const std::string &target);  // 필요 시 연결 후 전송
};

using CommunicationService = TelemetryService;