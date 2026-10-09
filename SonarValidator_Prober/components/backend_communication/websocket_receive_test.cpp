#include <chrono>
#include <iostream>
#include <stdexcept>
#include <thread>
#include "module/telemetry_module/telemetry_service.hpp"

using namespace std::chrono_literals;

static void Require(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
    std::cout << "[ok] " << message << '\n';
}

int main() {
    net::io_context server_context;
    tcp::acceptor acceptor(server_context, {net::ip::make_address("127.0.0.1"), 0});
    const auto port = acceptor.local_endpoint().port();
    std::exception_ptr server_error;
    std::jthread server([&] {
        try {
            websocket::stream<tcp::socket> peer(acceptor.accept());
            peer.accept();
            beast::flat_buffer input;
            peer.read(input);
            std::this_thread::sleep_for(150ms);
            peer.text(true);
            peer.write_some(false, net::buffer(std::string("{\"tick\":")));
            std::this_thread::sleep_for(200ms);
            peer.write_some(true, net::buffer(std::string("1}")));
            input.consume(input.size());
            peer.read(input);
            Require(beast::buffers_to_string(input.data()) == "second", "second send reaches peer after idle polling");
            peer.write(net::buffer(std::string("{\"tick\":2}")));
            // Intentionally do not initiate a close handshake.
            input.consume(input.size());
            boost::system::error_code ec;
            peer.read(input, ec);
        } catch (...) { server_error = std::current_exception(); }
    });

    const auto begin = std::chrono::steady_clock::now();
    {
        TelemetryService client("127.0.0.1", port, "/telemetry-test");
        Require(client.connect(), "local WebSocket handshake");
        Require(client.sendText("first"), "initial telemetry send");
        std::string message;
        const auto poll_start = std::chrono::steady_clock::now();
        Require(!client.tryReceiveText(message, 30ms), "idle read returns without a message");
        Require(std::chrono::steady_clock::now() - poll_start < 120ms, "idle polling respects its timeout");
        Require(client.sendText("second"), "write works while asynchronous read is pending");
        int timeouts = 0;
        while (!client.tryReceiveText(message, 30ms)) {
            ++timeouts;
            Require(std::chrono::steady_clock::now() - begin < 3s, "fragmented receive stays bounded");
        }
        Require(timeouts >= 2 && message == "{\"tick\":1}", "fragmented message survives multiple polling windows");
        while (!client.tryReceiveText(message, 30ms)) {
            Require(std::chrono::steady_clock::now() - begin < 3s, "second receive stays bounded");
        }
        Require(message == "{\"tick\":2}", "connection remains usable for subsequent messages");
        Require(!client.tryReceiveText(message, 30ms), "leave an idle read pending before destruction");
    }
    Require(std::chrono::steady_clock::now() - begin < 3s, "destructor cancels idle read without waiting for peer close");
    server.join();
    if (server_error) std::rethrow_exception(server_error);
}
