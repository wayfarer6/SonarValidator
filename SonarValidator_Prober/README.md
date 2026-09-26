# SonarValidator Prober (Agent)

네트워크 장비에 접속해 CLI 출력과 설정을 수집하고, 파싱한 뒤 서버로 전송하는 **C++23 수집 에이전트**입니다.
서버가 닿지 않는 망분리 구간에서도 동작하도록 SQLite 로 결과를 버퍼링하고, 나중에 재전송할 수 있습니다.

## 기술 스택

| 항목 | 값 |
| --- | --- |
| 언어 | C++23 |
| 빌드 | CMake (>= 3.16) |
| 파서 | ANTLR4 4.13.2 (C++ 런타임) |
| 저장 | SQLite3 |
| 통신 | OpenSSL (TLS), WebSocket |
| 스레드 | pthread |

## 의존성

- **ANTLR4 C++ 런타임** — 헤더(`include/antlr4-runtime/antlr4-runtime.h`)가 필요합니다.
- **ANTLR4 완전 jar** — **문법을 재생성할 때만** 필요합니다. 평상시 빌드는 저장소에 커밋된
  생성 소스를 그대로 쓰므로 Java 가 없어도 됩니다.
- SQLite3, OpenSSL, pthread — 시스템 라이브러리.

## 빌드 / 테스트 / 실행

가장 간단한 방법은 `build.sh` 입니다. 툴체인 위치를 자동으로 찾고 구성 → 빌드 → 테스트 → 실행까지 합니다.

```bash
cd SonarValidator_Prober
./build.sh
```

툴체인 경로를 직접 지정하려면:

```bash
ANTLR4_JAR=$HOME/tools/antlr.jar \
ANTLR4_RUNTIME_ROOT=$HOME/tools/antlr4-install \
./build.sh
```

수동으로:

```bash
cd SonarValidator_Prober
cmake -S . -B build \
      -DANTLR4_JAR=$HOME/tools/antlr.jar \
      -DANTLR4_RUNTIME_ROOT=$HOME/tools/antlr4-install
cmake --build build --parallel
ctest --test-dir build --output-on-failure
./build/sonar_validator_prober
```

## 디렉터리 구조

```
SonarValidator_Prober/
├── components/              # 재사용 가능한 부품
│   ├── backend_communication/   # 서버 전송 (envelope 규격)
│   ├── device/                  # device_type + router/switch/firewall/vm
│   ├── network_object/          # nic, port, vlan, network_interface
│   ├── parser/                  # ★ ANTLR CLI 파서
│   ├── policy/                  # 정책 수신
│   ├── terminal/                # telnet/ssh 세션
│   └── system_service_registration/  # systemd / rc-service 등록
├── module/                  # 오케스트레이션 (부품을 조립하는 계층)
│   ├── configuration_module/
│   ├── initializing_module/
│   ├── management_module/
│   ├── offline_export_module/
│   └── telemetry_module/        # 수집 루프 (command_collector, telemetry_monitor)
├── database/                # SQLite 계층 (schema, telemetry_store)
├── utils/                   # CLI 옵션, 데이터 디렉터리
├── workers/                 # 워커 스레드
├── main.cpp
└── build.sh
```

### 계층 규칙

- `components/` = **재사용 부품**. 다른 부품에 의존하되 오케스트레이션하지 않습니다.
- `module/` = **조립 계층**. 부품을 묶어 실제 동작을 만듭니다.
- 수집기는 `components/` 가 아니라 `module/telemetry_module/` 에 있습니다.

## CLI 출력 파서

`components/parser/` 가 유일한 ANTLR 파서입니다. 손으로 만든 라인 스캐너나 `Trim` 루프를
추가하지 마세요 — 장비별 클래스는 **JSON → 모델** 변환만 하고 파싱은 `cli_parser::Parse*` 에 위임합니다.

### 문법 5종

```
components/parser/grammar/
├── IpAddr.g4          # ip a / ip route show / ip neigh
├── FrrRouter.g4       # show ip route (vtysh, 라우트 코드)
├── SwitchTopology.g4  # show vlan brief / show interfaces switchport
├── OvsTopology.g4     # ovs-vsctl show
└── NftablesRule.g4    # nft list ruleset
```

백엔드(Java)에 같은 이름의 문법 5종이 있습니다. **양쪽은 같은 JSON 계약을 만들어야 합니다.**
Prober 가 만든 JSON 이 언어 경계를 그대로 넘어가므로, 계약이 어긋나면 데이터가 조용히 사라집니다.

### 공개 API

