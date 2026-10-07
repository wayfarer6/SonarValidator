#include "workers/TerminalAgentWorker.hpp"

#include <algorithm>
#include <chrono>
#include <climits>
#include <cstdlib>
#include <deque>
#include <functional>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>

#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>
#include <openssl/evp.h>
#include <nlohmann/json.hpp>

#include "components/backend_communication/connect_with_timeout.hpp"
#include "components/backend_communication/envelope.hpp"
#include "components/terminal/terminal_session.hpp"

namespace
{

namespace beast = boost::beast;
namespace net = boost::asio;
namespace websocket = beast::websocket;
using tcp = net::ip::tcp;
using Json = nlohmann::json;

std::string Base64Encode(std::string_view input)
{
    if (input.size() > static_cast<std::size_t>(INT_MAX))
    {
        throw std::length_error("terminal output is too large to encode");
    }
    if (input.empty())
    {
        return {};
    }

    const std::size_t encoded_size = 4 * ((input.size() + 2) / 3);
    std::string output(encoded_size + 1, '\0');
    const int result = EVP_EncodeBlock(
        reinterpret_cast<unsigned char*>(output.data()),
        reinterpret_cast<const unsigned char*>(input.data()),
        static_cast<int>(input.size()));
    if (result < 0)
    {
        throw std::runtime_error("OpenSSL failed to encode terminal output");
    }
    output.resize(static_cast<std::size_t>(result));
    return output;
}

} // namespace

void TerminalAgentWorker(std::stop_token stop_token, const ProberConfig& config)
{
    const char* configured_secret = std::getenv("SONAR_TERMINAL_SHARED_SECRET");
    if (configured_secret == nullptr || std::char_traits<char>::length(configured_secret) < 32)
    {
        std::cerr << "[TERMINAL] disabled: SONAR_TERMINAL_SHARED_SECRET must contain at least 32 characters\n";
        return;
    }
    const std::string shared_secret(configured_secret);
    const std::string agent_id =
        config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();

    while (!stop_token.stop_requested())
    {
        TerminalSession shell;
        try
        {
            net::io_context ioc;
            tcp::resolver resolver(ioc);
            websocket::stream<beast::tcp_stream> stream(ioc);
            const auto endpoints = resolver.resolve(
                config.GetServerIpv4(), std::to_string(config.GetServerPort()));
            const auto connect_error =
                sonar::net::ConnectWithTimeout(stream, endpoints, std::chrono::seconds(5));
            if (connect_error)
            {
                throw boost::system::system_error(connect_error);
            }

            stream.handshake(config.GetServerIpv4(), "/api/v1/terminal/agent");
            stream.text(true);
            const Json hello{
                {"type", "terminal-hello"},
                {"agent_id", agent_id},
                {"secret", shared_secret},
                {"device_type", envelope::DeviceTypeToString(config.GetDeviceType())}};
            const std::string hello_payload = hello.dump();
            stream.write(net::buffer(hello_payload));

            beast::flat_buffer read_buffer;
            std::deque<std::shared_ptr<std::string>> write_queue;
            std::size_t queued_bytes = 0;
            bool writing = false;
            bool disconnected = false;
            std::function<void()> pump_write;
            pump_write = [&]() {
                if (writing || write_queue.empty() || disconnected)
                {
                    return;
                }
                writing = true;
                const auto payload = write_queue.front();
                stream.async_write(net::buffer(*payload),
                    [&, payload](const boost::system::error_code& error, std::size_t) {
                        queued_bytes -= payload->size();
                        if (!write_queue.empty() && write_queue.front() == payload)
                        {
                            write_queue.pop_front();
                        }
                        writing = false;
                        if (error)
                        {
                            disconnected = true;
                            return;
                        }
                        pump_write();
                    });
            };
            const auto send = [&](Json message) {
                auto payload = std::make_shared<std::string>(message.dump());
                if (queued_bytes + payload->size() > 256 * 1024)
                {
                    disconnected = true;
                    return;
                }
                queued_bytes += payload->size();
                write_queue.push_back(std::move(payload));
                pump_write();
            };

            std::function<void()> read_next;
            read_next = [&]() {
                stream.async_read(read_buffer,
                    [&](const boost::system::error_code& error, std::size_t) {
                        if (error)
                        {
                            disconnected = true;
                            return;
                        }
                        const std::string raw = beast::buffers_to_string(read_buffer.data());
                        read_buffer.consume(read_buffer.size());
                        try
                        {
                            const Json message = Json::parse(raw);
                            const std::string type = message.value("type", std::string{});
                            if (type == "terminal-open")
                            {
                                shell.Close();
                                if (!shell.Open({"/bin/bash", "--login"}))
                                {
                                    send({{"type", "terminal-error"},
                                          {"message", "Could not start /bin/bash"}});
                                }
                                else
                                {
                                    (void)shell.Resize(100, 30);
                                    send({{"type", "terminal-ready"}});
                                }
                            }
                            else if (type == "terminal-input" && shell.IsOpen())
                            {
                                const std::string data = message.value("data", std::string{});
                                if (data.size() > 8192 || !shell.WriteRaw(data))
                                {
                                    send({{"type", "terminal-error"},
                                          {"message", "Could not write terminal input"}});
                                }
                            }
                            else if (type == "terminal-resize" && shell.IsOpen())
                            {
                                const auto cols = static_cast<unsigned short>(
                                    std::clamp(message.value("cols", 80), 20, 300));
                                const auto rows = static_cast<unsigned short>(
                                    std::clamp(message.value("rows", 24), 5, 100));
                                if (!shell.Resize(cols, rows))
                                {
                                    send({{"type", "terminal-error"},
                                          {"message", "Could not resize terminal"}});
                                }
                            }
                            else if (type == "terminal-close" && shell.IsOpen())
                            {
                                shell.Close();
                                send({{"type", "terminal-exit"}});
                            }
                        }
                        catch (const std::exception& error)
                        {
                            send({{"type", "terminal-error"},
                                  {"message", std::string("Invalid terminal message: ") +
                                                  error.what()}});
                        }
                        read_next();
                    });
            };
            read_next();

            while (!stop_token.stop_requested() && !disconnected)
            {
                ioc.run_for(std::chrono::milliseconds(20));
                if (shell.IsOpen())
                {
                    std::string output = shell.ReadAvailable(std::chrono::milliseconds(5), 8192);
                    if (!output.empty())
                    {
                        send({{"type", "terminal-output"},
                              {"data_base64", Base64Encode(output)}});
                    }
                }
            }
        }
        catch (const std::exception& error)
        {
            if (!stop_token.stop_requested())
            {
                std::cerr << "[TERMINAL] Agent terminal channel disconnected: "
                          << error.what() << '\n';
            }
        }
        shell.Close();
        if (!stop_token.stop_requested())
        {
            std::this_thread::sleep_for(std::chrono::seconds(2));
        }
    }
}
