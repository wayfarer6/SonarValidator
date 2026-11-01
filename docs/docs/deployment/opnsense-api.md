# OPNsense API 연결

OPNsense는 Prober 설치 대신 Backend에서 API로 직접 연결하는 장비입니다. UI의 API 연결 노드와 Agent 목록을 구분합니다.
기준: OPNsenseController, OPNsense 관련 Service, Model/entity의 자격증명·연결 엔티티.

프로젝트 소속, 장비 관리 주소, API Key/Secret을 등록하고 연결 진단 결과를 확인합니다.
Secret은 SONAR_SECRET_KEY로 암호화해 저장하므로 키를 재시작 후에도 유지합니다.
HTTP 허용은 sonar.opnsense.allow-http와 http-origins 설정을 확인합니다. 실제 비밀번호·API Secret을 문서에 적지 않습니다.

Lab2와 Lab3에서는 기존 관리 IP와 API 자격증명을 각 프로젝트에 등록합니다. [Lab1~4 기능 시나리오](../testing/labs-1-4.md)의 연결·펌웨어·인터페이스·규칙·NAT·별칭 검사를 사용합니다. 과거 결과와 현재 실행 결과는 [시험 근거](../testing/evidence.md)에서 구분합니다.

자체 서명 인증서 허용은 해당 연결의 SSLContext에만 적용합니다. IP와 인증서 이름이 다른 Lab 장비를 포함하며, 기본 TLS 클라이언트는 계속 검증합니다. 전역 호스트 이름 검증 속성은 변경하지 않습니다.
