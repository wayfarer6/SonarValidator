# SonarValidator Prober — 작업 세션 노트 (핸드오프)

> 이 문서는 세션이 바뀌어도 이어서 작업할 수 있도록, 지금까지 **무엇을 했고 / 무엇이 검증되었고 / 다음에 무엇을 해야 하는지**를 기록합니다.
> 최종 갱신: 2026-09-18

---

## 0. 한 줄 요약

C++23 프로버 에이전트(`SonarValidator_Prober`)에 **ANTLR 기반 CLI 출력 파서 + 수집기 + SQLite 저장** 계층을 추가했고,
실장비(Arista vEOS, Cisco 8000v)에서 **E2E 검증까지 완료**했습니다.
현재는 **새 PoC 랩(Alpine firewall / Ubuntu VM / OpenVSwitch / FRR)** 으로 배포·검증을 진행 중입니다.

---

## 1. 프로젝트 기본 정보

| 항목 | 값 |
|---|---|
| 소스 루트 | `/home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober` |
| 언어/표준 | C++23 (`CMAKE_CXX_STANDARD 23`, extensions OFF) |
| 빌드 | CMake 3.28.3 + GNU Make 4.3 + g++ 13.3.0 |
| 테스트 | `ctest --test-dir build --output-on-failure` (8개 테스트) |
| DB | SQLite3 (`SQLite::SQLite3`) |
| JSON | nlohmann/json 3.11 |
| 통신 | Boost.Asio/Beast WebSocket |

### ANTLR 툴체인 (중요)

| 자산 | 경로 | 비고 |
|---|---|---|
| ANTLR jar | `~/tools/antlr.jar` | 4.13.1 |
| C++ 런타임 | `~/tools/antlr4-install` | `include/antlr4-runtime/antlr4-runtime.h`, `lib/libantlr4-runtime.a` |
| JDK | `~/.sdkman/candidates/java/current` (Java 26) | |

> ⚠️ **주의**: 이 툴체인은 세션 사이에 사라진 적이 있습니다. CI/새 환경에서는 반드시 재설치해야 합니다.
> `cmake -S . -B build` 로그에 `-- ANTLR4: toolchain not found` 가 보이면 **파서/수집기 없이 빌드된 것**입니다.
> 이 경우 바이너리에 ANTLR 심볼이 0개이므로 반드시 툴체인을 복구한 뒤 재빌드하세요.

**탐색 순서** (`parser/cmake/FindANTLR4Runtime.cmake`):
1. CMake 캐시 변수 `ANTLR4_JAR` / `ANTLR4_RUNTIME_ROOT`
2. 환경변수 `ANTLR4_JAR` / `ANTLR4_RUNTIME_ROOT`
3. 일반 경로 (`~/tools/antlr.jar`, `~/tools/antlr4-install`, …)
4. `PATH` 의 `antlr4` 실행 파일

**런타임 재설치 절차** (sudo 불필요):
```sh
cd /tmp && curl -sL -o rt.zip https://www.antlr.org/download/antlr4-cpp-runtime-4.13.2-source.zip
mkdir -p antlr4-src && cd antlr4-src && python3 -c "import zipfile;zipfile.ZipFile('../rt.zip').extractall('.')"
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX=$HOME/tools/antlr4-install \
  -DANTLR_BUILD_STATIC=ON -DANTLR_BUILD_SHARED=OFF -DANTLR_BUILD_CPP_TESTS=OFF
cmake --build build --target install -j $(nproc)
```

> 주의: 압축 해제 후 소스 루트가 `runtime/Cpp` 가 아니라 **`runtime/`** 입니다.
> 또한 `cmake -S runtime` 은 실패하므로 **소스 최상위(`/tmp/antlr4-src`)에서** 구성하세요.

### musl(Alpine) 대상 정적 빌드 (검증됨)

Alpine 은 **musl libc** 라 glibc 동적 바이너리가 실행되지 않습니다.
(`/lib/ld-musl-x86_64.so.1` 만 존재, `libc.so.6` 없음)

