#include "utils/log.hpp"
#include "components/advice/advice_job.hpp"
#include "workers/TerminalAgentWorker.hpp"

#include <algorithm>
#include <chrono>
#include <climits>
#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>
#include <string_view>

#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>
#include <openssl/evp.h>
#include <nlohmann/json.hpp>

#include "components/backend_communication/connect_with_timeout.hpp"
#include "components/backend_communication/timed_websocket_operation.hpp"
#include "components/backend_communication/timed_websocket_reader.hpp"
#include "utils/stop_wait.hpp"
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

void TerminalAgentWorker(std::stop_token stop_token, const ProberConfig& config, DatabaseService* database)
{
    const std::string& configured_secret = config.GetTerminalSharedSecret();
    if (configured_secret.size() < 32)
    {
        SV_LOG_WARN << "터미널 사용 안 함: 인증 설정을 확인하세요";
        return;
    }
    const std::string& shared_secret = configured_secret;
    const std::string agent_id =
        config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();

    while (!stop_token.stop_requested())
    {
        TerminalSession shell;
        net::io_context ioc;
        tcp::resolver resolver(ioc);
        websocket::stream<beast::tcp_stream> stream(ioc);
        sonar::net::TimedWebSocketReader reader;
        try
        {
            const auto endpoints = resolver.resolve(
                config.GetServerIpv4(), std::to_string(config.GetServerPort()));
            const auto connect_error =
                sonar::net::ConnectWithTimeout(stream, endpoints, std::chrono::seconds(5), stop_token);
            if (connect_error)
            {
                throw boost::system::system_error(connect_error);
            }

            sonar::net::HandshakeWithTimeout(ioc, stream, config.GetServerIpv4(),
                                              "/api/v1/terminal/agent", std::chrono::seconds(5), stop_token);
            stream.text(true);
            const Json hello{
                {"type", "terminal-hello"},
                {"agent_id", agent_id},
                {"secret", shared_secret},
                {"device_type", envelope::DeviceTypeToString(config.GetDeviceType())},
                {"advice_operations", database ? advice::Operations(config.GetProductName(),
                    command_runner::RunWithStatus("command -v nft >/dev/null 2>&1",std::chrono::seconds(2),1024,stop_token).exit_code==0) : std::vector<std::string>{}}};
            const std::string hello_payload = hello.dump();
            sonar::net::WriteWithTimeout(ioc, stream, hello_payload, std::chrono::seconds(5), stop_token);

            const auto send = [&](const Json& message) {
                if (message.value("type", std::string{}) == "advice-command-result") {
                    SV_LOG_INFO << "장비 명령 결과: id=" << message.value("execution_id", std::string{})
                                << " 상태=" << message.value("status", std::string{});
                }
                sonar::net::WriteWithTimeout(ioc, stream, message.dump(),
                                             std::chrono::seconds(5), stop_token);
            };

            // This thread owns the socket, reader and PTY. Process one message,
            // then forward shell output; no recursive callbacks or write queue.
            while (!stop_token.stop_requested())
            {
                std::string raw;
                boost::system::error_code read_error;
                const bool received = reader.ReadFor(ioc, stream, raw,
                    std::chrono::milliseconds(20), read_error);
                if (read_error) throw boost::system::system_error(read_error);
                if (received)
                {
                    Json message;
                    try { message = Json::parse(raw); }
                    catch (const Json::exception&)
                    {
                        send({{"type", "terminal-error"}, {"message", "Invalid terminal JSON"}});
                        continue;
                    }
                    if (!message.is_object())
                    {
                        send({{"type", "terminal-error"}, {"message", "Expected a terminal message object"}});
                        continue;
                    }
                    const std::string type = message.value("type", std::string{});
                    if (type == "advice-command") {
                        if (database) advice::Handle(message, config, *database, advice::NativeRunner(config, stop_token), send, shell.IsOpen());
                        else send({{"type","advice-command-result"},{"execution_id",message.value("execution_id",std::string{})},{"status","REJECTED"},{"verified",false},{"detail","Durable execution store is unavailable"}});
                    }
                    else if (type == "terminal-open")
                    {
                        shell.Close();
                        if (!shell.Open({"/bin/bash", "--login"}))
                            send({{"type", "terminal-error"}, {"message", "Could not start /bin/bash"}});
                        else
                        {
                            (void)shell.Resize(100, 30);
                            SV_LOG_INFO << "터미널 열기: 노드=" << agent_id;
                            send({{"type", "terminal-ready"}});
                        }
                    }
                    else if (type == "terminal-input" && shell.IsOpen())
                    {
                        const std::string data = message.value("data", std::string{});
                        if (data.size() > 8192 || !shell.WriteRaw(data))
                        {
                            shell.Close();
                            send({{"type", "terminal-error"}, {"message", "Could not write terminal input"}});
                            send({{"type", "terminal-exit"}});
                        }
                    }
                    else if (type == "terminal-resize" && shell.IsOpen())
                    {
                        const auto cols = static_cast<unsigned short>(
                            std::clamp(message.value("cols", 80), 20, 300));
                        const auto rows = static_cast<unsigned short>(
                            std::clamp(message.value("rows", 24), 5, 100));
                        if (!shell.Resize(cols, rows))
                            send({{"type", "terminal-error"}, {"message", "Could not resize terminal"}});
                    }
                    else if (type == "terminal-close" && shell.IsOpen())
                    {
                        shell.Close();
                        SV_LOG_INFO << "터미널 닫기: 노드=" << agent_id;
                        send({{"type", "terminal-exit"}});
                    }
                }
                if (shell.IsOpen())
                {
                    const std::string output = shell.ReadAvailable(std::chrono::milliseconds(5), 8192);
                    if (!output.empty())
                        send({{"type", "terminal-output"}, {"data_base64", Base64Encode(output)}});
                    if (!shell.IsOpen())
                    {
                        shell.Close();
                        SV_LOG_INFO << "터미널 닫기: 노드=" << agent_id;
                        send({{"type", "terminal-exit"}});
                    }
                }
            }
        }
        catch (const std::exception& error)
        {
            if (!stop_token.stop_requested())
            {
                SV_LOG_WARN << "[TERMINAL] Agent terminal channel disconnected after setup/write: "
                          << error.what() << '\n';
            }
        }
        reader.Reset(ioc, stream);
        shell.Close();
        WaitForStop(stop_token, std::chrono::seconds(2));
    }
}
