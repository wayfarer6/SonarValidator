# SonarValidator 전체 통합 테스트 시나리오 v1.0

**대상**: Frontend(React) · Backend(Spring Boot) · Agent(Prober, C++) 를 **동시에 기동**한 상태에서의 통합 검증
**작성일**: 2026-09-26
**전제**: AI 관련 기능(공급자 등록, 로그 AI 분석)은 **이번 검증 범위에서 제외**합니다.
**중요**: 테스트가 끝나도 **프로젝트를 삭제하지 않습니다.** 수동 검증을 위해 그대로 남깁니다. (§8)

---

## 1. 목적과 범위

| 구분 | 내용 |
| --- | --- |
| 검증 목적 | 세 계층이 실제로 맞물려 동작하는지, 화면에 보이는 값이 서버·DB와 일치하는지 확인 |
| 포함 | Dashboard, 프로젝트 CRUD, 서브넷/규칙/검증, Agent 배포·연결·텔레메트리, Policy, Compliance, Log, Notification, Network, 격리, OPNsense 자격증명 |
| 제외 | AI 공급자 등록/분석(`/api/v1/ai/**`, `/api/v1/logs/analyze`) — **버튼 존재 여부만 확인하고 실행하지 않음** |
| 합격 기준 | 10개 시나리오 전부 `PASS`. `FAIL` 1건이라도 있으면 원인 로그와 함께 기록 |

---

## 2. 테스트 환경 기동

### 2.1 포트 · 주소 결정 (⚠️ 가장 먼저 확인)

| 구성 요소 | 주소 | 비고 |
| --- | --- | --- |
| Backend | `http://<hostname>:3000` | **3000을 권장.** Agent `default.conf` 와 Frontend 기본값이 모두 3000 |
| Frontend (dev) | `http://localhost:5173` | `npm run dev` (Vite) |
| H2 Console | `http://localhost:3000/h2-console` | JDBC `jdbc:h2:file:./data/sonarvalidator;AUTO_SERVER=TRUE`, user `sa`, password **빈 값** |
| Agent → Server | `SERVER_IP` / `SERVER_PORT` (기본 3000) | `SonarValidator_Prober/Installer/default.conf` |

- Frontend 는 `.env` 가 없으면 API 주소를 `http://<window.location.hostname>:3000` 으로 잡습니다.
  → **Backend 를 3000 에 띄워야 Frontend 수정 없이 붙습니다.**
- Backend 를 3300 으로 띄우려면 Agent `default.conf` 의 `SERVER_PORT` 를 3300 으로 바꾸고,
  Frontend 는 `VITE_API_BASE_URL=http://localhost:3300` 를 설정해야 합니다.

### 2.2 Docker 정리 (⚠️ 3000 포트 충돌)

`docker compose up` 상태라면 `sonar-backend` 컨테이너가 **3000 을 점유**합니다.
이때 IDE/터미널에서 띄운 백엔드는 포트를 못 잡고, 브라우저는 **조용히 컨테이너와 대화**합니다.

```bash
cd /home/osboxes/IdeaProjects/SonarValidator
docker compose down          # 볼륨은 유지 (데이터 보존)
docker compose ps            # 아무것도 없어야 함
```

### 2.3 Backend 기동

```bash
cd /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Backend
export JAVA_HOME=/home/osboxes/.sdkman/candidates/java/26.0.2-oracle
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B -q spring-boot:run          # 기본 3000 (application.properties)
```

- 반드시 `SonarValidator_Backend/` 에서 실행합니다. H2 파일 경로가 JVM cwd 기준이라
  `data/sonarvalidator.mv.db` 가 이 디렉터리에 생깁니다.
- 기동 후 **약 10초** 기다립니다. devtools 재시작 직후에는 포트가 LISTEN 인데도
  브라우저가 `ERR_CONNECTION_REFUSED` 를 받는 구간이 있습니다.

확인:

```bash
curl -fsS http://localhost:3000/actuator/health && echo OK
```

### 2.4 Frontend 기동

```bash
cd /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Frontend
npm install
npm run dev            # http://localhost:5173
```

- `npm run dev` 는 **esbuild 라 타입 검사를 하지 않습니다.** 통합 테스트 전에 한 번
  `npx tsc -b` 로 타입 오류가 없는지 확인하십시오 (타입 오류가 있으면 화면이 백지가 될 수 있음).

### 2.5 Agent(Prober) 기동

```bash
cd /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober
# 툴체인 경로가 기본값과 다르면 환경변수로 지정
ANTLR4_JAR=$HOME/tools/antlr.jar \
ANTLR4_RUNTIME_ROOT=$HOME/tools/antlr4-install \
./build.sh
```

`build.sh` 가 구성 → 빌드 → 테스트 → 실행까지 수행합니다. 수동으로는:

```bash
cmake -S . -B build -DANTLR4_JAR=$HOME/tools/antlr.jar -DANTLR4_RUNTIME_ROOT=$HOME/tools/antlr4-install
cmake --build build --parallel
ctest --test-dir build --output-on-failure     # 13 tests
./build/sonar_validator_prober
```

Agent 설정 파일 (`Installer/default.conf`) — 서버가 이 값으로 Agent 이름·유형을 식별합니다.

```ini
SERVER_IP=localhost;
SERVER_PORT=3000;
NODE_TYPE=VM;          # Router / Switch / VM / Firewall
DATA_DIRECTORY=;
```