```sh
cmake -S . -B build_static -DCMAKE_BUILD_TYPE=Release \
  -DSQLite3_LIBRARY=/usr/lib/x86_64-linux-gnu/libsqlite3.a \
  -DCMAKE_EXE_LINKER_FLAGS="-static -static-libgcc -static-libstdc++"
cmake --build build_static --target sonar_validator_prober -j $(nproc)
```

결과: 7.7MB, `statically linked`, ANTLR 심볼 3026개, 파서 진입점 12개.
경고 2건(`dlopen`, `getaddrinfo`)은 정적 링크 시 정상이며 무해합니다.

> **변수명 함정**: `-DSQLITE3_LIBRARY=` (대문자) 는 무시됩니다.
> CMake 표준 모듈 변수인 **`-DSQLite3_LIBRARY=`** 를 써야 하며,
> `-DSQLITE3_USE_STATIC_LIBS=ON` 도 효과가 없습니다.
> 확인: `grep -o '[^ ]*libsqlite3[^ ]*' build_static/CMakeFiles/sonar_validator_prober.dir/link.txt`

---

## 2. 신규 추가 모듈 (이번 작업의 핵심)

### 2.1 파서 — `parser/cli_output_parser.{hpp,cpp}`

장치 CLI **조회 출력 원문 → JSON** 변환. **조회만** 하며 명령을 실행하지 않습니다.

진입점 (`cli_parser` 네임스페이스):

| 함수 | 대상 |
|---|---|
| `ParseNicStatus(raw)` | `ip a` / `ip addr` 상세 |
| `ParseNicBrief(raw)` | `ip -br addr show` 요약 |
| `ParseRouteStatus(raw, vendor)` | 라우팅 테이블 |
| `ParseInterfaceStatus(raw, vendor)` | 인터페이스 상태 |
| `ParseOvsTopology(raw)` | Open vSwitch 토폴로지 |
| `ParseSwitchVlan(raw)` | 스위치 VLAN |
| `ParseSwitchPorts(raw)` | 스위치 포트/트렁크 |
| `ParseFirewallRules(raw)` | nftables 규칙 |
| `ParseArpTable(raw, vendor)` | ARP 테이블 |

보조: `VendorFromProductName()`, `VendorName()`, `ParseQueryOutput(vendor, target, raw)`

`Vendor` enum: `kOpenVSwitch, kFrr, kCisco, kArista, kNftables, kUbuntu, kUnknown`

**실패 규약**: 예외를 던지지 않고 `{"parsed": false, "parse_error": "...", "raw": "..."}` 를 반환합니다.

### 2.2 문법 — `parser/grammar/*.g4` (5개)

| 파일 | 규칙 |
|---|---|
| `IpAddr.g4` | `document`, `briefDocument`, `routeDocument`, `arpDocument` |
| `OvsTopology.g4` | `showDocument`, `listDocument`, `flowDocument` |
| `FrrRouter.g4` | `routeDocument`, `ifaceDocument`, `detailDocument` |
| `NftablesRule.g4` | `rulesetDocument`, `chainDocument` |
| `SwitchTopology.g4` | `runningDocument`, `vlanDocument`, `briefDocument`, `portDocument` |

생성 코드는 `parser/generated/grammar/` 에 **빌드 시** 생성됩니다 (gitignore 대상).

### 2.3 수집기 — `collector/command_collector.{hpp,cpp}`

```cpp
struct CollectedState { snapshot, nic, route, vlan, trunk, arp, any_success };
```

| 함수 | 용도 |
|---|---|
| `CollectState(config, management_service)` | 실장비에서 명령 실행 후 수집 |
| `BuildStateFromOutputs(device_type, product_name, outputs)` | **순수 함수** — 테스트용 |
| `ClassifyProduct()` → `ProductKind{kLinux,kCisco,kArista,kOther}` | 벤더 판별 |

장치별 실행 명령:

