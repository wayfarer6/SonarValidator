# SonarValidator 최종 검증 결과 (2026-10-10)

## 판정

**조건부 실패 — 릴리스 전 수정이 필요한 High 버그 1건이 재현됨.**

VLAN 131·132·133의 Confidential 저장, 정책 안전장치, VLAN 131 단독 격리, VLAN 132 비영향, 격리 해제와 통신 복구는 모두 정상이다. 다만 프로젝트 편집기의 CIDR 초안 검증이 잘못된 IPv4/CIDR을 유효한 서브넷처럼 계속 처리한다.

## 실행 환경

- 실행: `SONAR_POLICY_AUTOMATIC_ENFORCEMENT_ENABLED=false ./Test/dev.sh --no-debug`
- 프론트엔드: `http://172.16.255.245:5173` — HTTP 200
- 백엔드: `http://172.16.255.245:3000` — actuator health `UP`
- 테스트 프로젝트: `PRJ-AADE1BC3` (`test`)
- 최종 Agent 상태: 16 connected / 18 total / 1 silent
- 최종 활성 격리: 0건

자동 정책 적용은 검증 중 대상 외 장비에 정책이 전파되지 않도록 껐다. 격리·해제 동작은 프론트엔드 UI에서 직접 요청하고 Agent 터미널에서 실제 효과를 교차 확인했다.

## 결과 요약

| 영역 | 결과 | 증거 |
|---|---:|---|
| 프론트 단위 테스트 | PASS | 3/3 |
| 프론트 빌드 | PASS | production build 완료 |
| 프론트 lint | PASS (경고) | error 0, 기존 `react-refresh` warning 7 |
| 백엔드 테스트 | PASS | 441 tests, failure/error/skip 0 |
| Prober CTest | PASS | 23/23 |
| 인증 및 주요 화면 | PASS | 15개 route, page error 0, 예상 밖 API error 0 |
| 인증 가드 | PASS | 비로그인 `/agent` → `/signin` |
| VLAN 영속성 | PASS | 131·132·133 = Confidential, 새로고침 후 유지 |
| 정책 안전장치 | PASS | 인터넷 노출 CRITICAL 3건, `정책 푸시` 비활성화 |
| 실제 VLAN 격리 | PASS | VLAN 131 실패(rc=1), VLAN 132 정상(rc=0), Agent `applied=true` |
| 격리 해제/복구 | PASS | nft 격리 테이블 제거, VLAN 131/132 모두 rc=0 |
| Agent 격리 UI 가드 | PASS | VM 미지원 표시, 프로젝트 미할당 라우터 버튼 비활성화 |
| 잘못된 CIDR 거부 | **FAIL** | 4/4 입력이 HTTP 200이며 invalid 오류 없음 |

## 발견 버그

### BUG-CIDR-VALIDATION-001 — High

프로젝트 편집기에서 잘못된 CIDR을 입력하고 `검증 실행`을 눌러도 서버가 HTTP 200을 반환하며 입력 오류를 알리지 않는다. 오히려 잘못된 주소를 인터넷 노출 위반과 샘플 패킷에 그대로 포함한다.

재현 입력은 모두 동일하게 실패했다.

| 입력 | 응답 | invalid 표시 | 원문 재사용 |
|---|---:|---:|---:|
| `10.10.131.999/24` | 200 | 없음 | 있음 |
| `10.10.131.0/33` | 200 | 없음 | 있음 |
| `10.10.131/24` | 200 | 없음 | 있음 |
| `not-an-ip/24` | 200 | 없음 | 있음 |

입력은 저장하지 않았고, 테스트 뒤 영속 CIDR이 `10.10.131.0/24`로 유지됨을 확인했다.

코드상 원인 범위:

- 프론트 CIDR 입력은 일반 text input이며 입력 제약/오류 표시가 없다.
- 백엔드 검증 엔진은 CIDR이 비었는지만 검사한다.
- CIDR 파서는 잘못된 주소를 정상적으로 예외 처리하지만, `normalizeCidr`가 예외를 삼키고 원문을 돌려준다. 따라서 검증 보고서까지 잘못된 원문이 흘러간다.

권장 수정은 저장·초안 검증 공통 경계에서 `PacketVariables.parseCidr` 실패를 명시적인 validation violation 또는 400 응답으로 변환하고, 프론트에서도 같은 형식의 즉시 피드백을 표시하는 것이다. 회귀 테스트에는 위 4개 입력과 정상 네트워크 정규화 사례를 함께 넣어야 한다.

## 실제 격리 증명

1. 기준 상태: VLAN 131 ping rc=0, VLAN 132 ping rc=0.
2. 프론트엔드에서 Firewall의 `10.10.131.0/24`만 선택해 격리.
3. Agent ACK: `applied=true`, `nft IPv4 subnet quarantine reconciled`.
4. 격리 중: VLAN 131 rc=1, VLAN 132 rc=0.
5. 프론트엔드에서 해제.
6. 복구 후: nft 격리 테이블 없음, VLAN 131 rc=0, VLAN 132 rc=0.

## 참고 경고

- 프론트 빌드에서 빈 `:is()`가 포함된 WebKit scrollbar selector 관련 CSS minifier 경고 3건.
- 500 kB를 넘는 번들 경고가 있으며 가장 큰 main chunk는 약 2.34 MB(minified, gzip 약 634 kB).
- 위 경고들은 이번 기능 검증을 실패시키지는 않았지만 CSS 정리와 코드 분할 후보이다.

## 증적

- 전체 계획: `TEST-PLAN-20261010.md`
- VLAN 격리/해제 결과: `evidence/final-ui-vlan-quarantine-20261010/result.json`
- 화면 회귀 결과: `evidence/frontend-regression-20261010/result.json`
- CIDR 매트릭스 및 Agent 가드: `evidence/cidr-and-agent-guard-20261010/result.json`
- 격리 스크린샷: `screenshots/final-ui-vlan-quarantine-20261010/`
- 화면 회귀 스크린샷: `screenshots/frontend-regression-20261010/`
- CIDR/Agent 가드 스크린샷: `screenshots/cidr-and-agent-guard-20261010/`

## 최종 상태

- VLAN 131: `10.10.131.0/24`, Confidential
- VLAN 132: `10.10.132.0/24`, Confidential
- VLAN 133: `10.10.133.0/24`, Confidential
- 활성 격리 0건
- 프론트와 백엔드 모두 계속 실행 중
- 테스트 도중 생성된 실패 재시도용 스크린샷/텍스트 3개는 혼동 방지를 위해 휴지통으로 이동했다.
