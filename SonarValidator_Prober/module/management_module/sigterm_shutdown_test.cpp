#include <chrono>
#include <iostream>
#include <stdexcept>

#include "components/backend_communication/envelope.hpp"
#include "components/backend_communication/test_websocket_server.hpp"
#include "module/management_module/management_service.hpp"

using namespace std::chrono_literals;

void Require(bool value, const char* message)
{
    if (!value) throw std::runtime_error(message);
    std::cout << "[ok] " << message << '\n';
}

int main()
{
    std::stop_source stop;
    TestWebSocketServer server([&](TestWebSocketServer::Socket& peer) {
        boost::beast::flat_buffer input;
        peer.read(input);
        const auto hello = nlohmann::json::parse(boost::beast::buffers_to_string(input.data()));
        const auto ack = envelope::Make(envelope::kAck, "shutdown-test", "VM", envelope::CorrelationId(hello), nlohmann::json::object());
        peer.write(boost::asio::buffer(ack.dump()));
        input.consume(input.size());
        peer.read(input);  // Policy request is received, but no response is sent.
        stop.request_stop();
        input.consume(input.size());
        boost::system::error_code ignored;
        peer.read(input, ignored);
    });
    {
        ManagementService service("127.0.0.1", server.Port(), "/management");
        const auto start = std::chrono::steady_clock::now();
        service.fetchPolicy(DeviceType::kVirtualMachine, "shutdown-test", stop.get_token());
        Require(stop.stop_requested(), "stop requested during an active policy response wait");
        Require(std::chrono::steady_clock::now() - start < 2s, "policy wait ends promptly on stop");
    }
    server.Wait();

    ManagementService service("127.0.0.1", server.Port(), "/management");
    const auto start = std::chrono::steady_clock::now();
    service.fetchPolicy(DeviceType::kVirtualMachine, "already-stopped", stop.get_token());
    Require(std::chrono::steady_clock::now() - start < 250ms, "already-stopped request avoids connection setup");
}
