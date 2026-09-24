#ifndef SONAR_VALIDATOR_PROBER_NETWORK_HPP_
#define SONAR_VALIDATOR_PROBER_NETWORK_HPP_

#include "components/device/device_type.hpp"
#include "components/network_object/vlan.hpp"

// 네트워크 관련 주요 구조체들을 정리해둔 헤더 파일입니다.
//
// 장비별로 중복 정의되던 공통 네트워크 요소(포트/VLAN/NIC)는
// components/network_object/ 의 추상 인터페이스(NetworkInterface) 와 구현체
// (Port / Nic / Vlan) 로 옮겼습니다. 여기서는 예전 이름을 그대로 쓸 수 있도록
// 별칭만 제공합니다.

// VLAN 식별자를 감싸는 타입입니다. (공통 객체 Vlan 의 별칭)
using VLan = Vlan;

// 서브넷 식별자를 감싸는 타입입니다.
struct Subnet
{
public:
    explicit Subnet(int subnet_id);

private:
    int subnet_id;
};

// 네트워크 인터페이스(NIC) 식별자를 감싸는 타입입니다.
// (인터페이스 자체의 상태/주소를 담는 데이터 객체는 components/network_object/nic.hpp 의 Nic)
struct NIC
{
public:
    explicit NIC(int nic_id);

private:
    int nic_id;
};

#endif // SONAR_VALIDATOR_PROBER_NETWORK_HPP_