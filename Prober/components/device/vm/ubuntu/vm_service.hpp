#ifndef SONAR_VALIDATOR_PROBER_VM_SERVICE_HPP_
#define SONAR_VALIDATOR_PROBER_VM_SERVICE_HPP_

#include <nlohmann/json.hpp>

// VM(가상 머신) 전용 상태 수집 서비스입니다.
// VM은 네트워크 제어 대상이 아니며, NIC/연결 상태를 조회해
// 텔레메트리로 전송하는 역할만 담당합니다. (확장성 분리 목적)
class VmService
{
public:
    // NIC 목록(이름/상태/MTU/MAC), IP, 활성 연결 정보를 수집합니다. (루트 불필요, 읽기 전용)
    static nlohmann::json CollectNicStatus();
};

#endif // SONAR_VALIDATOR_PROBER_VM_SERVICE_HPP_
