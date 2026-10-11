# SonarValidator 전체 기능 검증 계획 (2026-10-10)

## 목적
`./Test/dev.sh` 로 Frontend + Backend 를 기동한 뒤, 프로젝트/VLAN/정책/격리/Agent 터미널/로그/알림 등
모든 화면 기능을 실제 UI 조작과 API·터미널 교차 검증으로 확인하고, 특히 **격리(quarantine) 정책이
서브넷 간 통신을 실제로 막고 해제하면 복구되는지**를 터미널에서 직접 확인한다.

## 실행 환경
- 호스트: Management-Console `172.16.255.245` (osboxes)
- Frontend: `http://172.16.255.245:5173` (Vite dev, `dev.sh`)
- Backend : `http://172.16.255.245:3000` (Spring Boot H2 local 프로필)
- 로그인: `admin` / `.env` 의 `SONAR_ADMIN_PASSWORD`
- 정책 자동 적용: 검증 중 대상 외 장비로의 전파를 막기 위해 `SONAR_POLICY_AUTOMATIC_ENFORCEMENT_ENABLED=false` 로 기동

## 원칙
1. 모든 상태 변경은 프론트엔드 UI 에서 수행하고, API·터미널은 읽기 전용 교차 검증에만 사용한다.
2. 관리망 `172.16.255.0/24` 은 격리 대상에서 제외한다.
3. 격리 테스트는 종료 전 반드시 해제하고 활성 격리 0건을 확인한다.
4. 기존 작업 트리 파일은 수정하지 않는다. 새 스크립트/증적은 `Report-20261010/` 에만 쓴다.

## 테스트 매트릭스
| 단계 | 검증 항목 | 합격 기준 |
|---|---|---|
| A | dev 스택/인증 | 프론트 200, 백엔드 health `UP`, 로그인 후 `/auth/me` 200 |
| B | 정적·단위 회귀 | 프론트 test/build/lint, 백엔드 전체 테스트, Prober CTest 통과 |
| C | 화면 회귀 | 주요 라우트 렌더링, pageerror 0, 예상 밖 API 4xx/5xx 0 |
| D | 인증 가드 | 비로그인 접근 시 `/signin` 리다이렉트 |
| E | 프로젝트/VLAN CRUD·영속성 | 서브넷 등급·이름 저장 후 새로고침 유지 |
| F | 정책 검증·안전장치 | 망분리 검증 결과 표시, Confidential 인터넷 노출 시 푸시 차단 |
| G | 격리 적용 | 131·132·133 = Confidential, Firewall 의 `10.10.131.0/24` 단독 격리, Agent `applied=true` |
| H | 격리 실측 (핵심) | 터미널 ping: 격리 전 131·132 모두 도달 → 격리 후 **131 차단**, 132 유지 |
| I | 해제/복구 | nft 격리 테이블 제거, 131·132 모두 도달, 활성 격리 0건 |
| J | 최종 상태 | 3개 VLAN 등급 유지, 관리망 정상, dev 스택 정상, 증적 저장 |

## 격리 정책 실측 절차 (단계 H · I)
1. Firewall Agent 터미널에서 기준 상태 확인:
   `ping -c2 -W1 10.10.131.10`, `ping -c2 -W1 10.10.132.10` → 둘 다 rc=0.
2. 프론트 `/agent` → Firewall 행 → `서브넷 격리` → `VLAN131` 선택 → `선택한 서브넷 격리`.
3. `/api/v1/quarantine?project_id=...` 에서 `Firewall` / `10.10.131.0/24` `applied=true` 확인.
4. 격리 중 터미널 확인: `nft list table inet sonar_quarantine` 존재, 131 rc≠0, 132 rc=0.
5. 프론트에서 `서브넷 해제` → `applied` 사라짐 확인.
6. 복구 후 터미널: 격리 테이블 없음, 131·132 모두 rc=0.

## 중단·복구 조건
- 관리망 또는 백엔드 접속이 영향을 받는 징후 → 즉시 격리 해제 후 중단
- 대상 외 서브넷 통신 실패 → 중단 및 원인 기록
- 장치 ACK `applied=false` 또는 해제 실패 → 수동 해제 후 보고

## 산출물 (`Report-20261010/`)
- `PLAN.md` (본 문서), `REPORT.md` (최종 결과)
- `evidence/` : 단계별 JSON·터미널 원문
- `screenshots/` : 단계별 화면 캡처
- `logs/` : 백엔드/프론트 기동·테스트 로그

---

# 2차 검증 (2026-10-10, 후속 요청)

## 동기
1차 검증에서 `Test/dev.sh` 가 `.env` 를 로드하지 않아 CORS 때문에 관리 콘솔 IP
(`172.16.255.245:5173`) 로 로그인이 실패했다. 이를 수정하고, 터미널·격리 테스트를 다시 수행하며,
등급(Confidential/Sensitive/Open) 설정과 연결 규칙으로 검증이 통과/실패하는지 확인한다.

## 수정
`Test/dev.sh` 에 `.env` 로더(`load_env_file`)를 추가했다.
- 이미 설정된 환경변수는 덮어쓰지 않는다(명령줄 override 우선).
- dev.sh 가 관리하는 `BACKEND_PORT`/`FRONTEND_PORT`/`DEBUG_PORT` 는 `.env`(docker 용 80/3000)로 덮어쓰지 않는다.
- `--no-env` 로 건너뛸 수 있다.

## 2차 테스트 매트릭스
| 단계 | 검증 항목 | 합격 기준 |
|---|---|---|
| A | 로그인 (172.16.255.245:5173) | CORS 통과, `/auth/me` 200 |
| B | 터미널 3종(방화벽·라우터·스위치) | `uid=0(root)` + 완료 마커 |
| C | 격리 왕복 | 격리 전 131·132 도달 → 격리 후 131 차단·132 유지 → 해제 후 복구 |
| D | 등급 설정 | 131=Confidential, 132=Open, 133=Sensitive 저장·유지 |
| E | 금지쌍 규칙(131→132) | 검증 **실패**, 해당 규칙 위반 보고 |
| F | Sensitive 경유 규칙(131→133, 133→132) | 검증 **통과** (위반 0) |
| G | 원복 | 등급 3건 Confidential 복귀, 추가 규칙 제거 |
| H | 최종 | 활성 격리 0건, 스택 정상 |

## 등급 규칙(서버와 동일)
- `|등급 차이| <= 1` 이면 직접 연결 허용, `>= 2` 면 금지(`ZoneClass.forbidsDirectConnection`).
- 위반 = **연결 규칙(허용 집합)** ∩ **금지쌍 집합**(예: Confidential↔Open).
- 따라서 "연결 규칙을 제대로 추가"한다는 것은 금지쌍을 직접 잇지 않고 Sensitive 를 경유하도록
  규칙을 구성하는 것을 의미한다.

