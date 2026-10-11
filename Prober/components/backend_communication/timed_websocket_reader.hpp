#ifndef SONAR_VALIDATOR_PROBER_TIMED_WEBSOCKET_READER_HPP_
#define SONAR_VALIDATOR_PROBER_TIMED_WEBSOCKET_READER_HPP_

#include <chrono>
#include <memory>
#include <string>
#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>

namespace sonar::net {

// Keep one asynchronous read alive between polling windows. A synchronous
// Beast read can wait indefinitely even with native_non_blocking(true), while
// surfacing would_block to Beast fails the WebSocket. Cancelling on every
// timeout would also discard fragmented messages and break idle connections.
class TimedWebSocketReader {
    struct State {
        boost::beast::flat_buffer buffer;
        boost::system::error_code error;
        bool pending{false};
        bool ready{false};
    };
    std::shared_ptr<State> state_{std::make_shared<State>()};

public:
    using Stream = boost::beast::websocket::stream<boost::beast::tcp_stream>;

    bool ReadFor(boost::asio::io_context& context, Stream& stream,
                 std::string& message, std::chrono::milliseconds timeout,
                 boost::system::error_code& error) {
        error.clear();
        if (!state_->pending && !state_->ready) {
            state_->pending = true;
            stream.async_read(state_->buffer,
                [state = state_](boost::system::error_code ec, std::size_t) {
                    state->error = ec;
                    state->pending = false;
                    state->ready = true;
                });
        }
        if (!state_->ready) {
            context.restart();
            context.run_for(timeout);
        }
        if (!state_->ready) return false;
        error = state_->error;
        if (!error) message = boost::beast::buffers_to_string(state_->buffer.data());
        state_->buffer.consume(state_->buffer.size());
        state_->ready = false;
        return !error;
    }

    // No close handshake here: an idle/unresponsive peer must not prevent
    // worker shutdown. Drain cancellation before reusing the stream.
    void Reset(boost::asio::io_context& context, Stream& stream) {
        boost::system::error_code ignored;
        auto& socket = boost::beast::get_lowest_layer(stream).socket();
        socket.cancel(ignored);
        socket.shutdown(boost::asio::ip::tcp::socket::shutdown_both, ignored);
        socket.close(ignored);
        context.restart();
        context.poll();
        state_ = std::make_shared<State>();
    }
};
} // namespace sonar::net
#endif
