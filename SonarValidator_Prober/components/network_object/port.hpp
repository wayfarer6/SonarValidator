#include <iostream>
#include <vector>
class Port{
    std::string name;               // 포트 이름
    std::string interface_name;     // 실제 인터페이스 이름
    std::vector<int> access_vlans;  // access 모드 VLAN 목록
    std::vector<int> trunk_vlans;   // trunk 허용 VLAN 목록
    bool is_internal = false;  
};

// L2인 스위치의 클래스, L3인 라우터, VM의 스위치 관련 기능이 다를꺼기에 좀씩
// 이 부분에 대한 개선도 필요함 아예 NIC 등을 별도로 클래스 분리한 다음에 이걸 상속 받는 식으로