데이터/템플릿 위치를 바꾸려면 환경변수로 덮어씁니다.

```bash
SONAR_DATA_DIR=/tmp/sonar-agent \
SONAR_TEMPLATE_PATH=$PWD/Installer/default_template.sqlite \
./build/sonar_validator_prober
```

⚠️ **종료는 반드시 SIGTERM.** `kill -9` 로 죽이면 SQLite 에 hot journal 이 남아
다음 기동이 `Runtime initialization failed` 로 실패합니다.

### 2.6 기동 확인 체크리스트

| # | 확인 | 명령 | 기대 |
| --- | --- | --- | --- |
| 1 | Backend health | `curl -fsS localhost:3000/actuator/health` | `{"status":"UP"}` |
| 2 | 로그인 | `curl -s -c /tmp/c.txt -X POST localhost:3000/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin"}'` | 200 + 쿠키 저장 |
| 3 | 인증 확인 | `curl -s -b /tmp/c.txt localhost:3000/api/v1/auth/me` | 사용자 정보 |
| 4 | CORS (4개 origin) | 아래 §2.7 | 4개 모두 `Access-Control-Allow-Origin` |
| 5 | Frontend | 브라우저 `http://localhost:5173` | 로그인 화면 |
| 6 | Agent 프로세스 | `pgrep -af sonar_validator_prober` | 1개 이상 |
| 7 | Agent 접속 | `curl -s -b /tmp/c.txt localhost:3000/api/v1/agents` | `connected` 1 이상 |

### 2.7 CORS 사전 점검 (로그인 무한 스피너의 원인)

```bash
for o in localhost:5173 127.0.0.1:5173 localhost:4173 127.0.0.1:4173; do
  echo -n "$o -> "
  curl -s -i -X OPTIONS http://localhost:3000/api/v1/auth/login \
    -H "Origin: http://$o" -H "Access-Control-Request-Method: POST" \
    | grep -i access-control-allow-origin || echo "MISSING"
done
```

4개 전부 헤더가 나와야 합니다. 하나라도 없으면 `sonar.cors.allowed-origins` 기본값의
중괄호가 깨진 상태이므로 **설정을 먼저 고치고** 테스트를 시작하십시오.

---

## 3. 테스트 데이터 원칙

1. **프로젝트는 삭제하지 않습니다.** 시나리오 2에서 만든 프로젝트는 종료 후에도 남겨
   사용자가 수동으로 다시 확인할 수 있게 합니다.
2. 시나리오 2에서 만들 **프로젝트 이름을 고정**하고, 모든 시나리오에서 같은 것을 씁니다.
   예: `IT-INTEGRATION-2026-09-26` / `project_id` 는 서버 생성값을 그대로 사용.
3. 삭제가 꼭 필요한 검증(프로젝트 삭제 API, 격리 해제)은 **별도 임시 객체**로만 수행하고
   본 프로젝트/본 Agent 는 건드리지 않습니다.

---

## 4. 시나리오 요약

| # | 시나리오 | 계층 | 핵심 API |
| --- | --- | --- | --- |
| 1 | Dashboard 전반 이상 유무 | FE+BE | `/projects`, `/agents/overview`, `/logs/summary`, `/network/topology/{id}` |
| 2 | 프로젝트 생성 | FE+BE+DB | `POST /projects` |
| 3 | 프로젝트 편집(서브넷·등급·규칙) 및 검증 + 오프라인 스냅샷 가져오기 | FE+BE | `PUT /projects/{id}`, `POST /projects/{id}/validation`, `POST /offline/import` |
| 4 | Node(Agent) 추가 · 배포 예정 등록 · 번들 다운로드 | FE+BE | `POST /agents/expected`, `GET /agents/bundle/info`, `GET /agents/bundle/{id}` |
| 5 | Agent 연결 · 텔레메트리 · 중립 설정 수집 | Agent+BE | `/agents`, `/agents/overview`, `/{id}/telemetry`, `/{id}/config` |
| 6 | Policy 관리 (위반·금지쌍·푸시·Export) | FE+BE+Agent | `/policy/violations/{id}`, `/policy/push/{id}`, `/policy/forbidden-pairs/{id}` |
| 7 | Compliance 변경 이력 · PDF Export | FE+BE | `/compliance/changes`, PDF |
| 8 | Log 조회 · 필터 · 적재 · 플래그 · Probe | FE+BE | `/logs`, `/logs/filters`, `/logs/ingest`, `/logs/{id}/flags` |
| 9 | Notification (헤더 + 이력 화면) | FE+BE | `/notifications`, `/summary`, `/unread`, `PATCH /{id}/read` |
| 10 | Network 토폴로지·라우팅 + 격리/해제 + OPNsense 자격증명 | FE+BE+Agent | `/network/topology/{id}`, `/routes`, `/quarantine/{id}`, `/opnsense/**` |

---

## 5. 시나리오 상세

### S1. Dashboard 전반 이상 유무 확인

**목적**: 로그인 직후 첫 화면에서 4개 카드와 상단 요약이 **모두 실데이터**로 채워지는지 확인.
(오류 배너·`불러오는 중...` 무한 표시·빈 카드가 하나도 없어야 함)

**절차**

1. 브라우저에서 `http://localhost:5173` 접속 → `admin` / `admin` 로그인.
2. 로그인 후 자동 이동한 `/` 화면에서 아래 5개 블록을 순서대로 확인합니다.

