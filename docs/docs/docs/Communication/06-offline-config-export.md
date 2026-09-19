---
title: "06. 오프라인 설정 내보내기 / 가져오기"
sidebar_label: "06. 오프라인 설정 Export/Import"
---

# 오프라인 설정 내보내기 / 가져오기 (Offline Config Export & Import)

> 대상 독자: 프로버(Agent)를 배포하는 운영자, 백엔드/프론트엔드 개발자
> 최종 갱신: 2026-09-19

---

## 1. 한 줄 요약

**관리 서버에 연결할 수 없는 장비**는 프로버가 수집 결과를 JSON 파일로 남기고,
운영자가 그 파일을 **프론트엔드에 끌어다 놓으면** 서버가 **온라인 텔레메트리와
완전히 같은 파서**로 변환해 장치 목록에 반영합니다.

```
   서버 미도달 장비                     브라우저                      서버
 ┌────────────────┐   파일 전송   ┌─────────────────┐  multipart  ┌──────────────┐
 │ Prober         │ ───────────► │ Import Offline  │ ──────────► │ DeviceConfig │
 │ --export-once  │              │ Prober Data 카드 │             │ Service      │
 └────────────────┘              └─────────────────┘             │ (온라인과    │
                                                                  │  동일 파서)  │
                                                                  └──────────────┘
```

---

## 2. 왜 필요한가

망분리 환경이나 관리망이 개통되지 않은 랩에서는 프로버가 **수집은 정상적으로
하지만 갈 곳이 없습니다.** 이때 두 가지 나쁜 선택지가 있었습니다.

| 기존 동작 | 문제 |
| --- | --- |
| 조용히 유실 | 운영자가 "수집이 안 되고 있다" 고 오인하고 프로버를 재배포/디버깅합니다 |
| 서버 연결을 강제 | 망분리가 목적인 랩에서 서버로 가는 경로를 뚫어야 합니다 (본말전도) |

그래서 **세 번째 길**을 만듭니다. 수집은 로컬에서 끝내고, 결과를 파일로
가져와 나중에 반영합니다.

---

## 3. Agent (C++ Prober)

### 3.1 신규 모듈

| 파일 | 역할 |
| --- | --- |
| `offline/offline_export.hpp` | 스키마 상수, 문서 조립, 파일 저장/읽기/대기열 API |
| `offline/offline_export.cpp` | 구현 (원자적 쓰기, 파일 이름 정제, 안전한 파싱) |
| `offline/offline_export_test.cpp` | 단위 테스트 (네트워크/SQLite 불필요) |

### 3.2 CLI 옵션

```bash
# 서버로 보내지 않고 스냅샷 파일만 남기며 상시 실행
./sonar_validator_prober --export-offline

# 한 번만 수집하고 파일 하나를 만들고 종료 (가장 흔한 사용법)
./sonar_validator_prober --export-once

# 저장 위치 지정
./sonar_validator_prober --export-once --export-dir /mnt/flash/snapshots

# 파일을 만들 수 없는 환경(원격 콘솔): 표준출력으로 인쇄 → 복사해서 업로드
./sonar_validator_prober --export-once --export-stdout

# 도움말
./sonar_validator_prober --help
```

| 옵션 | 동작 |
| --- | --- |
| `--export-offline` | 서버 전송을 **시도하지 않고** 항상 파일로만 남깁니다 |
| `--export-dir <경로>` | 스냅샷 저장 위치 (기본: `<데이터 디렉터리>/offline`) |
| `--export-once` | 한 번 수집하고 종료합니다 |
| `--export-stdout` | 스냅샷 JSON 을 표준출력으로 인쇄합니다 |
| `--help`, `-h` | 사용법 출력 |

**환경변수** (우선순위: CLI 인자 > 환경변수 > 기본값)

| 변수 | 용도 |
| --- | --- |
| `SONAR_OFFLINE_DIR` | 스냅샷 저장 디렉터리 |
| `SONAR_DATA_DIR` | 데이터(DB/설정) 디렉터리 (기존) |
| `SONAR_TEMPLATE_PATH` | SQLite 템플릿 경로 (기존) |

### 3.3 자동 폴백 (옵션 없이도 동작)

