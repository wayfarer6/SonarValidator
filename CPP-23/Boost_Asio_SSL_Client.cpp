#include <boost/asio.hpp>
#include <boost/asio/ssl.hpp>
#include <iostream>
#include <string>
#include <memory>

using tcp = boost::asio::ip::tcp;

class client : public std::enable_shared_from_this<client> {
public:
    client(boost::asio::io_context& io_context, 
           boost::asio::ssl::context& context, 
           const std::string& host, 
           const std::string& port)
        : m_socket(io_context, context),
          m_resolver(io_context),
          m_host(host) {}

    void start(const std::string& port) {
        // 1. 도메인 및 포트 이름(문자열)을 엔드포인트로 변환 (비동기 DNS 조회)
        m_resolver.async_resolve(m_host, port,
            [this, self = shared_from_this()](const boost::system::error_code& ec, tcp::resolver::results_type endpoints) {
                if (!ec) {
                    do_connect(endpoints);
                } else {
                    std::cerr << "Resolve failed: " << ec.message() << "\n";
                }
            });
    }

private:
    void do_connect(const tcp::resolver::results_type& endpoints) {
        // 2. TCP 서버에 연결
        boost::asio::async_connect(m_socket.lowest_layer(), endpoints,
            [this, self = shared_from_this()](const boost::system::error_code& ec, const tcp::endpoint& /*endpoint*/) {
                if (!ec) {
                    do_handshake();
                } else {
                    std::cerr << "Connect failed: " << ec.message() << "\n";
                }
            });
    }

    void do_handshake() {
        // (선택사항) 자체 서명된 인증서를 테스트용으로 쓸 경우 호스트명 검증을 끌 수 있습니다.
        // 실무에서는 서버 인증서 검증 설정을 켜두어야 안전합니다.
        m_socket.set_verify_mode(boost::asio::ssl::verify_none);
        m_socket.set_verify_callback(boost::asio::ssl::rfc2818_verification(m_host));

        // 3. SSL 핸드셰이크 수행 (클라이언트 모드)
        m_socket.async_handshake(boost::asio::ssl::stream_base::client,
            [this, self = shared_from_this()](const boost::system::error_code& ec) {
                if (!ec) {
                    std::cout << "SSL Handshake success! Sending message...\n";
                    do_write();
                } else {
                    std::cerr << "Handshake failed: " << ec.message() << "\n";
                }
            });
    }

    void do_write() {
        auto self(shared_from_this());
        std::string request = "Hello from Boost.Asio SSL Client!\n";

        // 4. 서버로 메시지 전송
        boost::asio::async_write(m_socket, boost::asio::buffer(request),
            [this, self](const boost::system::error_code& ec, std::size_t /*length*/) {
                if (!ec) {
                    do_read();
                } else {
                    std::cerr << "Write failed: " << ec.message() << "\n";
                }
            });
    }

    void do_read() {
        auto self(shared_from_this());

        // 5. 서버로부터 응답 읽기
        m_socket.async_read_some(boost::asio::buffer(m_data),
            [this, self](const boost::system::error_code& ec, std::size_t length) {
                if (!ec) {
                    std::cout << "Received from server: " << std::string(m_data, length);
                } else {
                    std::cerr << "Read failed: " << ec.message() << "\n";
                }
            });
    }

    boost::asio::ssl::stream<tcp::socket> m_socket;
    tcp::resolver m_resolver;
    std::string m_host;
    char m_data[1024];
};

int main() {
    try {
        boost::asio::io_context io_context;

        // TLS 클라이언트 컨텍스트 설정
        boost::asio::ssl::context context(boost::asio::ssl::context::tlsv12_client);
        
        // 만약 서버 인증서가 공인 기관(CA)에서 발급받은 게 아니라면 시스템 기본 루트 CA를 쓸 수 있습니다.
        // context.set_default_verify_paths();

        // 로컬 테스트용 클라이언트 생성 (localhost, 포트 4433)
        auto c = std::make_shared<client>(io_context, context, "127.0.0.1", "4433");
        c->start("4433");

        io_context.run();
    } catch (std::exception& e) {
        std::cerr << "Exception: " << e.what() << "\n";
    }

    return 0;
}