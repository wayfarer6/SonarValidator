# 검증 근거와 범위

현재 Lab 1 결과는 [배포 보고](../deployment/lab1.md)에 기록합니다. 아래는 과거 시험에서 추린 근거입니다. 과거 PASS를 현재 Lab PASS로 합산하지 않습니다.

| 근거 | 확인 내용 | 한계 |
| --- | --- | --- |
| 2026-09-26 전체 통합 보고 | S1–S10 보고상 PASS, 등록·세션·편집·BDD·푸시·이력·로그 | 당시 OPNsense 실장비와 실제 인터페이스 격리 생략 |
| astra-e2e-d-ai-pbl-2 UI 결과 | 8단계 성공, 17 Agent 연결, 서브넷 39·VLAN 행 23, 이름·CSO 저장 유지, page error 0 | 이전 PoC. 로그인 전 auth/me 401 한 건은 정상 |
| 같은 PoC 배포 기록 | 설정·공유 키 일치, 반복 수집, 방화벽 전후 보존 | Cisco와 다른 FRR·OVS·Linux 경로 |
| 2026-10-10 RVI 기록 | Cisco SSH 2222 가능, dohost/IOSP 실패, 정적 바이너리 종료·인증 확인 | Cisco 수집·브라우저 PTY E2E 완료는 아님 |
| RVI OPNsense 진단 | 과거 API 5개 진단 성공 | 현재 방화벽 설정으로 확대 불가 |

`astra-e2e-d-ai-pbl-2`와 `astra e2e-RVI`는 작업 시작 때 사용자가 삭제한 상태였습니다. 복원하지 않고 Git HEAD에서 읽었습니다.
예: `git show 'HEAD:astra-e2e-d-ai-pbl-2/README.md'`.
날짜별 중복 보고서와 이전 접속 정보는 현재 문서에서 제거했습니다.