| 블록 | 화면 위치 | 서버 데이터 | 확인 포인트 |
| --- | --- | --- | --- |
| 상단 요약 (Projects / Connected Agents) | 최상단 | `GET /agents/overview` | 연결 Agent 수가 §2.6-7 과 일치 |
| Project List | 좌측 4칸 | `GET /projects` | S2 생성 전이면 0건 + "No project" 안내 |
| Latest Topology | 우측 8칸 | `GET /network/topology/{최신 id}` | 프로젝트 없으면 안내 문구, 있으면 노드/엣지 렌더 |
| Log Status | 전폭 | `GET /logs/summary` | 로그 0건이면 "로그가 없습니다." |
| Agent 리스트 | 전폭 | `GET /agents/overview` | id/이름/IP/대역/장비유형 5열, 유형 탭 동작 |

3. 각 카드에서 **빨간 오류 텍스트가 없는지** 확인합니다.
4. 브라우저 개발자도구 Network 탭에서 API 호출이 **모두 200** 인지 확인합니다.
   (401 이 섞이면 세션 쿠키 문제 → §2.7 부터 재확인)

**판정**

- `PASS`: 5개 블록 모두 렌더 + 4xx/5xx 없음 + 무한 로딩 없음
- `FAIL`: 카드 하나라도 오류/무한 로딩 / 401·500 응답

**확인 명령 (API 단독 재현)**

```bash
for p in projects agents/overview logs/summary logs/filters agents; do
  echo -n "$p -> "; curl -s -o /dev/null -w "%{http_code}\n" -b /tmp/c.txt localhost:3000/api/v1/$p
done
```

---

### S2. 프로젝트 생성

**목적**: 프로젝트 생성이 DB에 실제로 기록되고 목록/상세에 즉시 반영되는지 확인.

**절차**

1. 사이드바 → **Dashboard → Project List** (`/project`) 이동.
2. **Create Project** 버튼 → 모달에서 입력
   - Project Name: `IT-INTEGRATION-2026-09-26`
   - Category: `Defense`
   - Description: `통합 테스트용 (삭제하지 않음)`
3. 저장 → **편집 화면(`/project/editor/{project_id}`)으로 자동 이동**하는지 확인.
   (서버가 준 `project_id` 를 그대로 사용해야 함)
4. 목록으로 돌아와 카드에 아래가 표시되는지 확인
   - 프로젝트 이름 / `project_id` / status / `서브넷 n건 · 규칙 n건` / `Add Agent` / `Manage`
5. DB에 실제 row가 생겼는지 확인 (§6-A H2 콘솔 또는 §6-B 덤프)

**판정**

- `PASS`: 목록에 1건, 상세 조회 시 `subnets`/`rules` 키 존재, DB `PROJECT` 1 row
- `FAIL`: 목록에 안 보임 / 편집 화면 진입 실패 / DB 미기록

**주의**

- 프로젝트 **삭제 버튼은 화면에 없습니다.** 삭제 API(`DELETE /projects/{id}`)만 존재하므로
  이번 시나리오에서는 **삭제하지 않습니다.** (§3-1)
- 생성 본문이 비어도 서버는 초안 프로젝트를 만듭니다. 이름 없는 초안은 나중에 구분이
  안 되므로 반드시 이름을 넣으십시오.

---

### S3. 프로젝트 편집 (서브넷 · 등급 · 규칙) 및 검증

**목적**: 편집한 정책이 저장되고, 망분리 검증(BDD)이 **저장본 기준**으로 돌아 위반을 찾아내는지 확인.

**절차**

1. `Manage` 또는 `/project/editor/{project_id}` 진입.
2. **서브넷 등급 지정**
   - `Add` 로 서브넷 추가 (예: `10.10.131.0/24` → `Confidential`, `10.30.141.0/24` → `Open`)
   - 등급 드롭다운 변경 → `manually_edited` 로 표시되는지 확인
3. **연결 규칙 추가**
   - `Subnet-0131 → Subnet-0141`, port `8080`, protocol `tcp`, enabled
4. **저장 및 검증** 클릭 → 하단 검증 패널 확인
   - `Confidential(3) ↔ Open(1)` 은 **차이 2 → CRITICAL 위반**으로 나와야 정상
   - 위반 테이블에 `rule_id / 심각도 / 경로 / 사유 / 반례 패킷` 열이 채워지는지
5. **금지된 연결 조합** 패널이 표시되는지 (`GET /projects/{id}/forbidden-pairs`)
6. `저장 및 검증` 이 **모두 성공하고 위반 0건일 때만** 목록으로 이동하는지 확인
   (위반이 있으면 화면에 남아야 함 — 의도된 동작)
7. **오프라인 스냅샷 가져오기** (Agent 미접속 환경 보완)
   - `/project/create/subnet?project_id={id}` → `Import Offline Prober Data` 카드
   - Agent가 `--export-offline` 으로 만든 JSON 을 선택 → 스키마 사전검사 통과 → 가져오기 성공

**판정**

- `PASS`: 등급/규칙 저장 후 재조회 시 값 유지, CRITICAL 위반 1건 검출, 금지쌍 표시, 검증 통과 시에만 이탈
- `FAIL`: 저장 후 값이 되돌아감 / 위반 미검출 / 500

**⚠️ 등급 규칙 (판정 공식)**

`|level(A) - level(B)| <= 1` 이면 허용. Confidential=3, Sensitive=2, Open=1.

