#ifndef SONAR_TIMED_WEBSOCKET_OPERATION_HPP
#define SONAR_TIMED_WEBSOCKET_OPERATION_HPP

#include <algorithm>
#include <chrono>
#include <memory>
#include <string>
#include <stop_token>
#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>

namespace sonar::net {
using WebSocket = boost::beast::websocket::stream<boost::beast::tcp_stream>;

template<class Start>
boost::system::error_code RunWebSocketOperation(
    boost::asio::io_context& context, WebSocket& stream,
    std::chrono::milliseconds timeout, Start start, std::stop_token stop = {})
{
    if (stop.stop_requested()) return boost::asio::error::operation_aborted;
    struct State {
        bool done = false;
        boost::system::error_code result;
    };
    auto state = std::make_shared<State>();
    start([state](boost::system::error_code ec, auto...) {
        state->result = ec;
        state->done = true;
    });
    const auto deadline = std::chrono::steady_clock::now() + timeout;
    context.restart();
    while (!state->done && !stop.stop_requested() && std::chrono::steady_clock::now() < deadline)
        context.run_for(std::min(deadline - std::chrono::steady_clock::now(),
                                 std::chrono::steady_clock::duration(std::chrono::milliseconds(50))));
    if (state->done)
        return state->result;

    boost::system::error_code ignored;
    auto& socket = boost::beast::get_lowest_layer(stream).socket();
    socket.cancel(ignored);
    socket.close(ignored);
    context.restart();
    // Closing the socket must complete the pending handler. Bound the drain so a
    // pathological completion failure cannot hold the worker forever.
    const auto drain_deadline = std::chrono::steady_clock::now() + std::chrono::seconds(1);
    while (!state->done && std::chrono::steady_clock::now() < drain_deadline)
        context.run_one_until(drain_deadline);
    if (!state->done)
        context.stop();
    return stop.stop_requested() ? boost::asio::error::operation_aborted : boost::asio::error::timed_out;
}

inline void HandshakeWithTimeout(boost::asio::io_context& context, WebSocket& stream,
                                 const std::string& host, const std::string& target,
                                 std::chrono::milliseconds timeout, std::stop_token stop = {})
{
    const auto ec = RunWebSocketOperation(context, stream, timeout, [&](auto complete) {
        stream.async_handshake(host, target, std::move(complete));
    }, stop);
    if (ec) throw boost::system::system_error(ec);
}

inline void WriteWithTimeout(boost::asio::io_context& context, WebSocket& stream,
                             const std::string& message, std::chrono::milliseconds timeout, std::stop_token stop = {})
{
    const auto ec = RunWebSocketOperation(context, stream, timeout, [&](auto complete) {
        stream.async_write(boost::asio::buffer(message), std::move(complete));
    }, stop);
    if (ec) throw boost::system::system_error(ec);
}
} // namespace sonar::net
#endif
