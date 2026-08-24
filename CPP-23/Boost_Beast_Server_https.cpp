#include <boost/asio.hpp>
#include <boost/asio/ssl.hpp>
#include <boost/beast.hpp>
#include <iostream>
#include <memory>
#include <string>

namespace beast = boost::beast;
namespace http = beast::http;
namespace asio = boost::asio;
namespace ssl = boost::asio::ssl;
using tcp = asio::ip::tcp;

class http_session : public std::enable_shared_from_this<http_session> {
public:
    http_session(tcp::socket socket, ssl::context& ctx)
        : m_stream(std::move(socket), ctx) {}

    void run() {
        // 비동기 SSL 핸드셰이크 후 HTTP 요청 읽기 시작
        m_stream.async_handshake(ssl::stream_base::server,
            beast::bind_front_handler(&http_session::on_handshake, shared_from_this()));
    }

private:
    void on_handshake(beast::error_code ec) {
        if (ec) return;
        do_read();
    }

    void do_read() {
        // HTTP 요청을 읽기 위한 버퍼 초기화
        m_req = {};
        http::async_read(m_stream, m_buffer, m_req,
            beast::bind_front_handler(&http_session::on_read, shared_from_this()));
    }

    void on_read(beast::error_code ec, std::size_t bytes_transferred) {
        boost::ignore_unused(bytes_transferred);
        if (ec == http::error::end_of_stream) {
            // 클라이언트가 연결을 끊음
            return;
        }
        if (ec) return;

        // 클라이언트가 보낸 HTTP 요청 처리 및 응답 작성
        handle_request();
    }

    void handle_request() {
        // 예시: 어떤 경로로 요청이 오든 "Hello HTTPS!" 응답 전송
        auto const resp = [this]() {
            http::response<http::string_body> res{http::status::ok, m_req.version()};
            res.set(http::field::server, "Boost.Beast HTTPS Server");
            res.set(http::field::content_type, "text/html");
            res.keep_alive(m_req.keep_alive());
            res.body() = "<h1>Hello from C++ HTTPS Server!</h1>";
            res.prepare_payload();
            return res;
        }();

        // 비동기로 HTTP 응답 쓰기
        auto sp = std::make_shared<http::response<http::string_body>>(resp);
        http::async_write(m_stream, *sp,
            beast::bind_front_handler(&http_session::on_write, shared_from_this(), sp->need_eof()));
    }

    void on_write(bool close, beast::error_code ec, std::size_t bytes_transferred) {
        boost::ignore_unused(bytes_transferred);
        if (ec) return;

        if (close) {
            // Keep-Alive가 아니라면 연결 종료
            return;
        }
        // 지속 연결(Keep-Alive)인 경우 다음 요청 대기
        do_read();
    }

    ssl::stream<tcp::socket> m_stream;
    beast::flat_buffer m_buffer;
    http::request<http::string_body> m_req;
};