| 쌍 | 차이 | 결과 |
| --- | --- | --- |
| Confidential ↔ Sensitive | 1 | 허용 |
| Sensitive ↔ Open | 1 | 허용 |
| Confidential ↔ Open | 2 | **금지 (CRITICAL)** |

---

### S4. Node(Agent) 추가 · 배포 예정 등록 · 번들 다운로드

**목적**: 프로젝트에 장비를 붙이는 흐름이 **서버 등록까지** 이어지는지 확인.
(프로버가 아직 접속하지 않아도 서버는 "배포 예정"을 알아야 함)

**절차**

1. `/project` 목록에서 대상 카드의 **Add Agent** 클릭 → 행 아래로 배포 카드가 펼쳐집니다.
2. Management Server IP / Port 입력 (예: `localhost` / `3000`).
3. 장비 카드 중 **FRRouting (for poc)** 선택 → 설정 미리보기(`default.conf` 내용)가
   실제 값으로 만들어지는지 확인 (`SERVER_IP`, `SERVER_PORT`, `NODE_TYPE=Router`).
4. **배포 버튼** 클릭 → 서버에 배포 예정이 등록되는지 확인
   - `POST /api/v1/agents/expected`
   - Agent 목록 화면(`/agent`)에 해당 장비가 **무응답(silent)** 상태로 나타나야 함
5. **번들 다운로드** 확인 (설치 파일 일괄 ZIP)
   - `GET /api/v1/agents/bundle/info?agent_id=<name>&node_type=Router` → 요약 정보
   - `GET /api/v1/agents/bundle/<agentId>?node_type=Router` → ZIP 저장
6. **OPNsense** 를 골랐다면 카드 대신 자격증명 모달이 열리고,
   `PUT /api/v1/opnsense/credentials/{agentId}` 로 저장됩니다 (S10에서 검증).
7. 배포 예정 목록 확인: `GET /api/v1/agents/expected`

**판정**

- `PASS`: expected 등록 후 `/agent` 목록에 silent 로 표시, 번들 ZIP 다운로드 성공(0바이트 아님), 미리보기 값이 입력값과 일치
- `FAIL`: 목록에 안 나옴 / ZIP 실패 / `NODE_TYPE` 이 잘못 들어감

**주의**: Agent 이름은 배포 스크립트 규칙(`<장치>-agent`)과 맞춰야 프로젝트 서브넷의
`agent_id` 와 매칭됩니다. 이름이 다르면 S6 푸시에서 대상이 0건이 됩니다.

---

### S5. Agent 연결 · 텔레메트리 · 중립 설정 수집

**목적**: C++ Agent 가 WebSocket으로 붙어 **30초 주기 텔레메트리**를 올리고, 서버가 이를
벤더 무관 **중립 설정**으로 변환하는지 확인.

**절차**

1. Agent 기동 (§2.5) 후 서버 콘솔 로그에서 `hello` → `ack` 교환을 확인합니다.
2. 상태 확인

```bash
curl -s -b /tmp/c.txt localhost:3000/api/v1/agents
curl -s -b /tmp/c.txt "localhost:3000/api/v1/agents/overview"     # state 확인
curl -s -b /tmp/c.txt "localhost:3000/api/v1/agents/<agentId>/telemetry"
curl -s -b /tmp/c.txt "localhost:3000/api/v1/agents/<agentId>/config"
curl -s -b /tmp/c.txt localhost:3000/api/v1/agents/configs
```

3. `/agent` 화면에서 상태 열 확인

| state | 의미 | 이번 시나리오 기대 |
| --- | --- | --- |
| `connected` | WebSocket 세션 살아 있음 | **최종 목표 상태** |
| `telemetry-only` | 세션은 없지만 수신 이력 있음 | 일시적으로 허용 |
| `silent` | 등록됐지만 접속/수신 없음 | S4 직후 상태 |
| `unregistered` | 예정에 없이 붙은 장치 | 있으면 이름 규칙 확인 |

4. **30초** 이상 대기 → `마지막 수신` 열의 시각이 갱신되는지 확인.
5. `설정 내보내기` 열의 **JSON** 버튼 → `GET /api/v1/offline/export/{agentId}` 산출물이
   다운로드되는지, 인터페이스 0건이면 버튼이 비활성인지 확인.
6. `문제만 보기` 체크박스로 필터가 동작하는지 확인.
7. 인터페이스 수·라우트 수가 화면과 `/{id}/config` 응답에서 일치하는지 대조.

**판정**

- `PASS`: `hello`/`ack` 확인, 상태가 `connected`(또는 `telemetry-only`), 30초 후 `마지막 수신` 갱신, telemetry/config 응답 파싱 가능
- `FAIL`: Agent 프로세스는 살아 있는데 서버에 아무 흔적 없음 / config `null`

**⚠️ 알려진 함정**

- `/api/v1/agents` 는 **지금 살아 있는 세션만** 돌려줍니다. 배포 직후·단절 중에는 0건이 정상이며,
  이때는 `/agents/overview` 를 봐야 합니다.
- 장비 유형은 텔레메트리의 `device_type`(대문자)과 이름 **마지막 토큰**으로 추론됩니다.
  `Gateway-Router` → `ROUTER`.
- 장치가 화면에서 사라지듯 보이면 정책 전략이 예외를 던진 경우입니다.
  전략은 **예외를 던지면 안 되고 null 을 반환**해야 합니다(정책 응답 실패 = 텔레메트리 중단).

