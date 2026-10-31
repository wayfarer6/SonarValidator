#include "module/management_module/management_service.hpp"
#include "components/backend_communication/connect_with_timeout.hpp"
#include "components/backend_communication/timed_websocket_operation.hpp"
#include "components/terminal/command_runner.hpp"
#include "components/terminal/ios_cli.hpp"
#include "components/terminal/ios_snapshot.hpp"
#include "utils/runtime_environment.hpp"
#include <iostream>
#include <utility>

using Json = nlohmann::json;
using runtime_environment::ShellQuote;
ManagementService::ManagementService() : ManagementService("", 0, "") {}

ManagementService::ManagementService(std::string host, int port, std::string target)
    : host_(std::move(host)), port_(port), target_(std::move(target)), ioc_(), resolver_(ioc_), stream_(ioc_), connected_(false)
{
}

ManagementService::~ManagementService()
{
    cli_session_.Close();
    reader_.Reset(ioc_, stream_);
}

bool ManagementService::connect()
{
    try
    {
        if (host_.empty() || port_ <= 0)
        {
            return false;
        }

        reader_.Reset(ioc_, stream_);
        hello_sent_ = false;
        auto const results = resolver_.resolve(host_, std::to_string(port_));

        const boost::system::error_code ec =
            sonar::net::ConnectWithTimeout(stream_, results, kConnectTimeout, stop_token_);
        if (ec)
        {
            throw boost::system::system_error(ec);
        }

        sonar::net::HandshakeWithTimeout(ioc_, stream_, host_, target_, kHandshakeTimeout);

        connected_ = true;
        return true;
    }
    catch (const std::exception& error)
    {
        std::cerr << "[MGMT] connection failed: " << host_ << ':' << port_
                  << target_ << " (" << error.what() << ")\n";
        reader_.Reset(ioc_, stream_);
        connected_ = false;
        return false;
    }
}

void ManagementService::SetAgentId(std::string agent_id)
{
    agent_id_ = std::move(agent_id);
}

bool ManagementService::SendEnvelope(const nlohmann::json& message)
{
    if (!connected_ && !connect())
    {
        return false;
    }

    try
    {
        sonar::net::WriteWithTimeout(ioc_, stream_, message.dump(), kConnectTimeout);
        return true;
    }
    catch (...)
    {
        connected_ = false;
        return false;
    }
}

bool ManagementService::TryReceive(std::string& message, std::chrono::milliseconds timeout)
{
    if (!connected_) return false;
    boost::system::error_code ec;
    const bool received = reader_.ReadFor(ioc_, stream_, message, timeout, ec);
    if (ec) connected_ = false;
    return received;
}

std::string ManagementService::ResolveAgentId(const std::string& device_id) const
{
    if (!agent_id_.empty())
    {
        return agent_id_;
    }
    return device_id.empty() ? "unknown" : device_id;
}

nlohmann::json ManagementService::fetchPolicy(const DeviceType device_type,
                                              const std::string& device_id,
                                              std::stop_token stop_token)
{
    if (stop_token.stop_possible()) stop_token_ = stop_token;
    if (stop_token.stop_requested()) return nullptr;
    if (!connected_ && !connect())
    {
        return {};
    }

    const std::string agent_id = ResolveAgentId(device_id);

    if (!hello_sent_)
    {
        if (!SendEnvelope(envelope::Hello(agent_id, device_type)))
        {
            return {};
        }
        hello_sent_ = true;

        std::string greeting;
        bool received = false;
        const auto greeting_deadline = std::chrono::steady_clock::now() + kResponseTimeout;
        while (!stop_token.stop_requested() && connected_ &&
               std::chrono::steady_clock::now() < greeting_deadline)
        {
            if (TryReceive(greeting, std::chrono::milliseconds(200))) { received = true; break; }
        }
        if (received)
        {
            try
            {
                const Json reply = Json::parse(greeting);
                if (envelope::IsType(reply, envelope::kError))
                {
                    std::cerr << "[MGMT] hello rejected: " << envelope::ErrorText(reply) << '\n';
                }
            }
            catch (const std::exception&)
            {
                std::cerr << "[MGMT] hello reply was not JSON\n";
            }
        }
    }

    if (stop_token.stop_requested()) return nullptr;
    const Json request = envelope::PolicyRequest(agent_id, device_type, device_id);
    const std::string correlation_id = envelope::CorrelationId(request);

    if (!SendEnvelope(request))
    {
        return {};
    }

    const auto deadline = std::chrono::steady_clock::now() + kResponseTimeout;
    while (std::chrono::steady_clock::now() < deadline)
    {
        if (stop_token.stop_requested())
        {
            return {};
        }

        std::string raw;
        const auto remaining = std::chrono::duration_cast<std::chrono::milliseconds>(
            deadline - std::chrono::steady_clock::now());
        if (!TryReceive(raw, std::min(remaining, std::chrono::milliseconds(200))))
        {
            if (!connected_) break;
            continue;
        }

        Json reply;
        try
        {
            reply = Json::parse(raw);
        }
        catch (const std::exception&)
        {
            std::cerr << "[MGMT] dropped non-JSON frame: " << raw << '\n';
            continue;
        }

        if (envelope::CorrelationId(reply) != correlation_id)
        {
            std::cout << "[MGMT] ignored out-of-band envelope: " << envelope::Type(reply) << '\n';
            continue;
        }

        if (envelope::IsType(reply, envelope::kError))
        {
            std::cerr << "[MGMT] policy request failed: " << envelope::ErrorText(reply) << '\n';
            return {};
        }

        if (envelope::IsType(reply, envelope::kPolicyResponse))
        {
            return envelope::Payload(reply);
        }

        std::cerr << "[MGMT] unexpected reply type: " << envelope::Type(reply) << '\n';
    }

    std::cerr << "[MGMT] policy response timeout (correlation_id=" << correlation_id << ")\n";
    return {};
}

