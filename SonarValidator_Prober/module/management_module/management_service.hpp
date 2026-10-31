#ifndef SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_
#define SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_

#include <chrono>
#include <stop_token>
#include <string>
#include <vector>
#include <nlohmann/json.hpp>
#include "components/backend_communication/network.hpp"
#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>
#include "components/backend_communication/envelope.hpp"
#include "components/backend_communication/timed_websocket_reader.hpp"
#include "components/policy/policy_command.hpp"
#include "module/configuration_module/prober_config.hpp"
#include "components/device/command_executor.hpp"
#include "components/terminal/terminal_session.hpp"
#include "components/policy/policy_executor.hpp"
#include <thread>
namespace beast = boost::beast;
namespace net = boost::asio;
namespace websocket = beast::websocket;
using tcp = boost::asio::ip::tcp;

class ManagementService final : public DeviceCommandExecutor {
    std::stop_token stop_token_;
public:
    ManagementService();
    ManagementService(std::string host, int port, std::string target);
    ~ManagementService();

    bool connect();
    void SetStopToken(std::stop_token token) { stop_token_ = token; }

    void SetAgentId(std::string agent_id);

    bool SendEnvelope(const nlohmann::json& message);

    static constexpr std::chrono::seconds kResponseTimeout{5};

    static constexpr std::chrono::seconds kConnectTimeout{5};
    static constexpr std::chrono::seconds kHandshakeTimeout{5};

    nlohmann::json fetchPolicy(const DeviceType device_type,
                               const std::string& device_id,
                               std::stop_token stop_token = {});

    bool ReportPolicyApplied(const DeviceType device_type,
                            const std::string& device_id,
                            const std::string& policy_id,
                            bool applied);

    bool TryReceive(std::string& message, std::chrono::milliseconds timeout);

    static constexpr std::chrono::seconds kCommandTimeout{20};
    bool RunCommand(const std::string& command) override;
    std::string RunCommandOutput(const std::string& command) override;

    std::string ExecuteIosCli(const std::vector<std::string>& cli_commands) override;

    std::string QueryAristaCli(const std::string& command) override;

    std::string CliCommand(const std::vector<std::string>& argv, const std::string& command) override;
    bool ApplyPolicyCommand(const PolicyCommand& command);
    void RegisterPolicyExecutor(std::shared_ptr<const PolicyExecutor> strategy) {
        policy_executors_.Register(std::move(strategy));
    }

private:
    std::string ResolveAgentId(const std::string& device_id) const;

    std::string host_{};                              // 서버 호스트
    int port_{0};                                     // 서버 포트
    std::string target_{};                            // WebSocket 경로
    net::io_context ioc_;                             // Boost.Asio I/O 컨텍스트
    tcp::resolver resolver_;                          // DNS 리졸버
    websocket::stream<beast::tcp_stream> stream_;     // WebSocket 스트림
    sonar::net::TimedWebSocketReader reader_;
    TerminalSession cli_session_;
    std::string cli_program_;
    PolicyExecutorRegistry policy_executors_ = DefaultPolicyExecutors();
    bool connected_;                                  // 연결 상태
    bool hello_sent_{false};                          // hello 봉투 전송 여부
    std::string agent_id_{};                          // 서버에 알릴 에이전트 ID

};

#endif  // SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_
