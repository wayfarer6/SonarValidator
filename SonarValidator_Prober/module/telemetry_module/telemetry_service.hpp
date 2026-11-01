#include <chrono>
#include <memory>
#include <string>
#include <stop_token>

#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>
#include "components/backend_communication/timed_websocket_reader.hpp"

namespace beast = boost::beast;
namespace http = beast::http;
namespace net = boost::asio;
namespace websocket = beast::websocket;
using tcp = boost::asio::ip::tcp;


class TelemetryService {
private:
    std::stop_token stop_token_;
    std::string host_;                              // 서버 호스트
    int port_;                                      // 서버 포트
    std::string target_;                            // WebSocket 경로
    net::io_context ioc_;                           // Boost.Asio I/O 컨텍스트
    tcp::resolver resolver_;                        // DNS 리졸버
    using Stream = websocket::stream<beast::tcp_stream>;
    std::unique_ptr<Stream> stream_;   // WebSocket 스트림
    sonar::net::TimedWebSocketReader reader_;
    bool connected_;                                // 연결 상태

    void initialize(std::string host, int port, std::string target);

public:
    static constexpr std::chrono::seconds kConnectTimeout{5};
    static constexpr std::chrono::seconds kHandshakeTimeout{5};

    TelemetryService();
    TelemetryService(std::string host, int port, std::string target);
    ~TelemetryService();

    void SetStopToken(std::stop_token stop) { stop_token_ = stop; }
    bool IsConnected() const { return connected_; }
    bool connect();                                 // 서버에 WebSocket 연결
    bool sendText(const std::string &message);      // 텍스트 전송
    std::string receiveText();


    bool tryReceiveText(std::string& message, std::chrono::milliseconds timeout);
    bool sendRequest(const std::string &request, const std::string &target);  // 필요 시 연결 후 전송
};

using CommunicationService = TelemetryService;