---

### S6. Policy 관리 (위반 · 금지쌍 · 푸시 · Export)

**목적**: 검증 결과가 운영 화면에 집계되고, 통과한 정책만 장치로 전송되는지 확인.

**절차**

1. 사이드바 → **Policy Management → View Policy** (`/policy`).
2. 프로젝트 선택 → **위반 현황** 패널 확인
   - `정책 준수` 또는 `위반 N건`, 심각도별 카운트(CRITICAL/MAJOR/MINOR)
   - 위반 테이블: `심각도 / rule_id / 경로 / 사유 / 반례 패킷`
3. **금지 조합** 확인 (`GET /policy/forbidden-pairs/{id}`)
4. **정책 푸시** 시도
   - 위반이 있으면 버튼이 비활성 → 정상 (푸시 차단)
   - 위반을 S3에서 해소한 뒤 다시 시도 → `pushed: true`, `delivered`/`targets` 확인
   - `skipped_quarantined` 에 격리 장비가 제외되는지 확인
5. 푸시 결과 JSON 블록이 화면에 표시되는지 확인
6. **Policy Export** (`/policy/export`)
   - 프로젝트 선택 → **CSV 내보내기** / **JSON 내보내기**
   - 파일에 위반 목록 + 금지쌍 + 집계가 포함되는지 확인

**판정**

- `PASS`: 위반 집계가 S3의 검증 결과와 일치, 위반 시 푸시 차단, 해소 후 `pushed:true`, Export 파일 2종 생성
- `FAIL`: 화면 집계와 검증 결과 불일치 / 위반 상태에서도 푸시 성공 / Export 빈 파일

**참고 (거부 응답 형태)**

```json
{ "pushed": false,
  "reason": "policy validation failed; fix violations or pass force=true",
  "violation_count": 6,
  "violated_rule_ids": ["Rule-9001"] }
```

---

### S7. Compliance 변경 이력 · PDF Export

**목적**: 정책 수정·격리 같은 **운영 행위가 이력으로 남고** 조회·내보내기가 되는지 확인.

**절차**

1. 사이드바 → **Compliance → View Compliance** (`/compliance`).
2. 프로젝트 선택 → 변경 이력 테이블 확인
   - 열: `구분 / 유형 / 변경 요약 / 요청자 / 일시 / 상태`
   - S6에서 정책을 푸시했다면 해당 행위가 이력에 나타나야 함
3. 장치 필터를 바꿔 조회 범위가 좁혀지는지 확인 (`GET /compliance/changes?agent_id=`)
4. **Export Report** (`/compliance/export`) 이동
   - 대상 프로젝트/장치 선택 → **PDF 내보내기**
   - 파일이 열리고 표지·대상·이력 테이블이 포함되는지 확인

**판정**

- `PASS`: 이력에 푸시/수정 행위가 최소 1건, 필터 동작, PDF 정상 생성(0바이트 아님)
- `FAIL`: 이력 0건(행위를 했는데도) / PDF 생성 실패

**⚠️ 함정**: `ComplianceChange` 의 필드명은 `getType()` 입니다(`getChangeType()` 아님).
화면에서 "유형" 열이 비어 있으면 이 계열의 매핑 문제를 의심하십시오.

---

### S8. Log 조회 · 필터 · 적재 · 플래그 · Probe

**목적**: 로그 적재 → 조회 → 필터 → 표시(플래그)까지의 흐름 확인. **AI 분석은 제외.**

**절차**

1. 사이드바 → **Log Management → View Logs** (`/log`).
2. **적재**: `POST /api/v1/logs/ingest` 로 테스트 로그 3줄 주입

```bash
curl -s -b /tmp/c.txt -X POST localhost:3000/api/v1/logs/ingest \
  -H 'Content-Type: application/json' \
  -d '{"agent_id":"IT-INTEGRATION","lines":["link down eth1","CRITICAL: policy mismatch","info: heartbeat ok"]}'
```

3. 목록 새로고침 → 3건이 나타나는지, `truncated` 표시가 있는지 확인.
4. **필터** 순서대로 적용 후 **조회** 버튼
   - 프로젝트 / Agent / 심각도(최소 등급) / 기간 시작·끝 / 본문 검색(Enter)
   - 하나라도 걸리면 결과가 줄어드는지 확인
5. **초기화** 버튼 → 필터가 전부 비워지고 전체 목록 복귀.
6. **표시(플래그)**: 특정 행을 표시한 뒤 `POST /api/v1/logs/{id}/flags` 반영,
   `표시한 로그만`(`highlighted_only`) 필터로 그 행만 나오는지 확인.
7. **파일 업로드**: `POST /api/v1/logs/upload` (multipart) 로 로그 파일 1개 적재.
8. **Probe**: `POST /api/v1/logs/probe` 로 장치 로그 수집 트리거 (가능한 장비에 한함).
9. **AI 영역 확인(실행 금지)**: AI 공급자 드롭다운이 "등록된 공급자 없음"이고
   분석 버튼이 비활성인지 **존재만** 확인합니다. **분석 실행은 하지 않습니다.**

**판정**

- `PASS`: 적재 3건 반영, 필터 5종 각각 동작, 플래그 반영, 업로드 성공, AI 버튼 비활성
- `FAIL`: 적재 후 0건 / 필터 무효 / 500

**⚠️ 함정**

