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


/*

Agent의 커뮤니케이션 서비스 설계

- 하나의 에이전트는 일단 하나의 중앙서버에 대해 1개의 세션만 갖습니다.
- 모든 네트워크 관련 작업은 커뮤티케이션 서비스가 처리합니다.
- 멀티 세션을 갖지 않는 구조이기에 stream과 , 네트워크 작업등을 하나의 io_context 라는 큐에 담고 수행합니다.
- 세션을 닫는 것은 io_context의 종료 시점에 처리됩니다. 

*/

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
    bool sendRequest(std::string &request, std::string &target);  // 필요 시 연결 후 전송
};

using CommunicationService = TelemetryService;