`--export-offline` 을 주지 않아도, **전송이 실패하면** 같은 주기의 스냅샷이
자동으로 저장됩니다. 즉 서버가 잠깐 죽어 있던 동안의 수집도 잃지 않습니다.

```
[TELEMETRY] offline snapshot saved: /var/lib/.../offline/sonar_snapshot_c8000v-1_2026-09-19T04-00-00Z.json
```

### 3.4 스냅샷 파일 형식

```json
{
  "schema": "sonar.offline.snapshot",
  "schema_version": 1,
  "agent_id": "c8000v-1",
  "agent_name": "c8000v-1",
  "device_type": "ROUTER",
  "product": "Cisco 8000v",
  "vendor": "Cisco 8000v",
  "kernel": "17.09.04a",
  "collected_at": "2026-09-19T04:00:00Z",
  "exported_at": "2026-09-19T04:00:01Z",
  "reason": "server-unreachable",
  "payload": {
    "agent": "c8000v-1",
    "product": "Cisco 8000v",
    "vendor": "Cisco 8000v",
    "device_type": "ROUTER",
    "nic_status":    { "interfaces": [ ... ] },
    "route_status":  { "routes": [ ... ] },
    "arp_table":     { "entries": [ ... ] },
    "vlan_status":   { "vlans": [ ... ] },
    "trunk_status":  { "ports": [ ... ] },
    "firewall_rules":{ "tables": [ ... ] },
    "ovs_topology":  { "bridges": [ ... ] }
  }
}
```

#### 핵심: `payload` 는 온라인 텔레메트리와 **같은 구조**

`payload` 의 키 집합은 WebSocket `telemetry` 봉투의 payload 와 **완전히
동일**합니다. 이것이 이 설계의 유일한 핵심 결정입니다.

- 서버의 `DeviceConfigService` 는 어느 경로로 들어왔든 **같은 벤더별 파서**를 씁니다.
- 그래서 파서를 두 벌 만들 필요가 없고, 두 경로가 조용히 어긋나지 않습니다.

> ⚠️ 과거에 payload 조립부가 `firewall_rules` / `ovs_topology` 를 빠뜨려
> 서버에서 규칙/브리지가 전부 빈 값이 된 적이 있습니다. 그래서 조립 코드를
> `BuildTelemetryPayload()` **한 함수**로 모으고 온라인/오프라인이 함께 씁니다.

`reason` 값:

| 값 | 의미 |
| --- | --- |
| `server-unreachable` | 전송 실패로 자동 저장 |
| `offline-mode` | `--export-offline` 으로 강제 저장 |
| `manual-export` | `--export-once` 로 수동 저장 |
| `server-export` | 서버에서 내려받음 (역방향) |

### 3.5 파일 이름 규칙

```
sonar_snapshot_<agent_id>_<collected_at>.json
     ↑                  ↑
     kFilePrefix        ':' → '-' 치환 (윈도우에서도 안전)
```

에이전트 이름에 `:` `/` `\` `공백` 이 있어도 `_` 로 정제되므로 파일 생성이
실패하지 않습니다. 파일 이름에 타임스탬프가 들어 있으므로
**사전순 정렬 = 시간순** 이고, 이것이 재전송 순서를 보장합니다.

### 3.6 안전성 설계

| 위험 | 대응 |
| --- | --- |
| 쓰기 도중 프로세스 종료 → 반쪽 파일 | `.tmp` 에 쓰고 `rename` (같은 파일시스템에서 원자적) |
| `rename` 이 막히는 컨테이너 볼륨 | `copy_file` + `remove` 로 폴백 |
| 손상된 파일이 재전송 루프 정지 | `ReadSnapshot()` 이 예외 대신 `null` 반환 |
| 디스크 가득참 | 예외를 흡수하고 사유 로그만 남김 (수집 루프는 계속) |
| 저장 위치 권한 없음 | 최후 폴백 `/tmp/sonar_validator_offline` |

---

## 4. Backend (Spring)

### 4.1 신규 파일

| 파일 | 역할 |
| --- | --- |
| `Service/OfflineSnapshotService.java` | 스키마 검증 + 파서 위임 + 결과 보고 |
| `Controller/OfflineImportController.java` | 업로드/내보내기/조회 REST |

### 4.2 변경 파일

| 파일 | 변경 |
| --- | --- |
| `Service/AgentMessageRouterService.java` | `acceptOfflineTelemetry()`, `isOfflineOrigin()` 추가 |

### 4.3 REST 엔드포인트

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/offline/schema` | 서버가 기대하는 스키마 안내 |
| `POST` | `/api/v1/offline/import` | 파일 업로드 (`multipart/form-data`, 필드명 `files`) |
| `POST` | `/api/v1/offline/import` | JSON 본문 직접 업로드 (`application/json`) |
| `GET` | `/api/v1/offline/imported` | 파일로 반영된 장치 목록 |
| `GET` | `/api/v1/offline/export/{agentId}` | 현재 설정을 스냅샷 형식으로 내보내기 |

