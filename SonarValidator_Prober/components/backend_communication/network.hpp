#ifndef SONAR_VALIDATOR_PROBER_NETWORK_HPP_
#define SONAR_VALIDATOR_PROBER_NETWORK_HPP_

#include "components/device/device_type.hpp"
#include "components/network_object/vlan.hpp"








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
