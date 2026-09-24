#ifndef SONAR_VALIDATOR_PROBER_SWITCH_PORT_INFO_HPP_
#define SONAR_VALIDATOR_PROBER_SWITCH_PORT_INFO_HPP_

#include "components/network_object/port.hpp"

// 스위치 포트 정보입니다. 공통 네트워크 객체인 Port 를 상속합니다.
//
// 부모의 생성자를 그대로 물려받아 `PortInfo{name, "", {}, {}, false}` 형태의
// 집합 초기화가 그대로 동작합니다.
class PortInfo : public Port
{
public:
    using Port::Port;

    PortInfo() = default;

    ~PortInfo() override = default;
};

#endif // SONAR_VALIDATOR_PROBER_SWITCH_PORT_INFO_HPP_