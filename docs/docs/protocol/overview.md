# Backend–Prober 통신 계약

현재 구현은 STOMP가 아닌 WebSocket 텍스트 프레임의 JSON envelope를 사용합니다.
기준: Prober `components/backend_communication/envelope.hpp`, Backend의 `Envelope`와 WebSocket 라우터.

| 필드 | 역할 |
| --- | --- |
| type | hello, policy-request, policy-response, telemetry, command, ack, error |
| agent_id | 배포 예정 등록 이름과 같은 Agent 식별자 |
| device_type | ROUTER, SWITCH, FIREWALL, VM |
| correlation_id | 요청과 응답의 상관 ID |
| payload | 메시지 본문 |
| error | 오류 정보 |

hello 후 ack로 세션을 등록합니다. 정책 요청에는 같은 correlation_id의 응답을 받습니다. telemetry는 일방향 보고입니다.
원격 터미널의 인증·세션은 별도 기능이며 Backend와 Agent의 공유 시크릿이 일치해야 합니다.

수집 payload에는 product, vendor, device_type과 성공한 항목만 포함됩니다.
주요 항목은 nic_status, route_status, arp_table, vlan_status, trunk_status, firewall_rules, ovs_topology입니다.
한 종류 수집 성공을 전체 수집 성공으로 간주하지 않습니다.

현재 상태 확인은 `/api/v1/agents/overview?project_id=...`, 장비별 `/api/v1/agents/{id}/telemetry`와 `/config`를 사용합니다.
온라인 보고와 오프라인 내보내기는 `BuildTelemetryPayload`를 공유합니다.