### 4.4 왜 항상 200 을 돌려주는가

여러 파일을 한 번에 올릴 수 있으므로 **"일부만 성공" 이 정상적인 결과**입니다.
HTTP 상태로 표현할 수 없으므로 항상 200 과 함께 파일별 결과를 돌려줍니다.

```json
{
  "received": 4,
  "accepted": 1,
  "rejected": 3,
  "snapshots": [
    {
      "file_name": "roundtrip.json",
      "accepted": true,
      "agent_id": "c8000v-e2e",
      "format": "CISCO_IOS",
      "interfaces": 2,
      "routes": 3,
      "vlans": 0,
      "firewall_rules": 0,
      "warnings": ["VLAN/trunk not collected for Cisco (IOS CLI lacks switchport show)"],
      "errors": []
    },
    {
      "file_name": "broken.json",
      "accepted": false,
      "agent_id": null,
      "format": null,
      "interfaces": 0, "routes": 0, "vlans": 0, "firewall_rules": 0,
      "warnings": [],
      "errors": ["JSON 파싱 실패: Illegal unquoted character ..."]
    }
  ],
  "server_warnings": [],
  "accepted_device_ids": ["c8000v-e2e"]
}
```

### 4.5 거부 사유 6종 (전부 사유를 남김)

| 사유 | 언제 | 왜 거부하는가 |
| --- | --- | --- |
| `schema 불일치` | 다른 JSON 을 올림 | 파서가 해석할 수 없음 |
| `payload 가 없거나 객체가 아닙니다` | 형식 오류 | 구조를 추측하지 않음 |
| `payload 가 비어 있습니다` | 수집 성공 항목 0건 | **조용한 성공 방지** — 받아들이면 화면이 비는데 원인을 모름 |
| `제품명(product/vendor)이 없어…` | `product` 누락 | 파서 선택 불가 |
| `파서 '…' 가 아무 항목도 해석하지 못했습니다` | 키 이름 불일치 | **조용한 성공 방지** — 진단용으로 `keys=N` 을 함께 남김 |
| `파일이 너무 큽니다` | 8MB 초과 | 무제한 업로드 방지 |

> **설계 원칙: 추측하지 않는다.**
> 조용히 절반만 반영하면 운영자는 "올렸는데 왜 안 보이지" 를 디버깅합니다.
> 그래서 항상 `accepted` / `errors` 를 함께 돌려줍니다.

### 4.6 전방 호환 (schema_version)

서버보다 **높은** `schema_version` 은 **거부하지 않고** 경고만 남깁니다.
거부하면 새 Agent 가 만든 파일을 구 서버가 못 읽게 되기 때문입니다.
모르는 필드는 무시하고 아는 필드만 씁니다.

### 4.7 온라인/오프라인 구분

오프라인으로 들어온 설정은 `lastSeen` 을 **채우지 않습니다.** 채우면 UI 가
"연결됨" 으로 잘못 표시합니다. 대신 `offlineOrigins` 집합에 기록해
`isOfflineOrigin()` 으로 조회합니다.

---

## 5. Frontend (React)

### 5.1 신규 파일

| 파일 | 역할 |
| --- | --- |
| `lib/api/offline.ts` | 업로드/내보내기/조회 API + 클라이언트 사전 검증 |
| `components/offline/OfflineImportCard.tsx` | 드래그앤드롭 카드 |

