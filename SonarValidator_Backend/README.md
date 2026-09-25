# SonarValidator Backend

수집된 네트워크 상태와 설정을 받아 **정책 검증**을 수행하고 결과를 API 로 제공하는 Spring Boot 서버입니다.

## 기술 스택

| 항목 | 값 |
| --- | --- |
| Java | **26** |
| Spring Boot | 4.1.1 (모듈별 스타터) |
| 빌드 | Maven Wrapper (`./mvnw`) |
| JSON | **Jackson 3** (`tools.jackson.databind.*`) |
| 파서 | ANTLR4 4.13.1 (`antlr4-maven-plugin`) |
| DB | H2 (로컬/테스트), PostgreSQL (운영) |
| 인증 | Spring Security |

> ⚠️ **Jackson 3 을 사용합니다.** import 경로가 Jackson 2 와 다릅니다.
> `com.fasterxml.jackson.*` 가 아니라 `tools.jackson.databind.*` 입니다.

## 실행

```bash
export JAVA_HOME=/path/to/jdk-26
cd SonarValidator_Backend
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=3300
```

프로파일:

| 프로파일 | DB | 용도 |
| --- | --- | --- |
| (기본) | H2 파일 모드 | 로컬 개발 |
| `test` | H2 인메모리 + `create-drop` | 테스트 |
| `postgres` | PostgreSQL | 운영 |

## 디렉터리 구조

```
src/main/java/org/sonar/sonarvalidator_backend/
├── Config/       # Security, WebSocket 등 설정
├── Controller/   # REST 엔드포인트
├── Model/        # 도메인 / 설정 모델 (Config/Vendors 에 벤더별 파서)
├── Policy/       # 정책 검증 규칙
├── Repository/   # JPA 저장소
├── Service/      # 비즈니스 로직
│   └── cli/      # ★ 네이티브 CLI 출력 파서 (ANTLR Visitor 포함)
└── View/         # 응답 DTO
src/main/antlr4/org/sonar/sonarvalidator_backend/grammar/   # ★ ANTLR 문법 5종
```

## CLI 출력 파서

### 두 개의 파싱 계층이 있는 이유

정상 경로에서는 Prober 가 **이미 파싱한 구조화 JSON**(`nic_status`, `route_status`, `arp_table` …)을
보냅니다. 백엔드는 그것을 그대로 씁니다.

그런데 다음 경우에는 서버가 **원문을 직접** 파싱해야 합니다.

1. 구버전 Prober — 개별 장비 바이너리를 한 번에 올리기 어려워 원문만 보내는 장비가 섞임
2. 운영자가 진단 중 CLI 출력을 그대로 붙여넣는 경우
3. 저장된 오프라인 스냅샷 원문을 다시 파싱하는 경우
4. 벤더가 제 키로 보낸 JSON 을 원문 기준으로 재확인하는 경우

이 경로가 없으면 원문은 파싱되지 않은 채 버려집니다.

### 구성

| 파일 | 역할 |
| --- | --- |
| `Service/cli/CliOutputParser` | 단일 진입점. target + vendor 로 문법 하나를 고른다 |
| `Service/cli/*Visitor` | ANTLR `BaseVisitor` 구현. 문법에서 자동 생성된 트리를 순회 |
| `Service/cli/CliIngestionService` | 원문 문자열 폴백 파사드. 이미 구조화된 JSON 은 건너뛴다 |
| `Service/cli/CliIngestService` | 계약 키 매핑 + 중립 설정 변환 |
| `Service/cli/CliText`, `CliJson`, `CliVendor`, `RouteCodes` | 공용 유틸 |

### 문법 선택은 "출력 모양"이 아니라 "명령"으로

같은 벤더라도 명령에 따라 출력이 달라집니다.

```
FRR 호스트 셸   ip route show    → 라우트 코드 없음  → IpAddr 문법
FRR vtysh       show ip route    → `O>*` 코드 있음   → FrrRouter 문법
```