- **Linux(Alpine/Ubuntu)**: `RunCommandOutput("ip a")`, `"ip -br addr show"`, `"ip route show"`, `"ip neigh show"`
- **Cisco(C8000v)**: `ExecuteIosCli({"show ip interface brief","show ip route","show ip arp"})`
- **Arista(vEOS)**: `QueryAristaCli("show vlan brief","show ip interface brief","show interfaces switchport","show arp")`

`CleanAristaOutput()` 가 명령 echo(`> show...`), `% Internal error at line N`, `Pagination disabled.` 를 제거합니다.

### 2.4 DB 스키마 — `database/schema.cpp`

DDL 단일 소스. 신규 테이블:

| 테이블 | 내용 |
|---|---|
| `nic_info` | NIC 상세 |
| `nic_address` | NIC 주소 (`index_number` 사용 — `index` 는 SQLite 키워드) |
| `route_table` | 라우팅 (`prefix`/`next_hop` 또는 `destination`/`via`) |
| `vlan_status` | VLAN 현황 (설계시 `vlan_table` 과 **별개**) |
| `trunk_status` | 트렁크 현황 |
| `arp_table` | ARP |

기존 유지: `Agent_info`, `nic_status`, `settings`, `nic_table`, `subnet_table`, `vlan_table`, `router_table`, `router_config_table`, `firewall_rule_table`

### 2.5 저장 헬퍼 — `database/telemetry_store.{hpp,cpp}`

- `CurrentUtcTimestamp()`
- `EnqueueRouteStatusSave`, `EnqueueNicInfoSave`, `EnqueueVlanStatusSave`, `EnqueueTrunkStatusSave`, `EnqueueArpTableSave`
- `FirstOf(ColumnValue, ColumnValue)` — `prefix`/`destination`, `next_hop`/`via` 양쪽 키를 흡수

> **원칙**: 스냅샷 1개 = 태스크 1개 = **트랜잭션 1개**

### 2.6 아키텍처 다이어그램 — `docs/Agent/Agent_Architecture_Diagrams.md`

Mermaid 7종: 컴포넌트 flowchart, classDiagram, 기동/정책 sequenceDiagram, 텔레메트리 수집/전송/저장 sequenceDiagram, 벤더 분기 flowchart, ER 다이어그램, 배포 토폴로지

---

## 3. 런타임 환경변수 (비root 실행에 필수)

| 변수 | 기본값 | 용도 |
|---|---|---|
| `SONAR_DATA_DIR` | `/var/lib/sonar_validator_prober` | DB/데이터 저장 위치 |
| `SONAR_TEMPLATE_PATH` | `/etc/sonar_validator_prober/sqlite_template.sqlite` | DB 템플릿 |
| `SONAR_CONFIG_PATH` | `/etc/sonar_validator_prober/default.conf` | 설정 |

`default.conf` 예시:
```
SERVER_IP=localhost;
SERVER_PORT=3000;
NODE_TYPE=VM;      # Router | Switch | VM | Firewall
```

관련 코드: `main.cpp` 의 `ResolveDataDirectory()`, `ResolveTemplatePath()`, `prober_config.cpp` 의 `ResolveConfigPath()`

---

## 4. 검증 완료 항목 (실장비 E2E)

### 4.1 Arista vEOS — ✅ 통과

- 배포: `parser/tools/deploy_arista.sh` (sftp, sha256 일치 확인)
- SFTP 막힌 환경에서는 `SCP_MODE=1` 로 `scp -O` 사용
- DB 저장 결과 (실측):

| 테이블 | 건수 |
|---|---|
| `vlan_status` | 4 |
| `trunk_status` | 11 |
| `arp_table` | 3 |
| `nic_info` | 4 |

### 4.2 Cisco 8000v — ✅ 통과

접속이 까다로웠고 **GNS3 콘솔 경로**로 해결했습니다.

- IOS SSH(22): 관리자 계정은 CLI 전용, guestshell SSH 는 **키 인증만** 지원
- RESTCONF 비활성(404), WebUI 로그인으로 CLI 진입 불가
- ✅ **해결**: GNS3 콘솔(기본 5018)이 **guestshell bash 에 직접 연결** → `dohost "<IOS cmd>"` 로 IOS 명령 실행 (인증 불필요)
- 파일 전송: IOS `copy http://...` 로 `/bootflash/guest-share` 에 넣으면 guestshell 과 공유됨
- 스크립트: `parser/tools/deploy_cisco.py` (환경변수 `SONAR_GNS3_SSH`, `SONAR_GNS3_CONSOLE`)

