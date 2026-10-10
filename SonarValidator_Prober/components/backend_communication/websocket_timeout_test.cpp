#include "components/backend_communication/connect_with_timeout.hpp"
#include "components/backend_communication/timed_websocket_operation.hpp"
#include <iostream>
#include <stdexcept>
#include <thread>

using namespace std::chrono_literals;
namespace net = boost::asio;
using tcp = net::ip::tcp;
using Stream = sonar::net::WebSocket;

void Require(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
    std::cout << "[ok] " << message << '\n';
}

int main() {
    net::io_context server_context, context;
    tcp::acceptor acceptor(server_context, {net::ip::make_address("127.0.0.1"), 0});
    tcp::resolver resolver(context);
    const auto endpoints = resolver.resolve("127.0.0.1", std::to_string(acceptor.local_endpoint().port()));
    Stream stream(context);
    // TCP accepts, but the server never sends an HTTP upgrade response.
    Require(!sonar::net::ConnectWithTimeout(stream, endpoints, 1s), "TCP connection succeeds");
    auto peer = acceptor.accept();
    const auto start = std::chrono::steady_clock::now();
    bool timed_out = false;
    try { sonar::net::HandshakeWithTimeout(context, stream, "localhost", "/", 150ms); }
    catch (const boost::system::system_error& e) { timed_out = e.code() == net::error::timed_out; }
    Require(timed_out && std::chrono::steady_clock::now() - start < 1s,
            "silent HTTP peer cannot hang WebSocket handshake");
    Require(!boost::beast::get_lowest_layer(stream).socket().is_open(), "timed out handshake closes socket");
    peer.close();

    // Reuse the failed stream and ensure writes to a non-reading peer also end.
    Require(!sonar::net::ConnectWithTimeout(stream, endpoints, 1s), "reconnect after timeout");
    std::jthread server([&](std::stop_token stop) {
        boost::beast::websocket::stream<tcp::socket> ws(acceptor.accept());
        ws.next_layer().set_option(net::socket_base::receive_buffer_size(1024));
        ws.accept();
        while (!stop.stop_requested()) std::this_thread::sleep_for(10ms);
    });
    sonar::net::HandshakeWithTimeout(context, stream, "localhost", "/", 1s);
    boost::beast::get_lowest_layer(stream).socket().set_option(net::socket_base::send_buffer_size(1024));
    timed_out = false;
    const std::string message(8 * 1024 * 1024, 'x');
    const auto write_start = std::chrono::steady_clock::now();
    try { sonar::net::WriteWithTimeout(context, stream, message, 150ms); }
    catch (const boost::system::system_error& e) { timed_out = e.code() == net::error::timed_out; }
    Require(timed_out && std::chrono::steady_clock::now() - write_start < 1s,
            "non-reading peer cannot hang WebSocket write");
    server.request_stop();
}