- 로그 조회는 상한이 있습니다(기본 100, 최대 500). 응답의 `truncated` 를 확인하지 않으면
  "데이터가 없다"고 오판합니다.
- `filters` 드롭다운의 장비 목록은 **서버가** 줍니다. 프론트가 별도 API 두 개를 조합하면
  로그가 있는 장비와 연결된 장비가 어긋납니다.

---

### S9. Notification (헤더 + 이력 화면)

**목적**: 사건(정책 푸시/격리/프로젝트 변경/Agent 연결)이 알림으로 발생하고,
읽음 처리·필터·삭제가 동작하는지 확인.

**절차**

1. **헤더 종 아이콘** 클릭 → 최근 알림 드롭다운(`GET /notifications/unread`) 확인.
2. 사이드바 → **Notification → View Notification** (`/notification`).
3. 상단 요약(`GET /notifications/summary`)과 탭별 카운트 확인
   - 탭: 전체 / **정책** / **Agent** / **프로젝트** / **보안** / **시스템**
4. 필터: 프로젝트 / 심각도(critical·warning·info) / 읽음 상태 / 검색어 → 조회·초기화.
5. 개별 **읽음** 처리 → 카운트가 줄고 목록 상태가 바뀌는지 확인
   (`PATCH /notifications/{id}/read`).
6. **모두 읽음** → `POST /notifications/read-all` 후 안읽음 0건.
7. **삭제**: 확인 대화상자 승인 후 사라지는지 확인 (`DELETE /notifications/{id}`).
   ⚠️ 삭제는 되돌릴 수 없으므로 **테스트용 알림만** 지웁니다.
8. **중복 병합 확인**: 같은 사건을 5분 안에 반복시키면 하나의 알림에
   `repeatCount` 가 증가하고 새 알림이 쌓이지 않는지 확인.
9. 알림 행의 **이동 링크** 클릭 → 서버가 준 `link` 로 정확한 화면에 도착하는지 확인.

**판정**

- `PASS`: S6 푸시 또는 S10 격리 후 알림 ≥1건 생성, 탭 카운트 일치, 읽음/모두읽음/삭제 동작, 링크 이동 정상
- `FAIL`: 사건을 일으켰는데 알림 0건 / 카운트 불일치 / 읽음 처리 후에도 카운트 그대로

---

### S10. Network 토폴로지 · 라우팅 + 격리/해제 + OPNsense 자격증명

**목적**: 수집 데이터가 토폴로지/라우팅으로 시각화되는지, 그리고 **격리 조치와 그 해제**가
정확한 상태 필드로 보고되는지 확인.

**절차 A — Network**

1. 사이드바 → **Network Management → View Network** (`/network`).
2. 프로젝트 선택 → **Mermaid 토폴로지**가 렌더되는지 확인
   - 노드 모양으로 등급 구분, 격리/미연결 노드 표시
   - 상단 지표: 금지 엣지 수 / 포트 미정의 엣지 수
3. **라우팅 테이블** 섹션: 장비별 라우트 목록 확인
   - 기본 경로·미선택 경로 위주로 표시되고, 잘린 경우 안내 문구가 나오는지
   - `protocol` 필터(`GET /routes?protocol=`) 동작
4. API 대조: `GET /network/topology/{id}`, `GET /network/discovered[/{id}]`, `GET /routes`

**절차 B — OPNsense 자격증명 (프로버가 아닌 REST 경로)**

5. 배포 카드에서 **OPNsense Firewall** 선택 → 자격증명 모달
6. 저장 → `PUT /api/v1/opnsense/credentials/{agentId}`, 검증 → `POST .../verify`
7. `GET /api/v1/opnsense/probe-targets`, `/candidates` 로 후보 목록 확인
8. 삭제 → `DELETE /api/v1/opnsense/credentials/{agentId}` (확인 대화상자)

**절차 C — 격리 / 해제**

9. `/agent` 화면에서 **격리 가능한 장비**(Router / Switch / VM)를 골라 **격리** 클릭
10. 응답 필드를 **구분해서** 확인합니다.

| 필드 | 의미 | 기대 |
| --- | --- | --- |
| `delivered` | 서버가 소켓에 써 넣었는가 | `true` (연결 시) |
| `applied` | 인터페이스가 실제로 내려갔는가 | ack 후 `true`, ack 전에는 `null` |
| `released` | 해제가 실제로 일어났는가 | 성공·실패 **양쪽 모두** 값이 있어야 함 |
| `preserved` | 살아남은 인터페이스 목록 | 비어 있지 않음 |

11. **Firewall 을 격리 시도** → 아래 거부 응답이 나오고 **격리 행/명령/알림이 생기지 않아야** 정상
    (`{ "rejected": true, "quarantined": false, "reason": ..., "hint": ... }`)
    - 이유: 랩 방화벽은 `eth1` 트렁크에 VLAN 131/132/133 을 함께 물고 있어
      `interface down` 이 3개 존을 동시에 끊습니다.
    - 프론트는 해당 장비의 격리 버튼을 **비활성**으로 표시해야 합니다.
12. 격리 **해제** → `DELETE /api/v1/quarantine/{agentId}` → `released: true` 확인,
    `/agent` 와 `/policy` 격리 목록이 **둘 다** 갱신되는지 확인.
13. Compliance 이력에 `QuarantineRejected` / `Quarantine` 레코드가 남았는지 S7에서 재확인.

**판정**