DB 저장 결과 (실측):

| 테이블 | 건수 |
|---|---|
| `route_table` | 9 |
| `arp_table` | 10 |
| `nic_info` | 5 |
| `nic_address` | 4 |

MAC 정규화 확인: `0c3f.5d52.0003` → `0c:3f:5d:52:00:03`

### 4.3 서버 텔레메트리 — ✅ 통과

`parser/tools/ws_collector.py` (표준 라이브러리만 사용) 수신 결과:

- `hello` 1건, `policy-request` 4건, `telemetry` 1건
- `telemetry` payload 키: `agent, agent_name, device_type, kernel, nic_status, route_status, arp_table`

### 4.4 Linux VM 경로 — ✅ 통과 (호스트에서)

- `nic_info` 7, `nic_address` 6, `route_table` 4(prefix 정상), `arp_table` 4

### 4.5 단위 테스트

| 항목 | 결과 |
|---|---|
| 파서 테스트 | 153/153 |
| 수집기 테스트 | 67/67 |
| ctest 전체 | 8/8 |

---

## 5. 새 PoC 랩 (D-AI-PBL-PoC-Network)

기준 문서: `PoC용 네트워크 구성하기/` (README, `네트워크_구성_요약.md`, 장비별 `.md`)

### 5.1 존 / 대역

| 존 | 대역 | 구성 |
|---|---|---|
| Confidential (C4I) | 10.10.131~133.0/24 | C4I-R → Firewall → Switch-3 → ATICS/KNCCS/AFCCS |
| Sensitive | 10.20.111~112.0/24, 10.40.121~122.0/24 | Surv-R → Switch-1 → TOD-Cam/UAV, VDI-R → Switch-2 → VDI-1/2 |
| Open (DMZ) | 10.30.141.0/24 | DMZ-R → Switch-4 → Public-Web-Server |
| Core | 10.99.10.0/24 (VLAN 10) | Gateway-Router + Switch-0 |
| Management | 172.16.255.0/24 | Hub1 경유, OSPF passive |

- 라우팅: 전 라우터 OSPF area 0, router-id = loopback `10.255.255.x`
- Gateway-Router: `default-information originate always` + eth0 MASQUERADE
- C4I-R: ASBR (`10.10.128.0/21 → 10.99.143.2` static → `redistribute static`)
- Firewall: OSPF 미참여, default 만 C4I-R 방향

### 5.2 주요 주소

| 장비 | 주소 |
|---|---|
| Gateway-Router | 10.99.10.1 / **172.16.255.1** / 192.168.122.10 / lo 10.255.255.1 |
| DMZ-Router | 10.99.10.3 / 172.16.255.3 / lo 10.255.255.3 |
| C4I-Network-Router | 10.99.10.4 / 172.16.255.4 / lo 10.255.255.4 |
| Survillance-Network-Router | 10.99.10.5 / 172.16.255.5 / lo 10.255.255.5 |
| VDI-Router | 10.99.10.6 / 172.16.255.6 / lo 10.255.255.6 |
| Firewall | 10.99.143.2 / 172.16.255.2, VLAN GW 10.10.131~133.1 |
| Public-Web-Server | **10.30.141.10** |
| ATICS / KNCCS / AFCCS | 10.10.131.10 / 10.10.132.10 / 10.10.133.10 |
| TOD-Cam / UAV | 10.20.111.10 / 10.20.112.10 |
| VDI-1 / VDI-2 | 10.40.121.10 / 10.40.122.10 |

스위치(Switch-0~4)는 L2 전용(Open vSwitch br0), 포트별 tag/trunk 구성이며 `eth11` 이 관리망(untagged).

### 5.3 랩 접속 경로 (실측)

