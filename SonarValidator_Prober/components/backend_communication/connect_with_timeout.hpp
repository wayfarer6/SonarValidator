#ifndef SONAR_CONNECT_WITH_TIMEOUT_HPP_
#define SONAR_CONNECT_WITH_TIMEOUT_HPP_
#include <algorithm>
#include <chrono>
#include <cerrno>
#include <stop_token>
#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>
#include <poll.h>
#include <sys/socket.h>

namespace sonar::net {
namespace beast = boost::beast;
namespace net = boost::asio;
namespace websocket = beast::websocket;
using tcp = boost::asio::ip::tcp;

inline boost::system::error_code ConnectWithTimeout(
    websocket::stream<beast::tcp_stream>& stream,
    const tcp::resolver::results_type& results,
    std::chrono::seconds timeout,
    std::stop_token stop = {}) {
    auto& socket = beast::get_lowest_layer(stream).socket();
    boost::system::error_code ignored;
    socket.close(ignored);
    struct CloseOnFailure {
        tcp::socket& socket;
        bool connected = false;
        ~CloseOnFailure() { if (!connected) { boost::system::error_code error; socket.close(error); } }
    } guard{socket};
    const auto deadline = std::chrono::steady_clock::now() + timeout;
    boost::system::error_code last = net::error::host_not_found;
    for (const auto& candidate : results) {
        if (stop.stop_requested()) return net::error::operation_aborted;
        if (std::chrono::steady_clock::now() >= deadline) return net::error::timed_out;
        socket.close(ignored);
        const auto endpoint = candidate.endpoint();
        socket.open(endpoint.protocol(), last);
        if (last) continue;
        socket.non_blocking(true, last);
        if (last) continue;
        // Asio's synchronous connect can poll indefinitely even on a nonblocking socket.
        const int connected = ::connect(socket.native_handle(), endpoint.data(), endpoint.size());
        int error = connected == 0 ? 0 : errno;
        if (error != 0 && error != EINPROGRESS && error != EALREADY && error != EINTR) {
            last = {error, boost::system::system_category()};
            continue;
        }
        while (error != 0) {
            if (stop.stop_requested()) return net::error::operation_aborted;
            const auto remaining = std::chrono::duration_cast<std::chrono::milliseconds>(deadline - std::chrono::steady_clock::now());
            if (remaining.count() <= 0) return net::error::timed_out;
            pollfd poller{socket.native_handle(), POLLOUT, 0};
            const int ready = ::poll(&poller, 1, static_cast<int>(std::min(remaining, std::chrono::milliseconds(50)).count()));
            if (ready < 0 && errno == EINTR) continue;
            if (ready < 0) { error = errno; break; }
            if (ready == 0) continue;
            socklen_t length = sizeof(error);
            if (::getsockopt(socket.native_handle(), SOL_SOCKET, SO_ERROR, &error, &length) != 0) error = errno;
            break;
        }
        if (error != 0) { last = {error, boost::system::system_category()}; continue; }
        socket.non_blocking(false, last);
        if (last) continue;
        guard.connected = true;
        return {};
    }
    return last;
}
}
#endif
