#ifndef SONAR_VALIDATOR_PROBER_VM_SERVICE_HPP_
#define SONAR_VALIDATOR_PROBER_VM_SERVICE_HPP_

#include <nlohmann/json.hpp>




class VmService
{
public:
    // NIC 목록(이름/상태/MTU/MAC), IP, 활성 연결 정보를 수집합니다. (루트 불필요, 읽기 전용)
    static nlohmann::json CollectNicStatus();
};

#endif // SONAR_VALIDATOR_PROBER_VM_SERVICE_HPP_