### 5.2 변경 파일

| 파일 | 변경 |
| --- | --- |
| `pages/SubnetAdvanceConfiguration.tsx` | 기존 스텁 드롭존 → `OfflineImportCard` 교체 |
| `pages/Agent.tsx` | "설정 내보내기" 열 추가 (JSON 내려받기) |

### 5.3 드래그앤드롭에서 조용히 틀리기 쉬운 4가지

구현 시 실제로 문제가 되는 지점들이라 전부 방어했습니다.

#### ① `dragover` 에서 `preventDefault()` 를 빼면 `drop` 이 **아예 안 옵니다**

브라우저 기본 동작이 "파일 열기" 라서 여기서 막아야 합니다. 이벤트가
발생하지 않으므로 **오류도 안 뜨고 그냥 아무 일도 일어나지 않습니다.**

```tsx
const handleDragOver = (event: DragEvent<HTMLDivElement>) => {
  event.preventDefault();              // ← 이 한 줄이 없으면 drop 이 안 옵니다
  event.dataTransfer.dropEffect = "copy";
};
```

#### ② `dragenter`/`dragleave` 는 **자식 요소마다** 다시 발생합니다

깊이 카운터가 없으면 테두리 강조가 깜빡거립니다.

```tsx
const dragDepth = useRef(0);

const handleDragEnter = (e) => { e.preventDefault(); dragDepth.current += 1; setIsDragOver(true); };
const handleDragLeave = (e) => {
  e.preventDefault();
  dragDepth.current = Math.max(0, dragDepth.current - 1);
  if (dragDepth.current === 0) setIsDragOver(false);   // 0 일 때만 해제
};
```

#### ③ `dataTransfer.files` 는 **`drop` 이후에만** 채워집니다

`dragover` 에서 읽으면 빈 목록이라 "드롭했는데 아무 일도 안 일어남" 이 됩니다.
그래서 파일 목록은 `handleDrop` 에서만 읽습니다.

#### ④ 같은 파일을 두 번 올리면 **같은 설정이 두 번 반영**됩니다

`이름 + 크기` 를 키로 중복을 제외하고, 업로드 성공 후 대기 목록을 비웁니다.

```tsx
const seen = new Set(previous.map((f) => `${f.name}:${f.size}`));
```

### 5.4 부가 방어

| 상황 | 대응 |
| --- | --- |
| 폴더를 끌어다 놓음 | `precheckFile()` 이 확장자/크기를 먼저 검사해 **업로드 전에** 사유 표시 |
| 8MB 초과 파일 | 서버 왕복 없이 즉시 안내 |
| 드래그를 못 쓰는 환경 | 클릭 → 파일 선택 경로 (`<input type="file">`) 제공 |
| 키보드만 사용 | `role="button"` + `tabIndex` + `Enter`/`Space` 처리 |
| 백엔드 미기동 | `ApiError.status === 0` 으로 구분해 화면에 표시 |
| 내보내기 실패 | Blob 대신 응답을 먼저 확인 → 오류 JSON 이 파일로 저장되는 것을 막음 |

> **사전 검증의 위치**: `precheckFile()` 은 **업로드 여부만** 결정합니다.
> 통과해도 서버가 거부할 수 있으므로 "성공" 으로 표시하면 안 됩니다.
> 최종 판정은 항상 서버입니다.

---

## 6. 검증 결과 (2026-09-19)

### 6.1 빌드 / 테스트

| 대상 | 결과 |
| --- | --- |
| C++ `ctest` | **9/9 통과** (신규 `offline_export_test` 포함) |
| 백엔드 `./mvnw clean test` | **77/77 통과** (신규 `OfflineSnapshotImportTest` 12건) |
| 프론트 `tsc -b` | 오류 0 |
| 프론트 `npm run build` | 성공 |

### 6.2 실서버 E2E (포트 3200, H2 파일 DB 분리)