- `PASS`: 토폴로지·라우팅 렌더, 자격증명 CRUD·검증, Router/Switch/VM 격리 성공, **Firewall 격리 거부**, 해제 성공
- `FAIL`: Firewall 격리가 수행됨 / `applied` 를 `false` 로 오표시 / 해제 후 목록 미갱신

**⚠️ 함정**

- `delivered` 와 `applied` 는 다릅니다. 화면이 두 값을 합쳐 보여주면
  "장치는 살아 있는데 서버는 격리됐다고 믿는" 최악의 상태를 못 잡습니다.
- `applied = null` 은 실패가 아니라 **모름**(ack 미도착)입니다.
- `released` 는 성공 경로에도 반드시 있어야 합니다. 없으면 클라이언트가 `undefined` 를
  실패로 해석해 성공을 정반대로 표시합니다.

---

## 6. 공통 확인 명령

### 6-A. H2 콘솔

`http://localhost:3000/h2-console`

| 항목 | 값 |
| --- | --- |
| JDBC URL | `jdbc:h2:file:./data/sonarvalidator;AUTO_SERVER=TRUE` |
| Driver | `org.h2.Driver` |
| User | `sa` |
| Password | **(빈 값)** |

⚠️ 콘솔 기본값 `jdbc:h2:~/test` 는 **틀린 URL** 입니다. 반드시 위 URL 을 그대로 입력하십시오.
⚠️ 이 DB 계정은 **앱 로그인 계정이 아닙니다.** 앱은 `admin` / `admin`(`SONAR_ADMIN_PASSWORD`)입니다.

주요 테이블: `PROJECT`, `PROJECT_SUBNET`, `PROJECT_RULE`, `AGENT_*`(예정), `NOTIFICATION`,
`COMPLIANCE_CHANGE`, `DEVICE_LOG`, `LOG_ANALYSIS`, `OPNSENSE_CREDENTIAL`, `CONFIGURATION`, `APP_USER`

### 6-B. 스키마·데이터 덤프 (백엔드를 멈추지 않고)

```bash
cd /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Backend
java -cp ~/.m2/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar org.h2.tools.Script \
  -url 'jdbc:h2:file:./data/sonarvalidator;AUTO_SERVER=TRUE' \
  -user sa -password '' -script /tmp/full.ddl -options DROP
```

### 6-C. 세션 쿠키 재사용

```bash
curl -s -c /tmp/c.txt -X POST localhost:3000/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin"}'
# 이후 모든 호출에 -b /tmp/c.txt
```

- `/api/v1/auth/**` 를 제외한 **모든 `/api/**` 는 인증 필요**합니다. 401 이면 쿠키를 확인하십시오.
- `POST /api/v1/auth/logout` 은 **로그인 안 한 상태에서 401** 이 정상입니다(버그 아님).

---

## 7. 실패 시 진단 포인트

| 증상 | 우선 확인 |
| --- | --- |
| 로그인 후 무한 스피너 | CORS 4개 origin (§2.7), `@Value` 기본값 중괄호 |
| 로그인은 되는데 이후 전부 401 | 브라우저 주소가 `127.0.0.1:5173` 인지 / API 가 `:3000` 인지 (쿠키 SameSite) |
| 브라우저만 이상하고 curl 은 정상 | **Docker 컨테이너가 3000 을 점유** → `docker compose down` |
| 기동 직후 `ERR_CONNECTION_REFUSED` | devtools 재시작 직후 → 10초 대기 |
| `/agents` 가 0건 | 정상일 수 있음 → `/agents/overview` 로 확인 |
| 프로젝트 편집 마법사에서 `draft` 배너가 안 나옴 | `ProjectView.forEditing()` 은 현재 미사용 경로 → 기대 동작 |
| 목록엔 있는데 푸시 대상 0건 | Agent 등록 이름 ≠ 서브넷 `agent_id` |
| H2 콘솔 접속 실패 | URL/계정 (§6-A), 백엔드 cwd 가 `SonarValidator_Backend/` 인지 |
| Agent 재기동 실패 (`Runtime initialization failed`) | hot journal → SIGTERM 사용, DB 헤더 확인 (§2.5) |

---

## 8. 종료와 데이터 보존

1. **프로젝트를 삭제하지 않습니다.** 생성한 프로젝트(`IT-INTEGRATION-2026-09-26`)와
   그 서브넷·규칙·Agent 등록을 그대로 남깁니다. → 사용자가 수동으로 재검증할 수 있게 하기 위함.
2. 격리했던 장비는 **반드시 해제**합니다(운영 영향을 남기지 않기 위함). 해제 API/화면 확인까지가 테스트입니다.
3. Agent 종료는 **SIGTERM**

```bash
pkill -TERM -f sonar_validator_prober    # -9 금지
```

4. Frontend / Backend 는 각각 `Ctrl-C`. Backend 는 종료 직후에도 forked JVM 이
   잠깐 남으므로 재기동 전에 포트가 비워졌는지 확인합니다.

```bash
ss -ltnp | grep :3000 || echo "3000 free"
```

5. 테스트 결과(§9 시트)를 채워 남깁니다.

---

## 9. 판정 기록 시트