호스트는 관리망·데이터망 **양쪽**에 붙어 있습니다.

- `ens3 = 172.16.255.245/24` (Management)
- `ens4 = 192.168.122.58/24` (NAT/Internet)

| 대상 | 접속 | 상태 |
|---|---|---|
| **172.16.255.1** Gateway-Router (Alpine + FRR 8.2.2) | `ssh root@172.16.255.1` (기존 키) | ✅ **성공** |
| **ubuntu@10.30.141.10** Public-Web-Server (Ubuntu 24.10) | ProxyJump + 비밀번호 `ubuntu` | ✅ **성공** |
| 172.16.255.2 / .3 / .4 / .5 / .6 | SSH 22 열림, 키 거부 | ❌ 비밀번호 미상 |
| 10.99.10.1/.3/.4/.5/.6 | 데이터망 SSH 22 OPEN | ⚠️ 인증 미확보 |
| Switch-0~4, TOD-Cam, UAV, VDI-1/2 | SSH 없음 | ❌ 콘솔 필요 |
| GNS3 호스트 172.18.136.244 | ping OK, SSH 키 거부, 콘솔포트 닫힘 | ❌ |

**172.16.255.1 (FRR 라우터) 보유 도구**: `ssh`, `scp`, `vtysh`, `ip`, `iptables`, `base64`, `gzip` / **`python3` 없음**, `nft` 없음

**Ubuntu VM(10.30.141.10) 환경**: glibc 2.40, x86_64, `python3`/`base64`/`gzip`/`sudo` 있음, **컴파일러·cmake 없음**, 디스크 여유 약 600MB

### 5.4 Ubuntu VM 접속 방법 (검증된 명령)

로컬에는 `sshpass` 가 없고, 라우터 쪽 busybox 는 `setsid -w` 를 지원하지 않습니다.
따라서 **askpass 스크립트**를 라우터에 만들어 두고 사용합니다.

```sh
# 1) 라우터에 askpass 스크립트 생성
printf '#!/bin/sh\necho ubuntu\n' | ssh root@172.16.255.1 'cat > /tmp/ask.sh && chmod +x /tmp/ask.sh'

# 2) 라우터를 경유해 VM 명령 실행
ssh root@172.16.255.1 \
  'SSH_ASKPASS=/tmp/ask.sh SSH_ASKPASS_REQUIRE=force ssh \
     -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
     -o PreferredAuthentications=password -o PubkeyAuthentication=no \
     -o NumberOfPasswordPrompts=1 ubuntu@10.30.141.10 "CMD"'
```

> `-J root@172.16.255.1` 로 로컬에서 직접 ProxyJump 하면 `administratively prohibited` 로 실패했습니다.
> 라우터에서 **원격으로 ssh 를 다시 실행**하는 방식이 동작합니다.

---

## 6. 알려진 함정 (재발 방지)

