# CLI 파서

현재 문법은 Prober components/parser/grammar와 Backend src/main/antlr4 아래에 있습니다.
C++ cli_output_parser.cpp와 수집기 command_collector.cpp가 명령 결과를 nic_status·route_status·arp_table 등으로 정규화합니다.

| 문법 | 주요 대상 |
| --- | --- |
| IpAddr | Linux 주소·라우트·이웃 |
| FrrRouter | FRR/Cisco 라우트·인터페이스 출력 |
| SwitchTopology | Arista 포트·VLAN |
| OvsTopology | OVS 브리지·포트 |
| NftablesRule | nftables 규칙 |

문법을 변경하면 생성된 소스와 런타임 버전을 함께 확인합니다. regenerate_parser 타깃의 실제 스크립트와 도구 경로를 사용합니다.
이번 Lab 1에서는 IOS 원문 → Prober 파싱 → Backend의 CISCO_IOS 중립 설정까지 확인합니다.
조회 실패를 성공으로 처리하거나 오래된 캐시를 현재 결과로 보고하지 않습니다.
