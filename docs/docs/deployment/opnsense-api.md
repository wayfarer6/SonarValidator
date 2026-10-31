# OPNsense API 연결

OPNsense는 Prober 설치 대신 Backend에서 API로 직접 연결하는 장비입니다. UI의 API 연결 노드와 Agent 목록을 구분합니다.
기준: OPNsenseController, OPNsense 관련 Service, Model/entity의 자격증명·연결 엔티티.

프로젝트 소속, 장비 관리 주소, API Key/Secret을 등록하고 연결 진단 결과를 확인합니다.
Secret은 SONAR_SECRET_KEY로 암호화해 저장하므로 키를 재시작 후에도 유지합니다.
HTTP 허용은 sonar.opnsense.allow-http와 http-origins 설정을 확인합니다. 실제 비밀번호·API Secret을 문서에 적지 않습니다.

이번 Lab 1에는 OPNsense가 없으며 API 연결 시험을 실행하지 않았습니다. 과거 RVI 결과는 [시험 근거](../testing/evidence.md)에서 범위를 확인합니다.