| 증상 | 원인 / 해결 |
|---|---|
| ANTLR 이 `:` 를 삼킴 | `WORD`/`RULEWORD` 에서 `:` 제외 → `INDEX`+`COLON` 분리 |
| `IPNAME` vs `ATTRWORD` 충돌 | 문자집합 동일 → **선언 순서**가 우선. 키 위치에서 양쪽 토큰 모두 허용 |
| 라우트 코드 `O>*` 가 3토큰으로 쪼개짐 | `ROUTECODE` 를 "코드+마커" 단일 토큰으로 정의 |
| Arista `Access Mode VLAN: 8 (VLAN8)` → `88` | 괄호 앞 숫자만 파싱 |
| `Json x{Json::array()}` → `[[]]` | `Json x = Json::array();` 사용 (initializer_list 문제) |
| Linux `ip route show` 라우트 0건 | FRR 문법은 라우트 코드 필수 → `vendor==kUbuntu` 는 `IpAddr` 문법 사용 |
| `route_table.prefix`/`next_hop` NULL | Linux 파서는 `destination`/`via` 로 출력 → `FirstOf()` 로 흡수 |
| Arista 수집 전부 빈 값 | FastCli pty 프롬프트 타이밍 문제 → 파이프 방식 실행으로 전환 |
| 비root 에서 `Runtime initialization failed` | 환경변수 오버라이드 3종 사용 |
| Release 에서 `database_service_test` 행 | `assert(expr)` 내부 부수효과가 `-DNDEBUG` 로 제거됨 → 부수효과를 assert 밖으로 이동 |
| Arista SFTP 실패 | `scp -O` 로 대체. EOS `copy http://` 는 default VRF 라 mgmt 호스트 도달 불가, base64 heredoc 은 약 16384B 제한 |
| **DB가 비어 보임** | **수집 주기 기본 30초**. 프로버를 30초 미만 실행하면 첫 수집 전에 종료된다. 60~70초 이상 실행할 것 |
| **FRR 수집 전면 누락** | `vtysh` 때문에 제품이 `"FRR"` 로 감지되는데 수집기에 FRR 분기가 없어 수집 생략됨 → `ProductKind::kFrr` 추가 |
| **FRR 라우팅 0건** | `ip route show`(커널)와 `show ip route`(vtysh) 형식이 다름 → 내용 판별로 문법 선택 |
| **musl에서 실행 불가** | Alpine 은 glibc 바이너리를 못 씀 → 완전 정적 빌드 필요 |
| **`-DSQLITE3_LIBRARY` 무시됨** | CMake 표준 변수 `-DSQLite3_LIBRARY` 를 써야 함 |
| ANTLR 소스 압축 구조 | `runtime/Cpp` 가 아니라 `runtime/` 이며, 최상위에서 cmake 구성 |
| **`ssh -J` 실패** | 라우터 경유 ProxyJump 는 `administratively prohibited`. 라우터에서 원격 `ssh` 재실행 방식 사용 |

---

## 7. 배포 도구 — `parser/tools/`

| 파일 | 용도 |
|---|---|
| `node_probe.py` | 장치 probing (Cisco/Arista/Ubuntu, Arista→VM 2단 접속) |
| `deploy_arista.sh` | Arista sftp/scp 배포 (`SCP_MODE=1` 로 scp -O) |
| `deploy_cisco.py` | GNS3 콘솔(5018) 경유 guestshell 배포 |
| `run_prober.sh` | Arista 실행 래퍼 |
| `ws_collector.py` | 표준 라이브러리 WebSocket 수신기 (검증용) |

---

## 8. 현재 상태 / 다음 단계

### 완료 ✅

- [x] 제어 명령어 문서 (`docs/Agent_Command.md`) 보강, OPNsense 제거
- [x] ANTLR 문법 5종 + 파서 구현 (파서 테스트 169/169)
- [x] DB 스키마 확장 + `telemetry_store` (신규 6테이블)
- [x] 수집기 계층 벤더 분기 (수집기 테스트 82/82)
- [x] ctest 8/8
- [x] Mermaid 다이어그램 7종
- [x] **Arista E2E 검증**
- [x] **Cisco E2E 검증**
- [x] **서버 텔레메트리 전송 검증**
- [x] **Linux VM 경로 검증(호스트)**
- [x] **ANTLR C++ 런타임 복구 + 파서 포함 재빌드**
- [x] **PoC 랩 접속 경로 확립** (172.16.255.1 → VM ProxyJump)
- [x] **Ubuntu VM(10.30.141.10) 검증** — nic 2 / addr 4 / route 2 / arp 1
- [x] **FRR 라우터(172.16.255.1, Alpine) 검증** — nic 20 / addr 18 / **route 30** / arp 66
- [x] **FRR 수집 지원 추가** (이전 결함 수정)
- [x] **musl(Alpine)용 완전 정적 빌드 확립**

### 이번 세션에서 수정한 결함 2건 ⚠️

1. **FRR 수집 전면 누락**
   `DetectProductName()` 이 `vtysh` 존재를 보고 제품을 `"FRR"` 로 판정하는데,
   수집기 `ClassifyProduct()` 에 FRR 분기가 없어 `kOther` 로 떨어져
   **조회 명령을 하나도 실행하지 않았습니다.** (`nic_info` 조차 0건)
   → `ProductKind::kFrr` 추가 + `ip a`/`ip route show`/`ip neigh show` 실행.
   `linux_style` 판정에도 FRR 포함.