```cpp
enum class Vendor { kOpenVSwitch, kFrr, kCisco, kArista, kNftables, kUbuntu, kUnknown };
Vendor VendorFromProductName(const std::string& product_name);
std::string VendorName(Vendor vendor);

nlohmann::json ParseNicStatus(const std::string& raw);      // ip a / ip addr show
nlohmann::json ParseNicBrief(const std::string& raw);       // ip -br addr show
nlohmann::json ParseArpTable(const std::string& raw, Vendor vendor);
nlohmann::json ParseRouteStatus(const std::string& raw, Vendor vendor);
nlohmann::json ParseInterfaceStatus(const std::string& raw);
nlohmann::json ParseSwitchTopology(const std::string& raw);
nlohmann::json ParseFirewallRules(const std::string& raw);

// target 이름으로 문법을 골라 주는 단일 진입점
nlohmann::json ParseQueryOutput(Vendor vendor,
                                const std::string& target,
                                const std::string& raw);
```

### 문법을 수정했을 때 — 반드시 재생성

생성된 파서 소스는 `components/parser/generated/grammar/` 에 **커밋**되어 있습니다.
따라서 `.g4` 만 고치고 일반 빌드를 하면 **옛 파서가 그대로 쓰입니다.** (증상: 문법을 고쳤는데
동작이 안 바뀜)

```bash
cd SonarValidator_Prober
cmake --build build --target regenerate_parser
# 또는
sh components/parser/cmake/regenerate_parser.sh
```

그 뒤 재빌드하고, **재생성된 `generated/grammar/*` 를 함께 커밋**하세요.

재생성에는 두 가지 준비물이 있습니다.

- Java 실행 파일
- ANTLR4 완전 jar — `ANTLR4_JAR` 환경변수 또는 아래 기본 경로 중 하나
  - `~/tools/antlr.jar`
  - `~/antlr/antlr-4.13.2-complete.jar`
  - `/usr/local/lib/antlr-4.13.2-complete.jar`
  - `/usr/share/java/antlr4.jar`

> ⚠️ **jar 버전을 확인하세요.** 커밋된 생성 소스는 **4.13.2** 기준입니다. 다른 버전으로
> 생성하면(예: 4.13.1) 런타임 API 가 달라져 생성 파일 diff 가 통째로 뒤집힙니다.
> `regenerate_parser.sh` 가 버전을 확인하고 불일치 시 중단합니다. 정말 다른 버전을 쓰려면
> `ANTLR4_EXPECTED_VERSION=<버전>` 을 함께 넘기세요.
>
> 실제 사례: `~/tools/antlr.jar` 라는 이름의 파일이 `4.13.2-SNAPSHOT` 포크였는데
> `Version 4.13.1` 로 보고되어, 재생성만으로 44개 파일이 뒤바뀌었습니다.
> **jar 이름이 아니라 보고되는 버전을 믿으세요.**

생성 옵션은 `-Dlanguage=Cpp -visitor -no-listener` 입니다. Visitor 는 자동 생성되므로
**직접 쓰지 마세요.**

### 파서 디버깅 도구

문법을 고친 뒤 특정 출력이 어떻게 파싱되는지 바로 확인할 수 있습니다.

```bash
# build/cli_output_parser_probe <target> <file> [vendor]
./build/cli_output_parser_probe route /tmp/show-route.txt cisco
./build/cli_output_parser_probe arp   /tmp/show-arp.txt   cisco
./build/cli_output_parser_probe ovs   /tmp/ovs.txt        ovs
```

- `target` : `nic | brief | route | interface | ovs | vlan | switchport | ruleset | arp`
- `vendor` : `ubuntu | frr | cisco | arista | ovs | nftables`

> `nic` 는 ARP 를 파싱하지 않습니다. ARP 는 `arp` target 을 쓰세요.

### 문법을 고칠 때의 원칙

1. **파싱 문제는 `.g4` 에서 고칩니다.** Visitor 를 손으로 패치하면 문법과 구현이 어긋납니다.
2. **토큰 선언 순서가 최장일치 동점을 결정합니다.** 동일한 문자집합을 가진 키워드는
   `IFNAME` / `ADDR` / `ATTRWORD` **보다 먼저** 선언해야 합니다.

   실제 사례: `ROUTETYPE`(`blackhole` 등)을 `IFNAME` 뒤에 두었더니 `blackhole` 이 `IFNAME` 으로
   렉싱되어 `routeHead` 가 매칭되지 않았고, **오류 없이 라우트 0건**이 되었습니다.
   소비자는 "조회는 했는데 경로가 없다"로 읽습니다 — 조용한 데이터 유실입니다.

