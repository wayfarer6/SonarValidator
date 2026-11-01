# 검증 근거와 범위

현재 실행은 [Lab1~4 기능 시나리오](labs-1-4.md)와 tests/robot/suites/lab_features.robot를 기준으로 기록합니다. 각 Lab을 실제로 시작하고 수집 시각과 통신 경로를 확인한 결과만 해당 Lab의 근거로 사용합니다.

| 자료 | 확인 내용 | 해석 범위 |
| --- | --- | --- |
| report.html, output.xml | 시나리오별 통과·실패·건너뜀 | 실행한 시나리오만 |
| videos, screenshots | 실제 브라우저 조작·응답 | 시나리오별 원본, 재구성 화면 아님 |
| evidence/collection.json | 소속·제품·NIC·경로·VLAN·수집 시각 | 최근 수집과 전원 꺼진 Lab의 온라인 표시 검증 |
| evidence/network.json | 인터넷·관리 경로·분리·장비 API | 실제 실행한 목적지·프로토콜만 |
| evidence/ai-readonly-execution.json | 명령 계획과 실제 결과 | 모의 AI 제안, 실제 조회, 변경 없음 |
| downloads/*-policy.json | BDD 판정과 분석 범위 | POLICY_HEADER_SPACE, data_plane_analyzed=false |
| evidence/processes.json | Prober 스레드 수와 터미널 작업 정리 | 종료 시점의 상태 |

Backend/Prober/Frontend 단위·통합 테스트는 장비 변경 실행기의 실패·권한·중복 요청·read-back 불일치를 검증합니다. 실제 설정 변경 성공이나 전 장비 정책 집행 완료로 확대하지 않습니다.

최종 발표 자료는 검증 화면과 대표 녹화 구간을 사용합니다. AI 응답은 모의 공급자임을 표시합니다. 전체 녹화와 시나리오 문서는 발표용 짧은 구간과 별도로 보관합니다. 과거 PoC의 PASS 수치는 현재 실행 결과에 합산하지 않으며 원본은 Git 이력에서 확인합니다.
