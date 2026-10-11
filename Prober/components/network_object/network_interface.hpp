#ifndef SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_NETWORK_INTERFACE_HPP_
#define SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_NETWORK_INTERFACE_HPP_

#include <string>

#include "components/network_object/vlan.hpp"

// 모든 장비가 공통으로 갖는 "네트워크 인터페이스" 계약입니다.
//
// 포트/ NIC 같은 요소는 스위치(L2) · 라우터(L3) · VM 마다 표현이 조금씩 다르지만
// 이름 · 내부 여부 · VLAN 이라는 공통 요소는 항상 존재합니다. 그 공통 요소를
// 여기(추상 인터페이스)에 모아 두고, 장비별 클래스가 상속해 구체화합니다.
//
// 구현 예:
//   class Port : public NetworkInterface { ... };  // 스위치 포트(L2)
//   class Nic  : public NetworkInterface { ... };  // 호스트/VM NIC(L3)
//
// RoutingTableView(추상) + Frr/CiscoRoutingTableView(구현) 구조와 동일한 패턴입니다.
class NetworkInterface
{
public:
    virtual ~NetworkInterface() = default;

    // 장비/표현 별 종류를 문자열로 돌려줍니다. (예: "port", "nic")
    virtual std::string Kind() const = 0;

    // 인터페이스 이름(예: eth0, Ethernet1, br0). 비어 있을 수 있습니다.
    virtual std::string Name() const = 0;

    // 내부 인터페이스 여부(예: OpenVSwitch type: internal, Linux veth).
    virtual bool IsInternal() const = 0;

    // 이 인터페이스에 배정/허용된 VLAN 번호 목록입니다.
    // 장비별 클래스가 access/trunk 등 자체 표현을 하나의 목록으로 합쳐 돌려줍니다.
    virtual VlanIdList VlanIds() const = 0;

    // 공통 유틸: VLAN 이 하나라도 배정되어 있는지.
    bool HasVlans() const { return !VlanIds().empty(); }

protected:
    NetworkInterface() = default;
};

#endif // SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_NETWORK_INTERFACE_HPP_