#ifndef SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_PORT_HPP_
#define SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_PORT_HPP_

#include <algorithm>
#include <string>
#include <utility>
#include <vector>

#include "components/network_object/network_interface.hpp"

// 스위치/장치의 포트(인터페이스) 하나를 표현하는 공통 네트워크 객체입니다.
//
// 공통 계약은 NetworkInterface(추상) 에 정의되어 있고, 이 클래스가 L2 스위치 포트에
// 맞게 구체화합니다. L3 라우터의 인터페이스나 VM 의 NIC 은 Nic 처럼 별도 클래스를
// 만들어 같은 NetworkInterface 를 상속받는 식으로 확장합니다.
class Port : public NetworkInterface
{
public:
    // 포트 이름 (예: eth0, Ethernet1)
    std::string name;
    // 실제 인터페이스 이름 (예: eth0.100)
    std::string interface_name;
    // access 모드 VLAN 목록
    std::vector<int> access_vlans;
    // trunk 허용 VLAN 목록
    std::vector<int> trunk_vlans;
    // 내부 포트 여부
    bool is_internal = false;

    Port() = default;

    Port(std::string port_name,
         std::string interface,
         std::vector<int> access,
         std::vector<int> trunk,
         bool internal)
        : name(std::move(port_name)),
          interface_name(std::move(interface)),
          access_vlans(std::move(access)),
          trunk_vlans(std::move(trunk)),
          is_internal(internal)
    {
    }

    ~Port() override = default;

    // --- NetworkInterface 구현 ---
    std::string Kind() const override { return "port"; }

    // interface_name 이 있으면 그것을, 없으면 포트 이름을 돌려줍니다.
    std::string Name() const override
    {
        return interface_name.empty() ? name : interface_name;
    }

    bool IsInternal() const override { return is_internal; }

    // access + trunk VLAN 을 중복 없이 합친 목록입니다.
    VlanIdList VlanIds() const override
    {
        VlanIdList ids;
        ids.reserve(access_vlans.size() + trunk_vlans.size());
        for (const int id : access_vlans)
        {
            if (std::find(ids.begin(), ids.end(), id) == ids.end())
            {
                ids.push_back(id);
            }
        }
        for (const int id : trunk_vlans)
        {
            if (std::find(ids.begin(), ids.end(), id) == ids.end())
            {
                ids.push_back(id);
            }
        }
        return ids;
    }
};

#endif // SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_PORT_HPP_
