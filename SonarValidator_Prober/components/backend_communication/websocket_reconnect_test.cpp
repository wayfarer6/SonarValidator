#include "module/telemetry_module/telemetry_service.hpp"
#include <iostream>
#include <stdexcept>
#include <thread>

int main()
{
    net::io_context context;
    tcp::acceptor acceptor(context, {net::ip::make_address("127.0.0.1"), 0});
    std::exception_ptr error;
    std::jthread server([&] {
        try
        {
            for (int connection = 0; connection < 2; ++connection)
            {
                websocket::stream<tcp::socket> peer(acceptor.accept());
                peer.accept();
                beast::flat_buffer input;
                boost::system::error_code ec;
                peer.read(input, ec);
                if (connection == 1 && (ec || beast::buffers_to_string(input.data()) != "reconnected"))
                    throw std::runtime_error("message did not reach replacement connection");
            }
        }
        catch (...) { error = std::current_exception(); }
    });
    {
        TelemetryService client("127.0.0.1", acceptor.local_endpoint().port(), "/reconnect");
        if (!client.connect() || !client.connect() || !client.sendText("reconnected"))
            throw std::runtime_error("reconnect did not replace WebSocket protocol state");
    }
    server.join();
    if (error) std::rethrow_exception(error);
    std::cout << "[ok] reconnect replaces the socket and WebSocket state\n";
}