2. **FRR 라우팅 0건 (문법 선택 오류)**
   FRR 라우터는 라우팅을 **두 형식**으로 낼 수 있습니다.
   - (A) 호스트 커널 `ip route show` — 라우트 코드 없음, `nhid` 토큰 있음
   - (B) vtysh `show ip route` — 라우트 코드(`O>*`, `C` …) 있음
   `ParseRouteStatus()` 가 `vendor == kUbuntu` 일 때만 `IpAddr` 문법을 써서
   kFrr 은 코드 필수인 `FrrRouter` 문법으로 가 **0건**이 됐습니다.
   → `LooksLikeVtyshRouteTable()` 로 내용을 판별해 문법을 선택.

> 두 결함 모두 **회귀 테스트를 추가**했습니다.
> - `parser/cli_output_parser_test.cpp` → `TestFrrKernelRoute()` (15줄 실측 샘플)
> - `collector/command_collector_test.cpp` → `TestFrrRouter()`

### 반드시 기억할 동작 특성 ⏱️

**텔레메트리 수집 주기 기본값은 30초입니다** (`TelemetryMonitor::interval_seconds_{30}`).
프로버를 30초 미만으로 실행하면 **첫 수집이 일어나기 전에 종료**되어
DB가 비어 보입니다. 검증할 때는 최소 35초, 안전하게 **60~70초** 실행하세요.
(이 때문에 처음에 `route_table` 0건으로 오진했습니다.)

### 남음 ⏳

- [ ] **Alpine firewall(172.16.255.2)** — SSH 비밀번호 확보 필요 (키 거부됨)
- [ ] **OpenVSwitch 스위치(Switch-0~4)** — 관리 IP/GNS3 콘솔 접근 경로 확보 필요
- [ ] 추가 라우터(DMZ/C4I/Surv/VDI, 172.16.255.3~.6) — 비밀번호 확보 시 동일 방식 배포

### 검증 착수 순서 (합의됨)

1. ~~세션 메모리 정리~~ ✅
2. ~~ANTLR 툴체인 복구 → 파서 포함 정식 빌드~~ ✅
3. ~~Ubuntu VM 배포·검증 → FRR 라우터~~ ✅


---

## 9. 배포 시 유의사항 요약

1. **정적 링크**: `-static -static-libgcc -static-libstdc++` (CentOS 7 glibc 2.17, CentOS Stream 8 glibc 2.28 대응). 단 `-lssl -lcrypto` 는 사용 불가.
2. Ubuntu VM(glibc 2.40)은 호스트(2.39)보다 최신이므로 **동적 링크 바이너리도 그대로 실행 가능**.
3. Alpine/busybox 대상은 `ash` 셸 기준으로 스크립트 작성 (`setsid -w`, 일부 GNU 옵션 미지원).
4. 비root 실행 시 `SONAR_DATA_DIR` / `SONAR_TEMPLATE_PATH` / `SONAR_CONFIG_PATH` **3종 모두** 지정.
5. 서버로 보내는 로직은 **건드리지 않았습니다** — 파서/수집/저장만 추가했습니다.

---

## 10. 참고 문서

| 문서 | 내용 |
|---|---|
| `docs/Agent/Agent_Architecture_Diagrams.md` | 아키텍처 Mermaid 7종 |
| `docs/Agent/SonarValidator_Prober_API.md` | 클래스/메서드 API 레퍼런스 |
| `docs/Agent_Command.md` | 에이전트 제어 명령어 |
| `docs/Agent/{Router,Switch,VM,Firewall}_Policy_Design.md` | 장치별 정책 설계 |
| `PoC용 네트워크 구성하기/네트워크_구성_요약.md` | PoC 랩 토폴로지/IP/포트 |
| `SonarValidator_Prober/incontext_guide.md` | 프로버 사용 가이드 |
