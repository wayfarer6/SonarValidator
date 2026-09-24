#ifndef SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_VLAN_HPP_
#define SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_VLAN_HPP_

#include <string>
#include <utility>
#include <vector>

// VLAN 하나를 표현하는 공통 네트워크 객체입니다.
//
// L2 스위치(access/trunk), L3 라우터(서브인터페이스), VM(SVI) 처럼 장비마다 VLAN 을
// 다루는 방식은 조금씩 다르지만, "식별자 + 이름 + 모드" 라는 공통 요소는 동일합니다.
// 장비별 클래스는 이 객체를 조합하거나 상속해 세부 동작만 덧붙입니다.
class Vlan
{
public:
    // 포트가 이 VLAN 을 어떻게 다루는지 나타냅니다.
    enum class Mode
    {
        kUnknown,  // 아직 판별되지 않음
        kAccess,   // untagged 로 나가는 access VLAN
        kTrunk,    // tagged 로 통과가 허용된 trunk VLAN
        kNative    // trunk 의 untagged(native) VLAN
    };

    Vlan() = default;

    explicit Vlan(int vlan_id)
        : id_(vlan_id), name_(std::to_string(vlan_id))
    {
    }

    Vlan(int vlan_id, std::string name)
        : id_(vlan_id), name_(std::move(name))
    {
    }

    // 기존 network.hpp 의 VLan::getVLANID() 와 호환되는 접근자입니다.
    int getVLANID() const { return id_; }
    int Id() const { return id_; }
    void SetId(int vlan_id) { id_ = vlan_id; }

    const std::string& Name() const { return name_; }
    void SetName(std::string name) { name_ = std::move(name); }

    Mode GetMode() const { return mode_; }
    void SetMode(Mode mode) { mode_ = mode; }

private:
    int id_ = 0;
    std::string name_;
    Mode mode_ = Mode::kUnknown;
};

// VLAN 번호 목록(access 배정/trunk 허용 목록)을 다룰 때 쓰는 공통 별칭입니다.
using VlanIdList = std::vector<int>;

#endif // SONAR_VALIDATOR_PROBER_NETWORK_OBJECT_VLAN_HPP_