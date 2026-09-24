#ifndef SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_NIC_HPP_
#define SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_NIC_HPP_

#include <string>
#include <utility>
#include <vector>

#include <nlohmann/json.hpp>

#include "components/network_object/network_interface.hpp"

// 호스트/VM/라우터의 네트워크 인터페이스 카드(NIC) 하나를 표현하는 공통 객체입니다.
//
// `Port` 가 L2 스위치 포트를 담당한다면, `Nic` 은 `ip a`/`ip -br addr` 계열의
// 호스트 관점 인터페이스를 담당합니다. 둘 다 NetworkInterface(추상) 를 상속합니다.
class Nic : public NetworkInterface
{
public:
    // 주소 한 개(IPv4/IPv6)입니다.
    struct Address
    {
        std::string family;      // "inet" / "inet6"
        std::string address;
        std::string prefix_len;
        std::string scope;
        std::string interface;   // 주소 객체가 가리키는 인터페이스 이름(선택)
    };

    std::string name;                       // 인터페이스 이름 (예: eth0, ens3)
    int index = 0;                          // ifindex (가능하면 채웁니다)
    std::string parent;                     // 상위 인터페이스 (예: vlan 상위)
    std::string mac;                        // MAC 주소
    std::string mtu;
    std::string state;                      // up / down / unknown
    std::vector<std::string> flags;         // 예: ["BROADCAST","MULTICAST","UP"]
    std::string link_type;                  // 예: "ether", "internal", "loopback"
    std::vector<Address> addresses;

    Nic() = default;

    explicit Nic(std::string nic_name)
        : name(std::move(nic_name))
    {
    }

    ~Nic() override = default;

    // --- NetworkInterface 구현 ---
    std::string Kind() const override { return "nic"; }
    std::string Name() const override { return name; }
    bool IsInternal() const override { return link_type == "internal"; }

    // NIC 은 별도 VLAN 목록을 보관하지 않습니다.
    // (VLAN 은 스위치 포트/서브인터페이스 쪽에서 다룹니다.)
    VlanIdList VlanIds() const override { return {}; }

    // telemetry_store(NIC 저장)가 기대하는 JSON 형태로 직렬화합니다.
    //  {"index","name","parent","mac","mtu","state","flags":[],"link_type",
    //   "addresses":[{"family","address","prefix_len","scope","interface"}]}
    nlohmann::json ToJson() const
    {
        using nlohmann::json;

        json out;
        out["name"] = name;
        out["index"] = index;
        if (!parent.empty())
        {
            out["parent"] = parent;
        }
        out["mac"] = mac;
        out["mtu"] = mtu;
        out["state"] = state;
        out["flags"] = flags;
        if (!link_type.empty())
        {
            out["link_type"] = link_type;
        }

        json address_array = json::array();
        for (const Address& address : addresses)
        {
            json entry;
            entry["family"] = address.family;
            entry["address"] = address.address;
            entry["prefix_len"] = address.prefix_len;
            entry["scope"] = address.scope;
            if (!address.interface.empty())
            {
                entry["interface"] = address.interface;
            }
            address_array.push_back(std::move(entry));
        }
        out["addresses"] = std::move(address_array);
        return out;
    }
};

#endif // SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_NIC_HPP_