| 단계 | 결과 |
| --- | --- |
| 로그인 (`admin`/`admin`) | 200 |
| `GET /offline/schema` | 파서 6종 목록 반환 (`CISCO_IOS`, `FRRRouter`, `OpenvSwitch`, `AlpineFirewall`, `LinuxVM`, `ARISTA_vEOS`) |
| Cisco 8000v 스냅샷 업로드 | `accepted=1`, `format=CISCO_IOS`, 인터페이스 2 · 경로 3 |
| `GET /network/discovered` | `c8000v-e2e` 가 `discovered=true` 로 **UI 목록에 나타남** |
| `GET /offline/export/{id}` | 원본 payload 그대로 반환 |
| **내보낸 파일 재업로드 (왕복)** | `accepted=1`, 인터페이스 2 · 경로 3 — **완전 복원** |
| 혼합 배치 (정상 1 + 손상 1 + 스키마 오류 1 + 빈 payload 1) | `received=4 accepted=1 rejected=3`, 파일별 사유 전부 보고 |

**혼합 배치 실제 응답**:

```
received=4 accepted=1 rejected=3

[OK ] roundtrip.json
[FAIL] broken.json
        사유: JSON 파싱 실패: Illegal unquoted character ((CTRL-CHAR, code 10)) ...
[FAIL] wrongschema.json
        사유: schema 불일치: 기대 'sonar.offline.snapshot', 실제 'other.format'
[FAIL] emptypayload.json
        사유: payload 가 비어 있습니다. 수집에 성공한 항목이 없는 스냅샷입니다.
```

### 6.3 브라우저 검증

이 환경에서 브라우저 자동화(CDP 연결)가 타임아웃되어 **클릭 조작 검증은
수행하지 못했습니다.** 대신 다음으로 대체 검증했습니다.

- `tsc -b` 0 오류 (타입 수준 계약 확인)
- Vite 변환 결과에 `onDragEnter`/`onDragOver`/`onDragLeave`/`onDrop` 과
  `preventDefault` 5회가 모두 포함됨을 확인
- API 레벨 데이터 경로 전체(업로드 → 반영 → 조회 → 내보내기 → 왕복) 검증

> ⚠️ **미검증 항목**: 실제 마우스 드래그 조작, 다크 모드 렌더링.
> 실 배포 전 브라우저에서 한 번 확인하는 것이 좋습니다.

---

## 7. 운영 절차 (Runbook)

### 7.1 서버 미도달 장비에서 설정 뽑기

```bash
# 1) 프로버 정지 (반드시 SIGTERM 먼저 — kill -9 는 SQLite 를 깨뜨립니다)
pkill -TERM -f sonar_validator_prober

# 2) 한 번만 수집해 파일 생성
SONAR_DATA_DIR=/var/lib/sonar_validator_prober \
  ./sonar_validator_prober --export-once

# 3) 파일 확인
ls -l /var/lib/sonar_validator_prober/offline/
```

> ⚠️ **`pkill -f sonar_validator_prober` 는 자기 SSH 명령줄과 매칭되어 세션을
> 죽입니다.** 반드시 `/proc/[0-9]*/exe` 를 readlink 해서 대상만 종료하세요.

> ⏱️ **수집 주기는 30초입니다.** 프로버를 30초 미만 실행하면 첫 수집 전에
> 종료되어 결과가 비어 보입니다. `--export-once` 는 즉시 수집하므로 이
> 제약이 없지만, 상시 실행 모드에서 확인할 때는 60~70초 기다리세요.

Alpine(musl) 장비는 **정적 바이너리**여야 합니다:

```bash
cmake -S . -B build_static -DCMAKE_BUILD_TYPE=Release \
  -DSQLite3_LIBRARY=/usr/lib/x86_64-linux-gnu/libsqlite3.a \
  -DCMAKE_EXE_LINKER_FLAGS="-static -static-libgcc -static-libstdc++"
cmake --build build_static --target sonar_validator_prober -j $(nproc)
```

### 7.2 파일을 서버에 반영

1. SonarValidator 에 로그인합니다 (인증 필요 — `/api/v1/offline/**` 는 예외 아님).
2. **Project Create → Subnet Advance Configuration** 화면으로 이동합니다.
3. 우측 **Import Offline Prober Data** 카드에 파일을 끌어다 놓습니다.
4. 사전 검사에서 걸러진 파일이 있으면 카드가 사유를 표시합니다.
5. **N건 업로드** 버튼을 누릅니다.
6. 결과에 파일별 성공/실패와 사유가 표시되고,
   **파일로 반영된 장치** 목록이 갱신됩니다.
