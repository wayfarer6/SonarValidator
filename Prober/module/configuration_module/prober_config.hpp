#ifndef SONAR_VALIDATOR_PROBER_PROBER_CONFIG_HPP_
#define SONAR_VALIDATOR_PROBER_PROBER_CONFIG_HPP_

#include <cstdint>
#include <string>

#include "components/device/device_type.hpp"

// 프로버의 시스템 정보와 중앙 서버 접속 설정을 보관하는 클래스입니다.
// - Get*: 설정 값 조회
// - Set*: 설정 값 변경
// - Detect*: 로컬 시스템에서 값을 자동 탐지
class ProberConfig
{
public:
    ProberConfig(std::string agent_id,
                 std::string agent_name,
                 std::string kernel_name,
                 std::string distribution_name,
                 DeviceType device_type,
                 std::string product_name,
                 std::uint64_t memory_size_bytes,
                 std::string server_ipv4,
                 std::uint16_t server_port);

    const std::string &GetAgentId() const;
    const std::string &GetAgentName() const;
    const std::string &GetKernelName() const;
    const std::string &GetDistributionName() const;
    const std::string &GetProductName() const;
    DeviceType GetDeviceType() const;
    std::uint64_t GetMemorySizeBytes() const;
    const std::string &GetServerIpv4() const;
    std::uint16_t GetServerPort() const;
    const std::string &GetTerminalSharedSecret() const;
    void SetTerminalSharedSecret(std::string terminal_shared_secret);
    void DetectTerminalSharedSecret();

    // 제어평면(관리망) 대역 목록입니다. (쉼표 구분 다중 대역)
    //
    // ⚠️ 격리에서 **절대 차단 대상이 되어서는 안 되는** 대역입니다.
    //    이 대역의 인터페이스를 내리면 서버로 나가는 길이 사라져
    //    해제 명령조차 도달하지 못합니다.
    //
    //    이전에는 quarantine_handler.hpp 의 kManagementPrefix 로
    //    "172.16.255.0/24" 가 박혀 있었습니다. 랩/프로젝트마다 관리 대역이
    //    다를 수 있으므로 default.conf 로 옮겼습니다.
    const std::string &GetManagementPrefixes() const;
    void SetManagementPrefixes(std::string management_prefixes);
    void DetectManagementPrefixes();

    void SetAgentName(std::string agent_name);
    void SetKernelName(std::string kernel_name);
    void SetProduct(std::string product_name);
    void SetDistributionName(std::string distribution_name);
    void SetDeviceType(DeviceType device_type);
    void SetMemorySizeBytes(std::uint64_t memory_size_bytes);
    void SetServerIpv4(std::string server_ipv4);
    void SetServerPort(std::uint16_t server_port);

    const std::string &GetArchitecture() const;
    void SetArchitecture(std::string architecture);

    void DetectKernelName();
    void DetectDistributionName();
    void DetectMemorySizeBytes();
    void DetectArchitecture();
    void DetectServerIpv4();
    void DetectServerPort();
    bool DetectDeviceType();
    void DetectProductName();

    // default.conf 의 AGENT_NAME 을 읽습니다. 없으면 빈 문자열을 돌려주고,
    // 호출자가 자동 생성 이름으로 대체합니다.
    // 설정 파일은 장치마다 하나뿐이므로 정적 함수로 둡니다.
    static std::string DetectAgentName();

private:
    std::string agent_id_;            // 에이전트 고유 ID
    std::string agent_name_;          // 생성된 에이전트 이름
    std::string kernel_name_;         // 커널 버전
    std::string distribution_name_;   // OS 배포판 이름
    std::string product_name_;        // 제품군(OpenVSwitch/Arista/FRR/nftables/Ubuntu/Cisco 8000v 등)
    DeviceType device_type_;          // 장치 유형(스위치/라우터/방화벽/VM)
    std::uint64_t memory_size_bytes_; // 총 메모리 크기(바이트)
    std::string server_ipv4_;         // 중앙 서버 IPv4
    std::uint16_t server_port_;       // 중앙 서버 포트
    std::string terminal_shared_secret_;
    std::string architecture_;        // CPU 아키텍처
    std::string management_prefixes_; // 제어평면(관리망) 대역 (쉼표 구분)
};

#endif // SONAR_VALIDATOR_PROBER_PROBER_CONFIG_HPP_