3. **포괄 규칙(`elem : ~NEWLINE`)은 구조를 지웁니다.** 쓰레기 입력도 `parsed:true` 로 통과합니다.
4. **머리 규칙을 넓게 잡지 마세요.** `routeHead : ... | IFNAME` 은 아무 단어나 레코드로 만듭니다.
   장비가 실제로 내보내는 토큰으로 좁히면 잡음은 `genericLine` 으로 빠져 무시됩니다.
5. 캐릭터 셋에서 `[` / `]` 는 이스케이프하지 않습니다 → `~[ \t\r\n:,()[\]]+`
6. `WORD` 가 `,` 를 포함하면 이름과 구분자가 붙어 버립니다 → `,` 를 제외하고 `COMMA : ','` 를 추가.

## 라우트 metric 계약

**Java 백엔드가 기준입니다.** Prober 도 같은 JSON 을 내야 합니다.

```
Cisco/IOS `[110/200]`  →  distance = 110 (int), metric = 200 (int)
                          한쪽이라도 숫자가 아니면 metric_raw (text) 에 원문 보존
`metric 20`            →  metric = 20 (int)
```

`metric` 이 정수로 읽히므로 문자열로 보내면 소비자가 잘못 해석합니다.
`blackhole` 같은 라우트 타입 키워드는 **목적지도 필드도 아니므로 버립니다** — Java 계약과 동일합니다.

```
blackhole 10.0.0.0/8   →  { "destination": "10.0.0.0/8", "is_default": false }
default via 10.0.0.1   →  { "destination": "0.0.0.0/0",  "is_default": true  }
```

## SQLite 계층

`database/schema.cpp` 는 `CREATE TABLE IF NOT EXISTS` 만 씁니다. **마이그레이션 기구가 없습니다.**
스키마를 바꿀 때는 파일 상단의 "변경 이력" 주석에 기록하고, 기존 DB 를 쓰는 환경이라면
DB 파일을 지우거나 수동 `ALTER TABLE` 이 필요합니다.

`route_table` 컬럼:

```
next_hop TEXT, metric INTEGER, interface_name TEXT, selected INTEGER,
fib INTEGER, connected INTEGER, distance INTEGER, metric_raw TEXT
```

> SQLite 의 타입 친화성 덕분에 TEXT 로 저장된 옛 데이터도 INTEGER 컬럼에서 읽힙니다.

## 테스트

```bash
cd SonarValidator_Prober
ctest --test-dir build --output-on-failure          # 전체
ctest --test-dir build -R cli_output_parser_test    # 특정 테스트
```

| # | 테스트 | 대상 |
| --- | --- | --- |
| 1 | `prober_config_test` | 설정 로딩 / 제품명 판별 |
| 2 | `database_service_test` | DB 서비스 |
| 3 | `telemetry_store_test` | 텔레메트리 저장소 |
| 4 | `envelope_test` | 서버 전송 봉투 규격 |
| 5 | `routing_table_test` | 라우팅 테이블 |
| 6 | `switch_test` | 스위치 |
| 7 | `firewall_test` | 방화벽 |
| 8 | `telemetry_service_test` | 텔레메트리 서비스 |
| 9 | `offline_export_test` | 오프라인 내보내기 |
| 10 | `management_service_integration_test` | 관리 서비스 통합 |
| 11 | `cli_output_parser_test` | **CLI 파서 (179 checks)** |
| 12 | `command_collector_test` | 명령 수집기 |

파서 테스트를 직접 실행하면 항목별 결과가 전부 보입니다. 크래시로 출력이 잘릴 때는
버퍼링을 끄고 보세요.

```bash
stdbuf -o0 -e0 ./build/cli_output_parser_test
```

## 도구

배포·프로빙 스크립트는 저장소 루트의 `deployment/` 아래에 있습니다.
(네트워크별로 나뉘고, 공통 도구는 `deployment/_shared/`)

| 스크립트 | 용도 |
| --- | --- |
| `deploy_poc_lab.sh` | PoC 랩 전체 배포 |
| `deploy_cisco.py`, `deploy_arista.sh` | 벤더별 장비 배포 |
| `node_probe.py`, `gns3_console.py` | GNS3 노드 프로빙 |
| `ws_collector.py` | WebSocket 수집기 |
| `run_prober.sh`, `run_prober_linux.sh` | 프로버 실행 |

`PoC_Lab_Deployment.md` 에 절차가 정리되어 있습니다.

## 참고

- CLI 파서 JSON 계약의 원본은 `components/parser/cli_output_parser.hpp` 주석입니다.
- 백엔드 쪽 대응 구현은 `SonarValidator_Backend/src/main/java/.../Service/cli/` 입니다.
- LLM 을 사용한다면 파싱 규칙을 바꿀 때 `cli_output_parser_test.cpp` 의 회귀 테스트를 먼저 확인하세요.