7. **View Detected Network Nodes** 화면에서 해당 장비를 확인합니다.

### 7.3 설정 백업 / 다른 랩으로 이관

**Agent** 화면의 각 행에서 **JSON** 버튼을 누르면 현재 설정이 스냅샷 형식으로
내려받아집니다. 이 파일을 다른 SonarValidator 인스턴스의 Import 카드에
올리면 그대로 복원됩니다 (왕복 검증 완료).

### 7.4 파일을 만들 수 없을 때 (원격 콘솔)

```bash
# 표준출력으로 인쇄 → 터미널에서 복사 → 파일로 저장 → 업로드
./sonar_validator_prober --export-once --export-stdout > snapshot.json
```

또는 JSON 본문을 직접 POST 합니다:

```bash
curl -b cookies.txt -X POST http://<server>:3000/api/v1/offline/import \
  -H 'Content-Type: application/json' \
  --data-binary @snapshot.json
```

---

## 8. 함정 정리 (재발 주의)

| 함정 | 증상 | 대응 |
| --- | --- | --- |
| `dragover` 에서 `preventDefault()` 누락 | 드롭해도 **아무 반응 없음** (오류도 안 뜸) | 반드시 호출 |
| `dragenter`/`dragleave` 카운터 없음 | 테두리 강조가 깜빡임 | 깊이 카운터 사용 |
| `dragover` 에서 `dataTransfer.files` 읽기 | 항상 빈 목록 | `drop` 에서만 읽기 |
| 중복 파일 업로드 | 같은 설정이 두 번 반영 | 이름+크기로 제외 |
| payload 조립 코드가 두 벌 | 한쪽만 키가 빠져 조용히 빈 값 | `BuildTelemetryPayload()` 단일 함수 공유 |
| 빈 payload 를 성공으로 처리 | 화면이 비는데 200 | 명시적 거부 + 사유 |
| 중립 설정을 직렬화해 내보내기 | 재업로드 시 **복원 안 됨** | 원본 텔레메트리 payload 를 그대로 실어 보냄 |
| 오프라인 설정에 `lastSeen` 기록 | UI 가 "연결됨" 으로 오표시 | `offlineOrigins` 로 별도 관리 |
| `/api/v1/offline/**` 를 인증 예외로 등록 | 누구나 설정을 주입 가능 | **예외 아님** — 로그인 필요 |
| CORS 에 새 헤더 미포함 | multipart 가 차단 | 기존 `WebMvcConfig` 규칙 그대로 사용 |

---

## 9. 파일 인벤토리

### Agent

```
SonarValidator_Prober/
├── offline/
│   ├── offline_export.hpp          # 스키마/문서/파일 API
│   ├── offline_export.cpp          # 구현
│   └── offline_export_test.cpp     # 단위 테스트 (9건)
├── telemetry/telemetry_monitor.hpp # SetOffline* / CollectSnapshotDocument 추가
├── telemetry/telemetry_monitor.cpp # BuildTelemetryPayload 추출, 저장/폴백
├── main.cpp                        # CLI 옵션 (--export-*)
├── switch/switch.hpp               # SwitchVendor::CiscoIosXe (9000v → IOS-XE)
└── CMakeLists.txt                  # offline 모듈 + 테스트 등록
```

### Backend

```
SonarValidator_Backend/src/main/java/.../
├── Service/OfflineSnapshotService.java      # 신규
├── Controller/OfflineImportController.java  # 신규
└── Service/AgentMessageRouterService.java   # acceptOfflineTelemetry 추가

src/test/java/.../
└── OfflineSnapshotImportTest.java           # 신규 (12건)
```

### Frontend

```
SonarValidator_Frontend/src/
├── lib/api/offline.ts                       # 신규
├── components/offline/OfflineImportCard.tsx # 신규
├── pages/SubnetAdvanceConfiguration.tsx     # 카드 교체
└── pages/Agent.tsx                          # 내보내기 열 추가
```

---

