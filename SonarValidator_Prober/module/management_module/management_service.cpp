#include "utils/log.hpp"
#include "module/management_module/management_service.hpp"
#include "components/backend_communication/connect_with_timeout.hpp"
#include "components/backend_communication/timed_websocket_operation.hpp"
#include "components/terminal/command_runner.hpp"
#include "components/terminal/ios_cli.hpp"
#include "components/terminal/arista_cli.hpp"
#include "components/terminal/ios_snapshot.hpp"
#include "components/terminal/ios_eem_bridge.hpp"
#include "utils/runtime_environment.hpp"
#include <iostream>
#include <utility>

using Json = nlohmann::json;
using runtime_environment::ShellQuote;
ManagementService::ManagementService() : ManagementService("", 0, "") {}

ManagementService::ManagementService(std::string host, int port, std::string target)
    : host_(std::move(host)), port_(port), target_(std::move(target)), ioc_(), resolver_(ioc_), stream_(std::make_unique<Stream>(ioc_)), connected_(false)
{
}

ManagementService::~ManagementService()
{
    reader_.Reset(ioc_, *stream_);
}

bool ManagementService::connect()
{
    try
    {
        if (host_.empty() || port_ <= 0)
        {
            return false;
        }

        reader_.Reset(ioc_, *stream_);
        stream_ = std::make_unique<Stream>(ioc_);
        hello_sent_ = false;
        auto const results = resolver_.resolve(host_, std::to_string(port_));

        const boost::system::error_code ec =
            sonar::net::ConnectWithTimeout(*stream_, results, kConnectTimeout, stop_token_);
        if (ec)
        {
            throw boost::system::system_error(ec);
        }

        sonar::net::HandshakeWithTimeout(ioc_, *stream_, host_, target_, kHandshakeTimeout, stop_token_);

        connected_ = true;
        SV_LOG_INFO << "관리 서버 연결: " << host_ << ':' << port_;
        return true;
    }
    catch (const std::exception& error)
    {
        SV_LOG_WARN << "[MGMT] connection failed: " << host_ << ':' << port_
                  << target_ << " (" << error.what() << ")\n";
        reader_.Reset(ioc_, *stream_);
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
        sonar::net::WriteWithTimeout(ioc_, *stream_, message.dump(), kConnectTimeout, stop_token_);
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
    if (!pending_commands_.empty()) {
        message = std::move(pending_commands_.front());
        pending_commands_.pop_front();
        return true;
    }
    return ReadFrame(message, timeout);
}

bool ManagementService::PreserveCommand(const std::string& raw, const Json& message)
{
    if (!envelope::IsType(message, envelope::kCommand)) return true;
    if (pending_commands_.size() >= 32) {
        SV_LOG_WARN << "관리 명령 대기 한도 초과: 재접속 후 정책 재조회";
        connected_ = false;
        return false;
    }
    pending_commands_.push_back(raw);
    return true;
}

bool ManagementService::ReadFrame(std::string& message, std::chrono::milliseconds timeout)
{
    if (!connected_) return false;
    boost::system::error_code ec;
    const bool received = reader_.ReadFor(ioc_, *stream_, message, timeout, ec);
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
            if (ReadFrame(greeting, std::chrono::milliseconds(200))) { received = true; break; }
        }
        if (received)
        {
            try
            {
                const Json reply = Json::parse(greeting);
                if (!PreserveCommand(greeting, reply)) return {};
                if (envelope::IsType(reply, envelope::kError))
                {
                    SV_LOG_WARN << "[MGMT] hello rejected: " << envelope::ErrorText(reply) << '\n';
                }
            }
            catch (const std::exception&)
            {
                SV_LOG_WARN << "[MGMT] hello reply was not JSON\n";
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
        if (!ReadFrame(raw, std::min(remaining, std::chrono::milliseconds(200))))
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
            SV_LOG_WARN << "관리 메시지 해석 실패: JSON 형식 오류" << '\n';
            continue;
        }

        if (envelope::CorrelationId(reply) != correlation_id)
        {
            if (!PreserveCommand(raw, reply)) return {};
            continue;
        }

        if (envelope::IsType(reply, envelope::kError))
        {
            SV_LOG_WARN << "[MGMT] policy request failed: " << envelope::ErrorText(reply) << '\n';
            return {};
        }

        if (envelope::IsType(reply, envelope::kPolicyResponse))
        {
            return envelope::Payload(reply);
        }

        SV_LOG_WARN << "[MGMT] unexpected reply type: " << envelope::Type(reply) << '\n';
    }

    SV_LOG_WARN << "정책 응답 대기 시간 초과";
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
        SV_LOG_WARN << "[COMMAND] execution timed out or failed to complete\n";
        return false;
    }
    if (result.exit_code != 0)
    {
        SV_LOG_DEBUG << "[COMMAND] execution failed with exit code " << result.exit_code << '\n';
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
        SV_LOG_WARN << "[COMMAND] output command timed out or failed to complete\n";
    }
    else if (result.exit_code != 0)
    {
        SV_LOG_WARN << "[COMMAND] output command failed with exit code "
                  << result.exit_code << '\n';
    }
    return result.output;
}

bool ManagementService::ApplyPolicyCommand(const PolicyCommand& command) {
    return policy_executors_.Apply(*this, command);
}

std::string ManagementService::QueryAristaCli(const std::string& command)
{
    const std::string invocation = arista_cli::BuildCommand({command});
    if (invocation.empty()) return {};
    const auto result = command_runner::RunWithStatus(
        invocation, kCommandTimeout, 4 * 1024 * 1024, stop_token_);
    if (!result.completed || result.exit_code != 0)
    {
        SV_LOG_WARN << "[ARISTA] query failed or was interrupted: " << command << '\n';
        return {};
    }
    return result.output;
}

std::string ManagementService::ExecuteIosCli(const std::vector<std::string>& cli_commands)
{
    if (stop_token_.stop_requested()) return {};
    const auto script = ios_cli::BuildScript(cli_commands);
    if (script.empty()) return {};

    const auto check = ios_eem_bridge::Available()
        ? ios_cli::CheckOutput(ios_eem_bridge::Execute(cli_commands, stop_token_))
        : ios_snapshot::ReadOrExecute(runtime_environment::DataDirectory(), script, [&] {
            return RunCommandOutput("dohost " + ShellQuote(script));
        });
    if (check.session_unavailable)
        SV_LOG_WARN << "Cisco 수집 실패: IOS 세션과 유효한 EEM 스냅샷 없음" << '\n';
    else if (check.failed)
        SV_LOG_WARN << "Cisco 명령 실패: 사용 가능한 출력 없음" << '\n';
    return check.text;
}
