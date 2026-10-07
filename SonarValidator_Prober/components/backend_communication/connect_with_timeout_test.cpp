#include "components/backend_communication/connect_with_timeout.hpp"

#include <chrono>
#include <iostream>

int main()
{
    boost::asio::io_context ioc;
    boost::asio::ip::tcp::resolver resolver(ioc);
    sonar::net::websocket::stream<sonar::net::beast::tcp_stream> stream(ioc);
    const auto results = resolver.resolve("127.0.0.1", "1");

    const auto first = sonar::net::ConnectWithTimeout(stream, results, std::chrono::seconds(1));
    if (!first || sonar::net::beast::get_lowest_layer(stream).socket().is_open())
    {
        std::cerr << "[FAIL] failed attempt leaves socket closed\n";
        return 1;
    }

    const auto second = sonar::net::ConnectWithTimeout(stream, results, std::chrono::seconds(1));
    if (!second || sonar::net::beast::get_lowest_layer(stream).socket().is_open())
    {
        std::cerr << "[FAIL] repeated connection attempt can recover cleanly\n";
        return 1;
    }

    std::cout << "[ok] failed connection attempts close/reset the socket\n";
    return 0;
}