## 10. Cisco 표기 정리 (이번 작업에 포함)

시스코 장비는 **8000v(IOS-XE 라우터)** 를 씁니다. Catalyst 9000v 는 사용하지
않습니다. 코드에 9000v 로 적혀 있어 "지원 예정인 스위치" 로 오해를 만들던
부분을 정리했습니다.

| 변경 전 | 변경 후 | 이유 |
| --- | --- | --- |
| `SwitchVendor::CiscoCatalyst9000v` | `SwitchVendor::CiscoIosXe` | 실제 장비는 8000v 라우터 |
| 브리지 이름 `catalyst8000v` | `cisco-ios-xe` | 라우터인데 스위치명이라 혼동 |
| `parseCiscoSwitchTopology` 주석 처리 | 복원 (수집/파싱은 동작) | 정책 적용만 미지원이지 파싱은 가능 |
| `ApplyCiscoSwitchPolicy` 주석 | "8000v 라우터만 지원" 으로 명확화 | 오해 제거 |

> 정책 **적용**은 아직 8000v 라우터(`ApplyCiscoRouterPolicy`)만 지원합니다.
> 수집/파싱/설정 반영(오프라인 경로 포함)은 정상 동작합니다.

---

## 11. 브라우저 검증 (2026-09-19, 실측)

API 레벨 검증에 더해 **실제 브라우저에서 조작**해 확인했습니다.

### 11.1 Mermaid 뷰 확대/축소

토폴로지 뷰(`/project/create/preview`, `/network`)에서 측정한 값입니다.

| 동작 | 기대 | 실측 |
| --- | --- | --- |
| 초기 상태 | 100%, 스크롤 없음 | `100%`, `scrollWidth == clientWidth == 570` |
| `+` 4회 | 200%, 스크롤 범위 2배 | `200%`, `scrollWidth 1140`, `transform matrix(2,0,0,2,0,0)` |
| `−` 5회 | MIN(25%)에서 멈춤 | `25%`, 축소 버튼 `disabled=true` |
| `+` 20회 | MAX(400%)에서 멈춤 | `400%`, 확대 버튼 `disabled=true` |
| 키보드 `+` ×2 | 100% → 150% | `150%` |
| 키보드 `0` | 100% 복귀 | `100%` |
| 드래그 이동 (200%) | 스크롤 이동 | `scrollLeft 0 → 300`, 커서 `grab` |
| 드래그 (100%) | 무시됨 | 커서 `auto`, `scrollLeft 0` 유지 |
| `Ctrl`+휠 위 | 확대 + `preventDefault` | `200% → 225%`, `defaultPrevented=true` |
| 일반 휠 | 확대하지 않음 | `225%` 유지, `defaultPrevented=false` |

### 11.2 드래그앤드롭 업로드 (End-to-End)

`Import Offline Prober Data` 카드에 파일을 실제로 드롭했습니다.

| 단계 | 실측 결과 |
| --- | --- |
| `dragover` | `defaultPrevented=true` (드롭이 성립하는 조건) |
| `drop` | 대기 목록에 `browser_drop.json` (1.2 KB) 추가 |
| 업로드 | `1건 중 1건 반영, 0건 실패` |
| 파일별 결과 | `반영됨` + `browser-drop-test · CISCO_IOS · 인터페이스 1 · 경로 2 · 규칙 0` |
| 경고 표시 | `⚠ VLAN/trunk not collected for Cisco (IOS CLI lacks switchport show)` |
| 대기 목록 | 업로드 후 비워짐 (중복 반영 방지) |
| 반영 장치 목록 | `파일로 반영된 장치` 에 `browser-drop-test` 즉시 표시 |

### 11.3 분기 카드 (`Branch_Divider`)

`/project/create` 화면에서 확인했습니다.

| 요소 | 실측 |
| --- | --- |
| 카드 제목 | `환경 구성 방식` |
| 분할선 | `role="separator"`, `aria-orientation="horizontal"` |
| 배지 | `OR` + `서버 연결 가능 여부로 갈립니다` |
| 갈래 라벨 | `온라인`, `오프라인` 배지 모두 렌더링 |
| 오프라인 명령 | `./sonar_validator_prober --export-once` 표시 |
| 이동 버튼 | `오프라인 데이터 가져오기 화면으로 이동` |

