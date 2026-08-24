#include <boost/asio.hpp>
#include <boost/asio/ssl.hpp>
#include <iostream>
#include <string>
#include <memory>

using tcp = boost::asio::ip::tcp;

// 1. SSL 스트림을 사용하는 세션 클래스
class session : public std::enable_shared_from_this<session> {
public:
    // 생성자에서 일반 socket 대신 ssl::stream을 받도록 변경
    session(tcp::socket socket, boost::asio::ssl::context& context)
        : m_socket(std::move(socket), context) {}

    void run() {
        do_handshake();
    }

private:
    void do_handshake() {
        auto self(shared_from_this());
        // 클라이언트와 SSL 핸드셰이크 수행 (인증서 교환 및 암호화 설정)
        m_socket.async_handshake(boost::asio::ssl::stream_base::server,
            [this, self](const boost::system::error_code& ec) {
                if (!ec) {
                    do_read();
                } else {
                    std::cerr << "Handshake failed: " << ec.message() << std::endl;
                }
            });
    }

    void do_read() {
        auto self(shared_from_this());
        m_socket.async_read_some(boost::asio::buffer(m_data),
            [this, self](boost::system::error_code ec, std::size_t length) {
                if (!ec) {
                    std::cout << "Received (SSL): " << std::string(m_data, length);
                    do_write(length);
                }
            });
    }

    void do_write(std::size_t length) {
        auto self(shared_from_this());
        boost::asio::async_write(m_socket, boost::asio::buffer(m_data, length),
            [this, self](boost::system::error_code ec, std::size_t /*length*/) {
                if (!ec) {
                    do_read();
                }
            });
    }

    // 소켓을 SSL 스트림으로 감싸서 관리
    boost::asio::ssl::stream<tcp::socket> m_socket;
    char m_data[1024];
};

// 2. SSL Context를 관리하는 서버 클래스
class server
{
public:
    server(boost::asio::io_context& io_context, short port) 
        : m_acceptor(io_context, tcp::endpoint(tcp::v4(), port)),
          m_context(boost::asio::ssl::context::tlsv12_server) {
        
        // 인증서 및 개인키 설정 (openssl 예제와 동일)
        m_context.set_options(
            boost::asio::ssl::context::default_workarounds |
            boost::asio::ssl::context::no_sslv2 |
            boost::asio::ssl::context::no_sslv3);
        m_context.use_certificate_chain_file("cert.pem");
        m_context.use_private_key_file("key.pem", boost::asio::ssl::context::pem);

        do_accept();
    }

private:
    void do_accept() {
        m_acceptor.async_accept([this](boost::system::error_code ec, tcp::socket socket) {
            if (!ec) {
                std::cout << "creating SSL session on: " 
                    << socket.remote_endpoint().address().to_string() 
                    << ":" << socket.remote_endpoint().port() << '\n';
                
                // 세션 생성 시 소켓과 SSL Context를 함께 전달
                std::make_shared<session>(std::move(socket), m_context)->run();
            } else {
                std::cout << "error: " << ec.message() << std::endl;
            }
            do_accept();
        });
    }

    tcp::acceptor m_acceptor;
    boost::asio::ssl::context m_context;
};

int main() {
    try {
        boost::asio::io_context io_context;
        server s(io_context, 4433);
        std::cout << "Boost.Asio SSL Server started on port 4433...\n";
        io_context.run();
    } catch (std::exception& e) {
        std::cerr << "Exception: " << e.what() << "\n";
    }
    return 0;
}