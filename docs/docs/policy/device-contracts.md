# 장비별 수집과 정책 적용

| 장비 | 수집 | 적용과 확인점 |
| --- | --- | --- |
| Cisco IOS XE | dohost 또는 신선한 EEM CLI 스냅샷 | IOS ACL·인터페이스 바인딩, EEM 큐는 desired/applied 확인 |
| FRR | Linux ip·vtysh | 명령 지원과 실제 적용을 별도 검증 |
| Arista | FastCli·포트·VLAN | VLAN·스위치포트·ACL, 관리 경로 보존 |
| Open vSwitch | ovs-vsctl·Linux 상태 | OpenFlow·브리지·포트 대상 확인 |
| Linux VM | ip addr/route/neigh | 배포판별 네트워크 백엔드와 권한 |
| nftables | Linux 상태·ruleset | 전용 테이블·체인, 기존 규칙 보존 |
| OPNsense | Backend의 인증된 HTTP(S) API | 엔드포인트·자격증명·적용 결과 확인 |

현재 계약은 Backend `Policy/strategy`와 Prober `module/management_module/management_service.cpp`를 기준으로 합니다. 과거 JSON 예제만으로 장비 명령을 재현하지 않습니다.

Cisco EEM 수집 성공은 ACL 적용용 EEM이 설정됐다는 뜻이 아닙니다. [Lab 1 배포](../deployment/lab1.md)에서 수집과 집행 범위를 구분합니다.
