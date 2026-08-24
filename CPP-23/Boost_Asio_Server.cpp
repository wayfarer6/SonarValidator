#include <boost/asio.hpp>
#include <iostream>
#include <string>
#include <memory>

using tcp = boost::asio::ip::tcp;

// 1. session 클래스를 server 클래스보다 위에 배치합니다.
class session : public std::enable_shared_from_this<session> {
public:
    session(tcp::socket socket) : m_socket(std::move(socket)) {}

    void run() {
        do_read();
    }

private:
    void do_read() {
        auto self(shared_from_this());
        m_socket.async_read_some(boost::asio::buffer(m_data),
            [this, self](boost::system::error_code ec, std::size_t length) {
                if (!ec) {
                    std::cout << "Received: " << std::string(m_data, length);
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

    tcp::socket m_socket;
    char m_data[1024];
};

// 2. 그 다음 server 클래스가 session을 안전하게 사용할 수 있습니다.
class server
{
public:
    server(boost::asio::io_context& io_context, short port) 
        : m_acceptor(io_context, tcp::endpoint(tcp::v4(), port)) {
        do_accept();
    }

private:
    void do_accept() {
        m_acceptor.async_accept([this](boost::system::error_code ec, tcp::socket socket) {
            if (!ec) {
                std::cout << "creating session on: " 
                    << socket.remote_endpoint().address().to_string() 
                    << ":" << socket.remote_endpoint().port() << '\n';
                
                std::make_shared<session>(std::move(socket))->run();
            } else {
                std::cout << "error: " << ec.message() << std::endl;
            }
            do_accept();
        });
    }

    tcp::acceptor m_acceptor;
};

int main() {
    try {
        boost::asio::io_context io_context;
        server s(io_context, 8080);
        std::cout << "Server started on port 8080...\n";
        io_context.run();
    } catch (std::exception& e) {
        std::cerr << "Exception: " << e.what() << "\n";
    }
    return 0;
}