---

## 12. 확대/축소에서 겪은 함정 2가지 (재발 주의)

구현 중 **조용히 실패**한 두 가지입니다. 오류가 나지 않아 원인 파악에 시간이 걸렸습니다.

### 12.1 측정 피드백 루프 → 다이어그램이 300px 로 수축

콘텐츠의 `offsetWidth` 를 재서 그 값을 다시 콘텐츠 `width` 로 설정하면 악순환이 생깁니다.

```
width 설정 → SVG 가 그 폭에 맞춰 줄어듦 (Mermaid 는 width:100% + max-width)
           → 다시 재면 더 작음 → 또 설정 → … → 300px 까지 수축
```

**증상**: 뷰포트가 570px 인데 콘텐츠가 300px 로 측정되고 스크롤 범위가 0.
확대 버튼은 눌리는데 아무 일도 일어나지 않습니다.

**해결**: 측정 대상을 콘텐츠에서 떼어냅니다.
1. **폭** — 스크롤 영역의 *바깥* wrapper 의 `clientWidth` (스크롤바 영향 없음)
2. **높이** — SVG `viewBox` 의 가로세로비 × 위 폭 (`viewBox` 는 컨테이너와 무관한 본질 크기)

### 12.2 Mermaid 인라인 `max-width` → 확대해도 552px 에서 멈춤

Mermaid 는 SVG 에 `style="max-width: 552px"` 를 넣습니다. 배율을 200% 로 올려도
SVG 가 552px 을 넘지 못했습니다.

**⚠️ 함정**: 이 스타일을 `useEffect` 에서 나중에 지우면 **효과가 없습니다.**
React 가 `dangerouslySetInnerHTML` 로 SVG 를 다시 주입할 때 인라인 스타일도
함께 되돌리기 때문입니다.

**증상이 오해를 부릅니다**: 같은 코드를 개발자 도구에서 직접 실행하면 값이
유지됩니다. 그래서 "코드는 맞는데 왜 안 되지" 로 보입니다.

**해결**: SVG **문자열을 주입하기 전에** 수정합니다. React 가 되돌릴 여지가 없고,
기준 크기도 같은 파싱에서 얻을 수 있습니다.

```ts
const newOpenTag = openTag
  .replace(/\sstyle="[^"]*"/i, "")   // Mermaid 의 max-width 제거
  .replace(/\swidth="[^"]*"/i, "")
  .replace(/\sheight="[^"]*"/i, "")
  .replace(/<svg/i, '<svg width="100%" height="100%" preserveAspectRatio="xMidYMid meet"');
```

### 12.3 버튼 연타가 누적되지 않던 문제

`setScale(scale + STEP)` 처럼 **현재 값을 직접 읽어** 계산하면, 연타 시 아직
갱신되지 않은 이전 값으로 계산되어 누적되지 않습니다.
(3번 눌러 150% 가 되는 증상)

**해결**: 함수형 업데이트를 씁니다.

```ts
setScale((previous) => clampScale(previous + ZOOM_STEP));
```

---

## 13. 다음 단계 (미구현)

| 항목 | 설명 |
| --- | --- |
| 다크 모드 렌더링 확인 | 확대/축소 툴바와 분할선의 다크 테마 대비 |
| 실 마우스 드래그 검증 | 합성 PointerEvent 로는 검증했으나, 실제 마우스 드래그는 미검증 |
| 재전송 자동화 | `ListPendingSnapshots()` / `ReadSnapshot()` / `RemoveSnapshot()` 은 구현됨. 서버 복구 시 자동 재전송 루프는 미연결 |
| 서버 측 스냅샷 영속화 | 현재 메모리 보관(`lastConfig`). 서버 재기동 시 소실 → JPA 저장 필요 |
| 오프라인 설정의 이력 관리 | 언제 어떤 파일로 반영됐는지 감사 로그 |
| 다중 장비 일괄 확인 UI | 반영된 장치를 프로젝트에 매핑하는 화면 |
| 확대 상태 저장 | 새로고침 후에도 배율 유지 (현재는 100% 로 초기화) |
