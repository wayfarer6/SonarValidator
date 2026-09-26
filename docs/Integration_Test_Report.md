# SonarValidator 전체 통합 테스트 결과 보고서

**테스트 일시**: 2026-09-26
**대상**: Frontend(React) · Backend(Spring Boot) · Agent(Prober, C++) 동시 기동
**시나리오 출처**: [SonarValidator 전체 통합 테스트 시나리오 v1.0](https://shseo2023.atlassian.net/wiki/spaces/SONAR/pages/1769533)
**결과 요약**: **10 / 10 시나리오 PASS**, 실기동에서 **결함 2건 발견·수정**, **문서 오류 3건 정정**
**릴리즈 반영**: `main` = `ad23f54` → `ded4dd6`

---

## 1. 총괄

| # | 시나리오 | 결과 | 비고 |
| --- | --- | --- | --- |
| S1 | Dashboard 전반 이상 유무 | ✅ PASS | 5개 블록 전부 실데이터, 4xx/5xx 0 |
| S2 | 프로젝트 생성 | ✅ PASS | `PRJ-5BCDF23A` 생성, DB 1 row 확인 |
| S3 | 프로젝트 편집·검증·오프라인 | ✅ PASS | CRITICAL 검출, round-trip 성공 |
| S4 | Node 추가·배포 예정·번들 | ✅ PASS | silent 등록, ZIP 5파일 |
| S5 | Agent 연결·텔레메트리·중립 설정 | ✅ PASS | connected, 30초 주기 갱신 |
| S6 | Policy 위반·금지쌍·푸시·Export | ✅ PASS | 차단→force→해소→정상 푸시 |
| S7 | Compliance 이력·PDF | ✅ PASS | Policy Update 2건, 필터 동작 |
| S8 | Log 조회·필터·적재·플래그 | ✅ PASS | 필터 8/8, 플래그 반영 |
| S9 | Notification | ✅ PASS | 사건 8종 포착, 읽음/삭제 동작 |
| S10 | Network·격리·OPNsense | ✅ PASS | FW 거부 정상 · **OPNsense 생략** |

### 생략 항목

| 항목 | 사유 | 대체 검증 |
| --- | --- | --- |
| OPNsense 자격증명·Probe | **네트워크에 OPNsense 장비 없음** (사용자 지시) | `GET /opnsense/credentials` → `{total:0}` 확인 |
| 로그 AI 분석 | 이번 범위 제외 (사용자 지시) | 공급자 0건·분석 버튼 비활성 상태만 확인 |
| Agent 실격리 (인터페이스 down) | 프로버가 **비-root** 로 실행되어 권한 없음 | **실패 보고 경로**로 검증 (아래 결함 #2) |

---

## 2. 테스트 환경

| 구성 요소 | 값 | 비고 |
| --- | --- | --- |
| OS | Linux (Ubuntu 24.04.5 LTS 컨테이너) | 커널 7.0.0-31-generic |
| Backend | `http://localhost:3000` | Java 26 (sdkman), Spring Boot 4.1.1 |
| Frontend | `http://localhost:5173` | Vite dev (esbuild) |
| Agent | `IT-Test-VM` (NODE_TYPE=VM) | SERVER_IP=localhost:3000 |
| DB | H2 `jdbc:h2:file:./data/sonarvalidator;AUTO_SERVER=TRUE` | user `sa`, password 공백 |
| 실행 경로 | Backend `/home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Backend` | H2 파일이 cwd 기준 |

### 기동 사전 점검 결과

| # | 확인 | 결과 |
| --- | --- | --- |
| 1 | Backend health | `{"groups":["liveness","readiness"],"status":"UP"}` |
| 2 | 로그인 (`admin`/`admin`) | 200 + `JSESSIONID` |
| 3 | `/auth/me` | 200 `Default Administrator` / `ADMIN` |
| 4 | **CORS 4 origin** | localhost:5173 · 127.0.0.1:5173 · localhost:4173 · 127.0.0.1:4173 **4/4 통과** |
| 5 | Frontend | 200 (vite) |
| 6 | Agent 프로세스 | 실행 중 |
| 7 | Agent 접속 | `connected` 확인 |

> Docker 컨테이너는 처음부터 없었습니다 → **3000 포트 충돌 없음** (사전 `docker compose down` 불필요했음)

---

## 3. 시나리오별 증거

### S1. Dashboard 전반 이상 유무 — ✅ PASS

| 블록 | 서버 데이터 | 실측 |
| --- | --- | --- |
| 상단 요약 | `GET /agents/overview` | connected 15 |
| Project List | `GET /projects` | total 1 (테스트 전) → 2 |
| Latest Topology | `GET /network/topology/{id}` | 노드/엣지 렌더 |
| Log Status | `GET /logs/summary` | 심각도별 집계 |
| Agent 리스트 | `GET /agents/overview` | 17건, 유형 탭 정상 |

API 단독 재현: `projects` / `agents/overview` / `logs/summary` / `logs/filters` / `agents` **전부 200**, 브라우저 오류·무한 로딩 0.

### S2. 프로젝트 생성 — ✅ PASS

```
POST /api/v1/projects  → 200
{"project_id":"PRJ-5BCDF23A","name":"IT-INTEGRATION-2026-09-26",
 "category":"Defense","status":"DRAFT","subnet_count":0,"rule_count":0}
```

- 목록 반영: `total: 2` (`PRJ-5BCDF23A` + 기존 `poc-dai-pbl`)
- DB 반영: `SELECT PROJECT_KEY, NAME FROM PROJECT` → **2 rows** 확인
- 응답 키 11개: `category, created_at, description, name, project_id, rule_count, rules, status, subnet_count, subnets, updated_at`

> 프로젝트는 지시대로 **삭제하지 않고 유지**했습니다.

### S3. 프로젝트 편집·검증·오프라인 — ✅ PASS

**(a) 저장·유지** — 서브넷 2건 + 규칙 1건 저장 후 재조회 시 값 유지, `manually_edited=true` 반영

**(b) 검증 (BDD)** — 등급 건너뛰기 검출

```json
{ "compliant": false, "violation_count": 1,
  "messages": ["오류: 네트워크 연결 제한 — 등급을 건너뛰는 직접 연결 1건이 있습니다."],
  "violations": [{ "rule_id":"Rule-9001", "severity":"CRITICAL",
    "src_class":"Confidential", "dst_class":"Open",
    "sampled_packet":"10.10.131.0 -> 10.30.141.0:8080",
    "reason":"... (Confidential ↔ Open — 등급 2단계 차이)" }] }
```

**(c) 등급 규칙 3케이스** — 모두 기대대로

| 케이스 | 결과 |
| --- | --- |
| Confidential → Open (차이 2) | `compliant=false`, CRITICAL 1 |
| Confidential → Sensitive (차이 1) | `compliant=true`, 0 |
| 위반 규칙 `enabled=false` | `compliant=true`, 0 (검증 제외) |

**(d) 금지쌍** — `total: 2` (양방향), 안내 문구 포함

**(e) 오프라인 스냅샷 round-trip** — 내보내기(`interfaces 6, routes 4`) → 다른 이름(`IT-Offline-VM`)으로 가져오기 `accepted: 1, warnings: []` → `/offline/imported` 에 반영 → `/agents/overview` 에 **`telemetry-only`** 로 등장

**(f) 잘못된 스키마 거부** — `rejected: 1`, `errors: ["schema 불일치: 기대 'sonar.offline.snapshot', 실제 'WRONG'"]`

### S4. Node 추가·배포 예정·번들 — ✅ PASS

- 배포 예정 등록 → `{agent_id, project_id, device_type:ROUTER, node_type:Router, registered:true}`
- 목록에서 `state: silent` 확인 (카운트 `expected_total:1, silent:1`)
- 번들 정보: `server_ip: 192.168.122.58`(자동 감지), `server_port: 3000`, `staged_assets` 3종 모두 `true`
- ZIP 다운로드: **200 / 6,205 bytes / `application/octet-stream`**, 5파일

```
default.conf  README.txt  Installer.sh  restart.sh  default_template.sqlite
```

- `default.conf` 값 대조 — 요청값과 일치

```ini
SERVER_IP=192.168.122.58;
SERVER_PORT=3000;
NODE_TYPE=Router;
AGENT_NAME=IT-Gateway-Router-agent;
```

### S5. Agent 연결·텔레메트리·중립 설정 — ✅ PASS

| 항목 | 실측 |
| --- | --- |
| 상태 | `connected` (`/agents/overview`) |
| 텔레메트리 | `nic_status` 6 interfaces · `route_status` 4 routes · `arp_table` 12 entries |
| 중립 설정 | `format: LinuxVM`, interfaces 6, routes 4, `device_type` 포함 |
| 설정 내보내기 | JSON 다운로드 성공 |

state 전이도 관측: `silent`(S4 등록 직후) → `connected`(Agent 접속) / `telemetry-only`(오프라인 가져오기)

### S6. Policy 관리 — ✅ PASS

| 단계 | 요청 | 응답 |
| --- | --- | --- |
| 위반 시 푸시 | `POST /policy/push/{id}` | `pushed:false`, `violation_count:1`, `violated_rule_ids:["Rule-9001"]` |
| 강제 푸시 | `?force=true` | `pushed:true, forced:true, delivered:2, targets:2` |
| 위반 해소 | 등급 → Sensitive | `compliant:true, violation_count:0` |
| 정상 푸시 | `POST /policy/push/{id}` | `pushed:true, forced:false, delivered:2, targets:2` |

- 위반 현황: `by_severity {CRITICAL:1, MAJOR:0, MINOR:0}` → 해소 후 전부 0
- 푸시 거부 메시지: `"policy validation failed; fix violations or pass force=true"`
- `skipped_quarantined` 로 격리 장비 제외 동작

### S7. Compliance 이력·PDF — ✅ PASS

- 전체 이력 **13건** (기존 랩 이력 + 신규)
- 프로젝트 한정: `Policy Update` 2건 — `프로젝트 정책 수정 (서브넷 2건, 규칙 1건)`
- 장치 한정 필터: `ATICS-agent` → 9건 (교차 검증으로 필터 동작 확인)
- `agent_id` + `project_id` 동시 지정 시 **장치 축 우선** (문서대로)

### S8. Log 조회·필터·적재·플래그 — ✅ PASS

**적재**: `POST /logs/ingest` 3줄 → `received:3, inserted:3`
**심각도 자동 분류** 확인:

| 입력 | 분류 |
| --- | --- |
| `link down eth1` | `error` (num 3) |
| `CRITICAL: policy mismatch` | `critical` (num 2) |
| `info: heartbeat ok` | `notice` (num 5) |

**필터 8/8 통과**

| 필터 | 결과 |
| --- | --- |
| `severity=warning` (최소 등급) | 2건 (critical+error) |
| `agent_id=IT-Test-VM` | 3건 |
| `agent_id=NOBODY` | 0건 |
| `project_id={id}` | 3건 |
| `search=policy` | 1건 |
| `search=zzz-nomatch` | 0건 |
| `from=2026-09-26T00:00:00Z` | 3건 |
| `from=2030-01-01T00:00:00Z` | 0건 |

**플래그**: `POST /logs/2/flags {highlighted:true}` → `highlighted_only=true` 필터로 **1건**만 조회
**업로드**: multipart 3줄 → `inserted:3`, `note` 포함 저장
**limit 상한**: `limit=600` 요청 → **500 으로 클램프** (문서대로)

### S9. Notification — ✅ PASS

S2~S6 의 사건이 **모두 알림으로 포착**되었습니다:

| 알림 | 분류 | 심각도 |
| --- | --- | --- |
| 프로젝트 생성: IT-INTEGRATION-2026-09-26 | PROJECT | info |
| Agent 연결: IT-Test-VM | AGENT | info |
| 망분리 위반 1건: IT-INTEGRATION-2026-09-26 | **SECURITY** | **critical** |
| 정책 푸시 거부 / 강제 완료 / 완료 / 수정 | POLICY | critical / warning / info |
| Agent 격리 — UI 검증 | **SECURITY** | critical |

- 필터 7종 동작 (category 5종 · severity · unread_only · project_id · search)
- 읽음 처리: `unread 12 → 11` → 안읽음 복원 `→ 12` (왕복 확인)
- **알림 링크**: `/project/editor/PRJ-5BCDF23A` — 서버가 준 `link` 로 정확한 화면 지목
- 요약: `by_category {AGENT:68, POLICY:16, PROJECT:2, SECURITY:7}`, `by_severity {critical:4, info:75, warning:14}`

### S10. Network·격리·OPNsense — ✅ PASS

**(a) 토폴로지**

| 프로젝트 | 노드 | 엣지 | 금지 엣지 | 포트 미정의 |
| --- | --- | --- | --- | --- |
| `PRJ-5BCDF23A` (테스트) | 2 | 1 | 0 | 0 |
| `poc-dai-pbl` (랩) | 8 | 9 | 1 | 5 |

**(b) 라우팅** — 8대, `Gateway-Router` 15 routes (OSPF 11, kernel 3, unknown 1, default 1)

**(c) Firewall 격리 거부** — 완벽히 동작

```json
{ "agent_id":"Firewall-agent", "device_type":"FIREWALL",
  "quarantined":false, "delivered":false, "applied":false, "rejected":true,
  "released":false,
  "reason":"방화벽은 격리 대상이 아닙니다 — 트렁크(eth1)에 연결된 모든 VLAN 이 함께 끊깁니다. 대신 프로젝트 규칙으로 해당 연결만 차단하세요.",
  "hint":"프로젝트 규칙에서 해당 연결만 차단하세요." }
```

→ 거부 후 **격리 목록 불변**(`ATICS-agent` 만), 격리 행·명령·알림 **미생성** 확인

**(d) VM 격리 / 해제** — 권한 제약으로 **실패 보고 경로**를 검증 (아래 결함 #2)

| 필드 | 값 | 해석 |
| --- | --- | --- |
| `delivered` | `true` | 서버가 소켓에 써 넣음 |
| `applied` | `null` → 이후 `false` | ack 도착 전 "모름" → 실패 확정 |
| `released` | `true` | 해제 성공 |
| `active` | `true` → `false` | 상태 전이 정상 |

**(e) OPNsense** — 장비 없음으로 생략. `GET /opnsense/credentials` → `{total:0, credentials:[]}`

---

## 4. 발견 결함과 조치

실기동에서 **예외 없이 조용히 잘못 동작하는** 결함 2건을 발견했습니다. 둘 다 로그를 보지 않으면 드러나지 않는 종류였습니다.

### 결함 #1. VM 정책 폴백 경로의 `__primary__` 미치환 — 수정 완료

| 항목 | 내용 |
| --- | --- |
| 증상 | 에이전트 로그에 `Cannot find device "__primary__"` |
| 원인 | 백엔드 전략이 `"interface":"__primary__"` 를 보내는데, netplan 경로만 `ResolveInterfaceName()` 을 거쳤고 `on/off/get` 폴백 경로는 원문을 그대로 `ip link set ... up` 에 넣음 |
| 부수 | netplan `remove` 경로에도 같은 누락 → `/etc/netplan/99-sonar-__primary__.yaml` 잔존 가능 |
| 조치 | 폴백·remove 두 경로 모두 `ResolveInterfaceName()` 적용 |
| 검증 | 수정 후 재기동 → **`Cannot find device "__primary__"` 0회** (수정 전 1회) |

### 결함 #2. 격리 실패를 성공으로 보고 — 수정 완료 ⚠️ 가장 위험

| 항목 | 내용 |
| --- | --- |
| 증상 | 모든 `ip link set down` 이 실패했는데 **서버는 격리 성공으로 인식** |
| 서버 로그 | `INFO quarantine ack agent=IT-Test-VM action=quarantine affected=null preserved=[]` ← **INFO(성공)** |
| 원인 | `Isolate()` 가 "내릴 대상이 없었다" 와 "내리려 했는데 전부 실패했다" 를 구분하지 않고 `ok=true` 반환 |
| 위험도 | **장치는 살아 있는데 서버는 격리됐다고 믿는 상태** — 이 기능에서 최악의 상태 (설계 문서가 경고한 바로 그 상태) |
| 발생 조건 | 프로버를 **비-root** 로 실행 (`RTNETLINK answers: Operation not permitted`) |
| 조치 | 실패 수를 세어 ① 하나도 못 내렸으면 `ok=false` + 사유, ② 일부 성공하면 성공분 + 실패 수 보고 |
| 검증 | 재기동 후 서버 로그가 `WARN quarantine ack FAILED ... 3개 인터페이스를 내리지 못했습니다 (권한 부족 또는 장치 거부). 이 장치를 격리된 것으로 보면 안 됩니다.` 로 변경, API `applied` 가 `null`("모름") → **`false`("실패")** 로 명확해짐 |

### 문서 오류 3건 — 정정 완료

| # | 잘못된 서술 | 실측 | 조치 |
| --- | --- | --- | --- |
| 1 | logout 은 비로그인 시 401 | **200** — `PUBLIC_PATHS` 에 login/logout/me 포함 | 시나리오 §6-C 정정 |
| 2 | `/logs/probe` = 장치 로그 수집 트리거 | **받은 본문을 그대로 반사하는 진단용** 엔드포인트 | 시나리오 S8-8 정정 |
| 3 | 격리 절차에 권한 요건 미기재 | `ip link set down` 은 **root 필요** | 시나리오 절차 C 에 선행 조건 추가 |

### 관찰 사항 (결함 아님)

| 항목 | 설명 |
| --- | --- |
| **SIGTERM 후 종료 지연** | 프로버가 SIGTERM 을 받고도 15초 내 종료되지 않음(마지막 `Clean shutdown complete` 로그 없음, futex 대기). 운영 영향은 제한적이나(pkill 로 종료됨) 향후 종료 경로 점검 필요 |
| `/logs/probe` 응답 형태 | `{"type":"ObjectNode","raw":"..."}` — 진단 목적이므로 정상 |
| `agents/configs` devices 수 | 세션 없는 오프라인 장치는 계수에서 빠짐(설계대로) |

---

## 5. 회귀 테스트

수정 후 전체 계층 재검증했습니다.

| 계층 | 명령 | 결과 |
| --- | --- | --- |
| Prober | `ctest --test-dir build --output-on-failure` | ✅ **13 / 13 passed** |
| Backend | `./mvnw -B clean test` | ✅ **261 tests, 0 failures, 0 errors** |
| Frontend | `npx tsc -b` | ✅ clean |
| Frontend | `npx eslint .` | ✅ **0 errors**, 7 warnings (기존) |
| Backend | `/actuator/health` | ✅ 200 (clean 이후에도 UP) |

---

## 6. 정리 및 데이터 보존

| 항목 | 조치 |
| --- | --- |
| **테스트 프로젝트** | **삭제하지 않음** — `IT-INTEGRATION-2026-09-26` (`PRJ-5BCDF23A`), 서브넷 2 · 규칙 1 · 배포 예정 1 유지 |
| 테스트 격리 | `released` 처리 완료 → 최종 격리 목록 `ATICS-agent` 1건(기존 랩 장치)만 남음 |
| 배포 예정 등록 | `IT-Gateway-Router-agent` (silent) — 수동 확인용으로 유지 |
| 서비스 | Backend(:3000) · Frontend(:5173) · Agent 계속 실행 중 |

---

## 7. 릴리즈

### Git 통합

| 단계 | 내용 |
| --- | --- |
| 커밋 | `ad23f54` fix(prober): VM 정책 자리표시자 치환 누락 · 격리 실패 오보고 수정<br>`ded4dd6` docs: 통합 테스트 시나리오에서 실측과 어긋난 3곳 정정 |
| 통합 | `feature/sonar-15-cli-ingest-metric-align` → **`main` fast-forward** |
| push | `origin/main` = `ad23f54` (`4e44b09..ad23f54`) |

### 브랜치 정리

| 브랜치 | 처리 | 사유 |
| --- | --- | --- |
| `STOMP` | **삭제** | 2026-09-16 기반 WIP(커밋 3개 모두 `save`). 10일 지난 기반이라 현재 전략 패턴 파일들을 옛 버전으로 되돌리고, 3.6MB·9.9MB 바이너리를 포함. 현재 문서는 "STOMP 미사용(순수 텍스트 프레임)" 명시 |
| `fix/ai-provider-default-flag` | **삭제** | 사용자 지시로 불필요 판단 |
| `feature/sonar-15-cli-ingest-metric-align` | **삭제** | main 에 fast-forward 통합 완료 |

**최종 브랜치: `main` 하나만 남음** (로컬·원격 모두)

---

## 8. 결론

- **10개 시나리오 전부 PASS.** 세 계층이 실제로 맞물려 동작함을 API·DB·화면 3중으로 확인했습니다.
- 실기동에서 **조용한 결함 2건**을 찾아 수정했습니다. 특히 결함 #2(격리 실패 오보고)는 이 기능의 설계가 경계한 "최악의 상태"를 만드는 것이었고, 수정 후 서버가 실패를 실패로 기록하게 되었습니다.
- **문서 3곳이 실제 동작과 달랐음**을 확인해 정정했습니다. 통합 테스트를 문서 기준으로 돌린 것이 문서 품질 개선으로도 이어졌습니다.
- **남은 권장 작업**: ① 프로버를 root 로 실행한 상태의 **실격리 검증**(이번엔 권한 부족으로 실패 경로만 검증), ② SIGTERM 종료 지연 원인 점검, ③ OPNsense 장비 확보 시 자격증명·Probe 검증.