bool ManagementService::ReportPolicyApplied(const DeviceType device_type,
                                            const std::string& device_id,
                                            const std::string& policy_id,
                                            bool applied)
{
    if (!connected_ && !connect())
    {
        return false;
    }

    Json payload;
    payload["device_id"] = device_id;
    payload["policy_id"] = policy_id;
    payload["applied"] = applied;

    return SendEnvelope(envelope::Make(envelope::kAck,
                                       ResolveAgentId(device_id),
                                       envelope::DeviceTypeToString(device_type),
                                       envelope::NextCorrelationId(),
                                       std::move(payload)));
}

bool ManagementService::RunCommand(const std::string& command)
{
    const command_runner::Result result =
        command_runner::RunWithStatus(command, kCommandTimeout, 4 * 1024 * 1024, stop_token_);
    if (!result.completed)
    {
        std::cerr << "[COMMAND] execution timed out or failed to complete\n";
        return false;
    }
    if (result.exit_code != 0)
    {
        std::cerr << "[COMMAND] execution failed with exit code " << result.exit_code << '\n';
        return false;
    }
    return true;
}

std::string ManagementService::RunCommandOutput(const std::string& command)
{
    const command_runner::Result result =
        command_runner::RunWithStatus(command, kCommandTimeout, 4 * 1024 * 1024, stop_token_);
    if (!result.completed)
    {
        std::cerr << "[COMMAND] output command timed out or failed to complete\n";
    }
    else if (result.exit_code != 0)
    {
        std::cerr << "[COMMAND] output command failed with exit code "
                  << result.exit_code << '\n';
    }
    return result.output;
}

bool ManagementService::ApplyPolicyCommand(const PolicyCommand& command) {
    return policy_executors_.Apply(*this, command);
}

std::string ManagementService::QueryAristaCli(const std::string& command)
{
    const std::string script = "enable\n" + command + "\n";
    const std::string pipeline =
        "printf '%s' '" + script + "' | timeout 20 FastCli 2>&1";
    return RunCommandOutput(pipeline);
}

std::string ManagementService::ExecuteIosCli(const std::vector<std::string>& cli_commands)
{
    if (stop_token_.stop_requested()) return {};
    const auto script = ios_cli::BuildScript(cli_commands);
    if (script.empty()) return {};

    const auto check = ios_snapshot::ReadOrExecute(runtime_environment::DataDirectory(), script, [&] {
        return RunCommandOutput("dohost " + ShellQuote(script));
    });
    if (check.session_unavailable)
        std::cerr << "[IOS] IOSP session unavailable and no valid EEM snapshot: " << script << '\n';
    else if (check.failed)
        std::cerr << "[IOS] command failed or returned no usable output: " << script << '\n';
    return check.text;
}


std::string ManagementService::CliCommand(const std::vector<std::string>& argv,
                                          const std::string& command)
{
    if (argv.empty())
    {
        return {};
    }

    // 세션이 없거나 다른 프로그램이면 새로 엽니다.
    if (!cli_session_.IsOpen() || cli_program_ != argv[0])
    {
        cli_session_.Close();
        cli_program_.clear();
        if (!cli_session_.Open(argv))
        {
            std::cerr << "[CLI] failed to open session for " << argv[0] << '\n';
            return {};
        }
        cli_program_ = argv[0];
        // 초기 배너/프롬프트를 소비합니다.
        cli_session_.ReadAvailable(std::chrono::milliseconds(300));
    }

    if (!cli_session_.Write(command))
    {
        return {};
    }
    return cli_session_.ReadAvailable(std::chrono::milliseconds(300));
}
