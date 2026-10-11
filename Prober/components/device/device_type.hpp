#ifndef SONAR_VALIDATOR_PROBER_DEVICE_TYPE_HPP_
#define SONAR_VALIDATOR_PROBER_DEVICE_TYPE_HPP_

#include <cstdint>

// 프로버가 관리하는 장치(노드)의 종류를 나타내는 공통 타입입니다.
// 설정(ProberConfig), 초기화(init), 관리 서비스(ManagementService),
// 스위치/방화벽/라우터 처리 등 프로젝트 전반에서 공유되므로
// 단일 헤더에 정의하여 중복 정의를 방지합니다.
enum class DeviceType : std::uint8_t
{
    kSwitch,
    kVirtualMachine,
    kFirewall,
    kRouter
};

#endif // SONAR_VALIDATOR_PROBER_DEVICE_TYPE_HPP_
