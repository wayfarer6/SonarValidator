#include "workers/TerminalAgentWorker.hpp"
#include "components/backend_communication/test_websocket_server.hpp"
#include <boost/beast/core.hpp>
#include <nlohmann/json.hpp>
#include <cerrno>
#include <algorithm>
#include <regex>
#include <openssl/evp.h>
#include <iostream>
#include <stdexcept>
#include <sys/wait.h>

using Json = nlohmann::json;

void Require(bool value, const char* message)
{
    if (!value) throw std::runtime_error(message);
    std::cout << "[ok] " << message << std::endl;
}

int main()
{
    const auto decode = [](const std::string& encoded) {
        std::string text(encoded.size(), '\0');
        int length = EVP_DecodeBlock(reinterpret_cast<unsigned char*>(text.data()),
            reinterpret_cast<const unsigned char*>(encoded.data()), static_cast<int>(encoded.size()));
        if (length < 0) throw std::runtime_error("invalid terminal base64");
        for (auto i = encoded.rbegin(); i != encoded.rend() && *i == '='; ++i) --length;
        text.resize(static_cast<std::size_t>(length));
        return text;
    };
    std::stop_source stop;
    TestWebSocketServer server([&](TestWebSocketServer::Socket& peer) {
        const auto read = [&] {
            boost::beast::flat_buffer input;
            peer.read(input);
            return Json::parse(boost::beast::buffers_to_string(input.data()));
        };
        const auto send = [&](const Json& message) { peer.write(boost::asio::buffer(message.dump())); };
        const auto until = [&](const char* type) {
            for (int i = 0; i < 100; ++i)
            {
                const auto message = read();
                if (message.value("type", "") == type) return;
            }
            throw std::runtime_error("expected terminal event was not received");
        };
        Require(read().value("type", "") == "terminal-hello", "terminal handshake identifies the agent");
        send(Json::array());
        until("terminal-error");
        send({{"type", "terminal-open"}});
        until("terminal-ready");
        std::string output;
        while (output.find("$ ") == std::string::npos && output.find("# ") == std::string::npos)
        {
            const auto message = read();
            if (message.value("type", "") == "terminal-output")
                output += decode(message.at("data_base64").get<std::string>());
        }
        output.clear();
        const std::string input = Json{{"type", "terminal-input"}, {"data", "echo SIMPLE_TERMINAL_OK\n"}}.dump();
        peer.write_some(false, boost::asio::buffer(input.data(), input.size() / 2));
        std::this_thread::sleep_for(std::chrono::milliseconds(80));
        peer.write_some(true, boost::asio::buffer(input.data() + input.size() / 2, input.size() - input.size() / 2));
        const auto plain = [](std::string text) {
            // Host shell integration adds OSC metadata around command output.
            text = std::regex_replace(text, std::regex(R"(\x1b\][^\x07\x1b]*(\x07|\x1b\\))"), "");
            text = std::regex_replace(text, std::regex("\x1b\\[[0-?]*[ -/]*[@-~]"), "");
            text.erase(std::remove(text.begin(), text.end(), '\r'), text.end());
            return text;
        };
        while (plain(output).find("\nSIMPLE_TERMINAL_OK\n") == std::string::npos)
        {
            const auto message = read();
            if (message.value("type", "") == "terminal-output")
                output += decode(message.at("data_base64").get<std::string>());
        }
        Require(true, "fragmented input executes and returns the command result");
        send({{"type", "terminal-close"}});
        until("terminal-exit");
        send({{"type", "terminal-open"}});
        until("terminal-ready");
        stop.request_stop();  // Stop while the second shell is still active.
        boost::beast::flat_buffer input_buffer;
        boost::system::error_code ignored;
        peer.read(input_buffer, ignored);
    });
    ProberConfig config("test-terminal", "test-terminal", "Linux", "Ubuntu",
        DeviceType::kVirtualMachine, "Ubuntu", 0, "127.0.0.1", server.Port());
    config.SetTerminalSharedSecret(std::string(40, 'x'));
    TerminalAgentWorker(stop.get_token(), config);
    server.Wait();
    int status = 0;
    Require(waitpid(-1, &status, WNOHANG) == -1 && errno == ECHILD,
            "terminal close and active stop reap their shell children");
}