| # | 시나리오 | 결과 | 증거(화면/캡처·응답 코드·행 수) | 비고 |
| --- | --- | --- | --- | --- |
| 1 | Dashboard 이상 유무 | ☐ PASS ☐ FAIL | | |
| 2 | 프로젝트 생성 | ☐ PASS ☐ FAIL | | 삭제하지 않음 |
| 3 | 프로젝트 편집·검증 + 오프라인 가져오기 | ☐ PASS ☐ FAIL | | |
| 4 | Node 추가·배포 예정·번들 | ☐ PASS ☐ FAIL | | |
| 5 | Agent 연결·텔레메트리·설정 | ☐ PASS ☐ FAIL | | |
| 6 | Policy 위반·금지쌍·푸시·Export | ☐ PASS ☐ FAIL | | |
| 7 | Compliance 이력·PDF | ☐ PASS ☐ FAIL | | |
| 8 | Log 조회·필터·적재·플래그·Probe | ☐ PASS ☐ FAIL | | AI 제외 |
| 9 | Notification | ☐ PASS ☐ FAIL | | |
| 10 | Network·격리/해제·OPNsense | ☐ PASS ☐ FAIL | | FW 격리 거부 포함 |

| 항목 | 값 |
| --- | --- |
| 테스트 일시 | |
| 테스트 환경 | Backend :3000 / Frontend :5173 / Agent NODE_TYPE= |
| Agent 이름 | |
| 프로젝트 | `IT-INTEGRATION-2026-09-26` (`project_id`: ) |
| 총평 | |

---

## 부록 A. 엔드포인트 레퍼런스 (이번 범위)

| 그룹 | 메서드 · 경로 |
| --- | --- |
| Auth | `POST /auth/login`, `GET /auth/me`, `POST /auth/logout` |
| User | `GET /users/me`, `POST /users/me/password`, `PUT /users/{username}/enabled` |
| Project | `GET/POST /projects`, `GET/PUT/DELETE /projects/{id}`, `GET/POST /projects/{id}/validation`, `GET /projects/{id}/forbidden-pairs`, `POST /projects/draft/validation` |
| Agent | `GET /agents`, `GET /agents/overview`, `GET /agents/{id}/telemetry`, `GET /agents/{id}/config`, `GET /agents/configs`, `POST /agents/{id}/push`, `POST /agents/broadcast` |
| Agent 배포 예정 | `POST /agents/expected`, `GET /agents/expected`, `DELETE /agents/expected/{id}` |
| Agent 번들 | `GET /agents/bundle/info`, `GET /agents/bundle/{agentId}` |
| Policy | `GET /policy/violations/{id}`, `GET /policy/forbidden-pairs/{id}`, `POST /policy/push/{id}?force=` |
| Compliance | `GET /compliance/changes?project_id=&agent_id=`, `GET /compliance/changes/project/{id}`, `GET /compliance/changes/agent/{id}` |
| Log | `GET /logs`, `GET /logs/filters`, `GET /logs/summary`, `POST /logs/ingest`, `POST /logs/upload`, `POST /logs/{id}/flags`, `POST /logs/probe` |
| Log (AI, 제외) | `POST /logs/analyze`, `GET /logs/analyses`, `GET /logs/analyses/{id}` |
| Notification | `GET /notifications`, `GET /notifications/unread`, `GET /notifications/summary`, `PATCH /notifications/{id}/read`, `PATCH /notifications/{id}/unread`, `POST /notifications/read-all`, `DELETE /notifications/{id}` |
| Network | `GET /network/topology/{id}`, `GET /network/discovered[/{id}]` |
| Route | `GET /routes`, `GET /routes/{agentId}?protocol=`, `GET /routes/summary` |
| Quarantine | `POST /quarantine/{id}`, `DELETE /quarantine/{id}`, `GET /quarantine`, `GET /quarantine/{id}` |
| OPNsense | `GET /opnsense/credentials[/{id}]`, `PUT/DELETE /opnsense/credentials/{id}`, `POST .../verify`, `POST /opnsense/verify-all`, `POST /opnsense/credentials/{id}/probe`, `GET /opnsense/probe-targets`, `GET /opnsense/candidates` |
| Offline | `GET /offline/schema`, `POST /offline/import`, `GET /offline/imported`, `GET /offline/export/{agentId}` |
| CLI (구경로) | `POST /cli/ingest`, `GET /cli/targets` |

**AI 그룹(검증 제외)**: `GET/POST/DELETE /ai/providers`, `GET /ai/providers/enabled`,
`POST /ai/providers/{id}/check`, `/enabled`, `/default`

---

## 부록 B. WS Envelope 요약 (계층 연결 검증용)

Agent 는 REST 를 쓰지 않습니다. **WebSocket 봉투만** 사용합니다.
계층이 붙었는지 확인하는 최소 신호는 `hello` → `ack` 입니다.

| 필드 | 필수 | 값 |
| --- | --- | --- |
| `type` | ✅ | `hello` / `policy-request` / `policy-response` / `telemetry` / `command` / `ack` / `error` |
| `agent_id` | ✅ | 배포 시 심은 이름 (원본 대소문자 유지) |
| `device_type` | ✅ | `SWITCH` / `ROUTER` / `FIREWALL` / `VM` (대문자) |
| `correlation_id` | ✅ | 요청-응답 짝 (`c-N`) |
| `payload` | ✅ | 본문 (null → 빈 객체) |
| `error` | — | 오류 사유 |

| 엔드포인트 | 용도 |
| --- | --- |
| `ws://<host>:3000/api/v1/management` | 정책 요청/응답, 명령 |
| `ws://<host>:3000/api/v1/telemetry` | 30초 주기 수집 결과 (프레임 한도 1MB) |