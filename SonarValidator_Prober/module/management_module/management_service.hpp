#ifndef SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_
#define SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_

#include <chrono>
#include <string>
#include <vector>
#include "components/backend_communication/network.hpp"
#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>
#include "components/backend_communication/envelope.hpp"
#include "components/device/switch/switch_interface/switch.hpp"
#include "components/device/firewall/firewall_interface/firewall.hpp"
#include "components/device/router/routing_table/routing_table.hpp"
#include "module/configuration_module/prober_config.hpp"
#include "components/terminal/terminal_session.hpp"
#include <thread>
namespace beast = boost::beast;
namespace net = boost::asio;
namespace websocket = beast::websocket;
using tcp = boost::asio::ip::tcp;


// 중앙 서버와 WebSocket으로 통신하며 수신한 정책을 장치에 적용하는 서비스입니다.
//
// STOMP를 사용하지 않고 순수 WebSocket 텍스트 프레임 위에서 envelope.hpp 가 만든
// JSON 봉투를 주고받습니다. 요청과 응답의 짝은 봉투의 correlation_id 로 맞춥니다.
//
// 벤더별 정책 적용(Apply*), 공통 명령 실행(RunCommand), 영속 CLI 세션(CliCommand)을 제공합니다.
class ManagementService {
public:
    ManagementService();
    ManagementService(std::string host, int port, std::string target);
    ~ManagementService();

    bool connect();

    // 서버에 알릴 에이전트 ID 를 설정합니다. (미설정이면 device_id 로 대체)
    void SetAgentId(std::string agent_id);

    // 봉투 하나를 텍스트 프레임으로 전송합니다.
    bool SendEnvelope(const nlohmann::json& message);

    // fetchPolicy 가 응답을 기다리는 최대 시간입니다.
    static constexpr std::chrono::seconds kResponseTimeout{5};

    // 정책을 요청하고(policy-request) 같은 correlation_id 의 응답(policy-response)을 기다립니다.
    // 실패하면 null JSON(Json())을 반환합니다.
    nlohmann::json fetchPolicy(const DeviceType device_type, const std::string& device_id);

    // 정책 적용이 끝났음을 서버에 알립니다. (ack 봉투, 일방향)
    bool ReportPolicyApplied(const DeviceType device_type,
                            const std::string& device_id,
                            const std::string& policy_id,
                            bool applied);

    // 짧은 시간 동안 수신을 시도합니다. 완전한 JSON 텍스트 프레임이면 true 입니다.
    // 서버 푸시(command 등)를 처리하는 수신 루프에서 사용합니다.
    bool TryReceive(std::string& message, std::chrono::milliseconds timeout);

    // 공통 명령 실행 유틸
    bool RunCommand(const std::string& command);
    std::string RunCommandOutput(const std::string& command);

    // IOS-XE guestshell의 dohost 명령으로 IOS CLI를 실행합니다. (인증 불필요)
    std::string ExecuteIosCli(const std::vector<std::string>& cli_commands);

    // Arista vEOS CLI(FastCli) 명령을 실행하고 출력을 돌려줍니다.
    // 수집기(collector)가 `show ...` 조회 명령을 실행할 때 사용합니다.
    // (내부적으로 private CliCommand({"FastCli"}, ...) 에 위임합니다.)
    std::string QueryAristaCli(const std::string& command);

    // 벤더별 정책 적용 (policy_receiver에서 호출)
    bool ApplyOpenVSwitchPolicy(const Json& policy);
    bool ApplyAristaSwitchPolicy(const Json& policy);
    bool ApplyCiscoSwitchPolicy(const Json& policy);
    bool ApplyCiscoRouterPolicy(const Json& policy);
    bool ApplyFrrRouterPolicy(const Json& policy);
    bool ApplyNftablesPolicy(const Json& policy);
    bool ApplyVmPolicy(const Json& policy);

private:
    // 영속 CLI 세션(pty)으로 명령을 보내고 출력을 받습니다.
    std::string CliCommand(const std::vector<std::string>& argv, const std::string& command);

    // 봉투를 만들 때 쓰는 에이전트 식별자입니다. (비어 있으면 device_id 로 대체)
    std::string ResolveAgentId(const std::string& device_id) const;

    std::string host_{};                              // 서버 호스트
    int port_{0};                                     // 서버 포트
    std::string target_{};                            // WebSocket 경로
    net::io_context ioc_;                             // Boost.Asio I/O 컨텍스트
    tcp::resolver resolver_;                          // DNS 리졸버
    websocket::stream<beast::tcp_stream> stream_;     // WebSocket 스트림
    beast::flat_buffer read_buffer_;                  // 수신 프레임 누적 버퍼
    bool connected_;                                  // 연결 상태
    bool hello_sent_{false};                          // hello 봉투 전송 여부
    std::string agent_id_{};                          // 서버에 알릴 에이전트 ID
    std::thread management_thread_;                   // (예약) 관리 스레드
    TerminalSession cli_session_;                     // 영속 CLI 세션(pty)
    std::string cli_program_;                         // 현재 열려 있는 CLI 프로그램명

};

#endif  // SONAR_VALIDATOR_PROBER_MANAGEMENT_SERVICE_HPP_
