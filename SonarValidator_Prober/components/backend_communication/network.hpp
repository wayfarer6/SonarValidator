#ifndef SONAR_VALIDATOR_PROBER_NETWORK_HPP_
#define SONAR_VALIDATOR_PROBER_NETWORK_HPP_

#include "device_type.hpp"

// 네트워크 관련 주요 구조체들을 정리해둔 헤더 파일입니다.

// VLAN 식별자를 감싸는 타입입니다.
struct VLan
{
public:
    explicit VLan(int vlan_id) : vlan_id(vlan_id) {}
    int getVLANID() const { return vlan_id; }

private:
    int vlan_id;
};

// 서브넷 식별자를 감싸는 타입입니다.
struct Subnet
{
public:
    explicit Subnet(int subnet_id);

private:
    int subnet_id;
};

// 네트워크 인터페이스(NIC) 식별자를 감싸는 타입입니다.
struct NIC
{
public:
    explicit NIC(int nic_id);

private:
    int nic_id;
};

#endif // SONAR_VALIDATOR_PROBER_NETWORK_HPP_