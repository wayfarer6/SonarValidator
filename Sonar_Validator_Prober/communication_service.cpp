#include "communication_service.hpp"

namespace
{
    // Report a failure
    void
    fail(beast::error_code ec, char const *what)
    {
        std::cerr << what << ": " << ec.message() << "\n";
    }

    void do_session(
        std::string const &host,
        std::string const &port,
        std::string const &target,
        int version,
        net::io_context &ioc,
        ssl::context &ctx,
        net::yield_context yield)
    {
        beast::error_code ec;

        // These objects perform our I/O
        tcp::resolver resolver(ioc);
        ssl::stream<beast::tcp_stream> stream(ioc, ctx);

        // Set SNI Hostname (many hosts need this to handshake successfully)
        if (!SSL_set_tlsext_host_name(stream.native_handle(), host.c_str()))
        {
            ec.assign(static_cast<int>(::ERR_get_error()), net::error::get_ssl_category());
            std::cerr << ec.message() << "\n";
            return;
        }

        // Set the expected hostname in the peer certificate for verification
        stream.set_verify_callback(ssl::host_name_verification(host));

        // Look up the domain name
        auto const results = resolver.async_resolve(host, port, yield[ec]);
        if (ec)
            return fail(ec, "resolve");

        // Set the timeout.
        beast::get_lowest_layer(stream).expires_after(std::chrono::seconds(30));

        // Make the connection on the IP address we get from a lookup
        get_lowest_layer(stream).async_connect(results, yield[ec]);
        if (ec)
            return fail(ec, "connect");

        // Set the timeout.
        beast::get_lowest_layer(stream).expires_after(std::chrono::seconds(30));

        // Perform the SSL handshake
        stream.async_handshake(ssl::stream_base::client, yield[ec]);
        if (ec)
            return fail(ec, "handshake");

        // Set up an HTTP GET request message
        http::request<http::string_body> req{http::verb::get, target, version};
        req.set(http::field::host, host);
        req.set(http::field::user_agent, BOOST_BEAST_VERSION_STRING);

        // Set the timeout.
        beast::get_lowest_layer(stream).expires_after(std::chrono::seconds(30));

        // Send the HTTP request to the remote host
        http::async_write(stream, req, yield[ec]);
        if (ec)
            return fail(ec, "write");

        // This buffer is used for reading and must be persisted
        beast::flat_buffer b;

        // Declare a container to hold the response
        http::response<http::dynamic_body> res;

        // Receive the HTTP response
        http::async_read(stream, b, res, yield[ec]);
        if (ec)
            return fail(ec, "read");

        // Write the message to standard out
        std::cout << res << std::endl;

        // Set the timeout.
        beast::get_lowest_layer(stream).expires_after(std::chrono::seconds(30));

        // Gracefully close the stream
        // stream.async_shutdown(yield[ec]);
        
        // 일단 종료하지 않을것이기에 예제의 shutdown 제거함.

        // ssl::error::stream_truncated, also known as an SSL "short read",
        // indicates the peer closed the connection without performing the
        // required closing handshake (for example, Google does this to
        // improve performance). Generally this can be a security issue,
        // but if your communication protocol is self-terminated (as
        // it is with both HTTP and WebSocket) then you may simply
        // ignore the lack of close_notify.
        //
        // https://github.com/boostorg/beast/issues/38
        //
        // https://security.stackexchange.com/questions/91435/how-to-handle-a-malicious-ssl-tls-shutdown
        //
        // When a short read would cut off the end of an HTTP message,
        // Beast returns the error beast::http::error::partial_message.
        // Therefore, if we see a short read here, it has occurred
        // after the message has been completed, so it is safe to ignore it.

        if (ec != net::ssl::error::stream_truncated)
            return fail(ec, "shutdown");
    }
}

CommunicationService::CommunicationService()
{

}