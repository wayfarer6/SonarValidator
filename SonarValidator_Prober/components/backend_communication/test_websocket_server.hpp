#ifndef SONAR_TEST_WEBSOCKET_SERVER_HPP_
#define SONAR_TEST_WEBSOCKET_SERVER_HPP_
#include <boost/asio.hpp>
#include <boost/beast/websocket.hpp>
#include <exception>
#include <functional>
#include <thread>
#include <poll.h>

class TestWebSocketServer {
public:
    using Tcp = boost::asio::ip::tcp;
    using Socket = boost::beast::websocket::stream<Tcp::socket>;
    explicit TestWebSocketServer(std::function<void(Socket&)> handle)
        : acceptor_(ioc_, Tcp::endpoint(Tcp::v4(), 0)), worker_([this, handle](std::stop_token stop) {
            try {
                while (!stop.stop_requested()) {
                    pollfd ready{acceptor_.native_handle(), POLLIN, 0};
                    if (::poll(&ready, 1, 50) <= 0) continue;
                    if (stop.stop_requested()) return;
                    Tcp::socket tcp(ioc_);
                    acceptor_.accept(tcp);
                    Socket socket(std::move(tcp));
                    socket.accept();
                    handle(socket);
                    return;
                }
            } catch (...) { error_ = std::current_exception(); }
        }) {}
    ~TestWebSocketServer() { worker_.request_stop(); }
    unsigned short Port() const { return acceptor_.local_endpoint().port(); }
    void Wait() { worker_.join(); if (error_) std::rethrow_exception(error_); }
private:
    boost::asio::io_context ioc_;
    Tcp::acceptor acceptor_;
    std::exception_ptr error_;
    std::jthread worker_;
};
#endif