그래서 `vendor` 만으로는 부족하고 `target` 까지 봐야 합니다.
target 이 비어 있으면 벤더 기본 조회로 폴백합니다.

### 실패 계약

파서는 **예외를 던지지 않습니다.** 문법 오류가 있어도 뽑은 정보를 담고 `parsed:false` 와
`parse_error` 를 붙여 돌려줍니다. 수집 경로에서 예외는 곧 데이터 유실이기 때문입니다.

빈 문서는 문법상 정상 파싱(`parsed:true`, 0건)입니다. **"비었다"를 판단하는 것은 파서가 아니라
수집 계층(`CliIngestService`)의 책임**입니다.

## REST API — 원문 CLI 수집

```
POST /api/v1/cli/ingest
GET  /api/v1/cli/targets
```

### `POST /api/v1/cli/ingest`

요청:

```json
{ "product": "Ubuntu", "target": "route", "raw": "default via 10.99.10.1 dev eth0 proto static" }
```

응답 (**항상 HTTP 200**):

```json
{
  "accepted": true,
  "vendor": "LINUX",
  "vendor_name": "Ubuntu",
  "query": "route",
  "target_key": "route_status",
  "item_count": 1,
  "parsed": { "routes": [ { "destination": "0.0.0.0/0", "is_default": true, ... } ] },
  "errors": []
}
```

`?config=true` 를 붙이면 중립 설정(`NeutralDeviceConfig`)까지 함께 변환합니다.

### 왜 200 을 돌려주면서 실패를 담는가

원문이 비었거나 문법에 맞지 않는 것은 **사용자 입력 문제**입니다. 요청 자체는 정상 처리됐으므로
200 과 함께 `accepted` / `errors` 를 돌려주고, 프론트엔드가 사유를 그대로 표시할 수 있게 합니다.
`OfflineImportController` 와 같은 관용구입니다.

### 실패 시 규칙

- `raw` 가 공백뿐이면 → `accepted=false`, `errors=[...]`, **`parsed` 키를 만들지 않는다**
- 파싱은 됐지만 `item_count == 0` 이면 → `errors` 에 사유를 넣고 **계약 키를 payload 에 병합하지 않는다**
- 오류가 없을 때만 `target_key` 로 결과를 담는다

> 🔒 `/api/**` 는 인증이 필요합니다 (`Config/SecurityConfig`). `OPTIONS /**` 와 `PUBLIC_PATHS` 만 허용됩니다.

### `GET /api/v1/cli/targets`

서버가 인식하는 대상 이름 · 계약 키 · 벤더 목록과 호출 예시를 돌려줍니다.
프론트엔드가 선택 상자를 그릴 때 씁니다. 서버와 어긋나지 않도록 목록을 프론트에 하드코딩하지 않습니다.

지원 대상:

| name | 계약 키 | 설명 |
| --- | --- | --- |
| `route` | `route_status` | 라우팅 테이블 |
| `nic` | `nic_status` | 인터페이스와 주소 |
| `brief` | `nic_status` | 인터페이스 요약 |
| `arp` | `arp_table` | 이웃 테이블 |
| `vlan` | `vlan_status` | VLAN |
| `port` | `trunk_status` | 스위치 포트 |
| `ruleset` | `firewall_rules` | nftables 규칙 |
| `topology` | `ovs_topology` | OVS 브리지 |

## ANTLR 문법

```
src/main/antlr4/org/sonar/sonarvalidator_backend/grammar/
├── IpAddr.g4          # ip a / ip route show / ip neigh
├── FrrRouter.g4       # show ip route (vtysh, 라우트 코드)
├── SwitchTopology.g4  # show vlan brief / switchport
├── OvsTopology.g4     # ovs-vsctl show
└── NftablesRule.g4    # nft list ruleset
```

생성 결과(패키지 `org.sonar.sonarvalidator_backend.grammar`)는 `target/generated-sources/antlr4/`
로 나가며 **커밋하지 않습니다.**

