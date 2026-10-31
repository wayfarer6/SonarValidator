#include "module/telemetry_module/telemetry_service.hpp"
#include "components/backend_communication/test_websocket_server.hpp"
#include <boost/beast/core.hpp>
#include <iostream>
#include <stdexcept>

int main() {
    TestWebSocketServer server([](auto& socket) {
        boost::beast::flat_buffer buffer;
        socket.read(buffer);
        if (boost::beast::buffers_to_string(buffer.data()) != "telemetry-snapshot")
            throw std::runtime_error("telemetry frame changed");
        socket.write(boost::asio::buffer(std::string("monitor-update")));
    });
    TelemetryService service("127.0.0.1", server.Port(), "/api/v1/telemetry");
    if (!service.sendRequest("telemetry-snapshot", "/api/v1/telemetry")) throw std::runtime_error("telemetry send failed");
    if (service.receiveText() != "monitor-update") throw std::runtime_error("telemetry instruction not received");
    server.Wait();
    std::cout << "Telemetry loopback integration PASS\n";
}
