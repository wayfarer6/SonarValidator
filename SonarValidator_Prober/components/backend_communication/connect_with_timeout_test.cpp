#include "components/backend_communication/connect_with_timeout.hpp"

#include <chrono>
#include <iostream>
#include <thread>

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

    std::stop_source cancel;
    std::jthread timer([&] { std::this_thread::sleep_for(std::chrono::milliseconds(150)); cancel.request_stop(); });
    const auto start = std::chrono::steady_clock::now();
    const auto cancelled = sonar::net::ConnectWithTimeout(stream, resolver.resolve("192.0.2.1", "9"),
                                                          std::chrono::seconds(5), cancel.get_token());
    if (!cancelled || std::chrono::steady_clock::now() - start > std::chrono::seconds(1)) {
        std::cerr << "[FAIL] connect cancellation exceeded its budget\n";
        return 1;
    }

    std::cout << "[ok] failed connection attempts close/reset the socket\n";
    return 0;
}