```bash
./mvnw antlr4:antlr4       # 재생성 (필요하면 target/generated-sources/antlr4 를 먼저 삭제)
./mvnw compile
```

`visitor=true`, `listener=false` 로 설정되어 있으므로 Visitor / BaseVisitor 만 생성됩니다.

### 문법을 고칠 때의 원칙

1. **파싱 문제는 `.g4` 에서 고칩니다.** Visitor 를 손으로 패치하지 않습니다.
2. Visitor / BaseVisitor 는 ANTLR 이 자동 생성합니다 — 직접 쓰지 않습니다.
3. **토큰 선언 순서가 최장일치 동점을 결정합니다.** 동일한 문자집합을 가진 키워드는
   `IFNAME` / `ATTRWORD` / `ADDR` **보다 먼저** 선언해야 합니다. 뒤에 두면 키워드가
   `IFNAME` 으로 렉싱되고 해당 규칙이 매칭되지 않아 **오류 없이 조용히 0건**이 됩니다.
4. **`elem : ~NEWLINE` 같은 포괄 규칙은 구조를 지웁니다.** 쓰레기 입력도 `parsed:true` 로
   통과합니다. 수집 계층에서 막거나 규칙을 좁히세요.
5. **머리 규칙을 넓게 잡지 마세요.** `routeHead : ... | IFNAME` 은 아무 단어나 레코드로
   만듭니다. 장비가 실제로 내보내는 토큰으로 좁히면 잡음은 `genericLine` 으로 빠져 무시됩니다.
6. 캐릭터 셋에서 `[` / `]` 는 이스케이프하지 않습니다 → `~[ \t\r\n:,()[\]]+`

## 라우트 metric 계약

**Java 구현이 기준입니다.** C++ Prober 도 같은 JSON 을 내야 합니다 — Prober 의 JSON 이
언어 경계를 그대로 넘어오기 때문입니다.

```
Cisco/IOS `[110/200]`  →  distance = 110 (int), metric = 200 (int)
                          한쪽이라도 숫자가 아니면 metric_raw (text) 에 원문 보존
`metric 20`            →  metric = 20 (int)
```

`metric` 이 정수로 읽히므로 문자열로 보내면 소비자가 잘못 해석합니다. `distance` 도 함께 담아
관리 거리(AD) 정보를 잃지 않게 합니다.

DB 계층(`route_table`)은 SQLite 타입 친화성 덕분에 TEXT / INTEGER 를 모두 받아들입니다.

## 테스트

```bash
./mvnw test                                    # 전체 — 183 tests
./mvnw test -Dtest=CliIngestServiceTest        # 특정 클래스
./mvnw test -Dtest='CliIngestServiceTest,CliOutputParserTest,CliIngestionServiceTest'
```

테스트는 H2 인메모리 + `create-drop` 을 씁니다. WebSocket 설정 검증에는
`@SpringBootTest(webEnvironment = RANDOM_PORT)` 가 필요합니다.

| 테스트 | 개수 |
| --- | --- |
| `IpValueObjectTest` | 28 |
| `AuthenticationTest` | 15 |
| `NotificationServiceTest` | 15 |
| `LogNormalizerTest` | 15 |
| `OpenAiCompatibleClientTest` | 14 |
| `BddManagerTest` | 14 |
| `CliOutputParserTest` | 13 |
| `OfflineSnapshotImportTest` | 12 |
| `SegmentationBddEngineTest` | 10 |
| `CliIngestServiceTest` | 9 |
| `AgentMessageRouterTest` | 8 |
| `CliIngestionServiceTest` | 8 |
| `DeviceConfigParserTest` | 7 |
| `ProjectDtoSerializationTest` | 6 |
| `OPNSenseFirewallRepositoryTest` | 3 |
| `SonarValidatorBackendApplicationTests` | 1 |
| `WebSocketBufferSizeTest` | 1 |