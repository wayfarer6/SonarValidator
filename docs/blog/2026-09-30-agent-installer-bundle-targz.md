---
slug: agent-installer-bundle-targz
title: "Agent 설치 번들 — tar.gz 로 전환한 이유와 설계"
authors: [sonarvalidator]
tags: [agent, deployment, backend, frontend, troubleshooting]
date: 2026-09-30
---
**대상**: 프로버(Agent)를 배포하는 운영자, 백엔드/프론트엔드 개발자
**작성일**: 2026-09-30
**수정일**: 2026-10-06 — 번들에 바이너리 포함 (스테이징된 경우)
**계기**: Agent 목록 화면에서 장비용 설치 번들을 만들려 했는데 **IP/Port 를 지정할
자리가 없었고**, 있던 번들도 장비에서 **풀리지 않았다.** 또한 **바이너리 누락으로
설치가 불완전**했다.

---

{/* truncate */}

## 1. 한 줄 요약

**서버가 설정을 채운 설치 번들(`Installer/` 폴더 + `tar.gz`)을 브라우저에서 내려받아,
장비에서 `tar -xzf` 한 줄로 푼다.** 번들의 `default.conf` 에는 관리 서버
IP·Port·장치 유형·Agent 이름이 **이미 채워져 있다.**

```
   브라우저 (/project | /agent)                 서버                     장비
 ┌──────────────────────────┐   GET bundle   ┌──────────────┐  tar -xzf  ┌─────────────┐
 │ Setup Management Server  │ ─────────────► │ Installer/   │ ─────────► │ Installer/  │
 │  IP [10.20.0.3]          │                │  default.conf│            │  default.conf│
 │  Port [8443]             │                │  README.txt  │            │  README.txt │
 │ [설치 번들 받기 (tar.gz)] │ ◄───────────── │  *.sh        │            │  *.sh       │
 └──────────────────────────┘  application/  └──────────────┘            └─────────────┘
                                  gzip
```

---

## 2. 왜 서버가 설정을 채우는가

프로버는 `Installer/default.conf` 의 네 값으로 동작합니다.

```ini
SERVER_IP=10.20.0.3;
SERVER_PORT=8443;
NODE_TYPE=Router;       # Router / Switch / VM / Firewall 네 가지만
AGENT_NAME=Gateway-Router;
```

이 값을 **사람이 손으로 채우면 반드시 어긋납니다.** 세 값 모두 서버가 이미
알고 있는 정보인데도 그렇습니다.

| 손으로 채울 때의 실수 | 결과 |
| --- | --- |
| 관리망이 아닌 NAT 주소를 씀 | 관리망 전용 장치(스위치)가 서버에 못 닿음 → **로그 없이 무응답** |
| 장치 유형을 잘못 적음 | 정책 적용기가 엉뚱한 벤더 명령을 만듦 |
| `AGENT_NAME` 이 배포 예정 등록 이름과 다름 | 같은 장치가 목록에 **두 줄**로 나타나고, 등록한 장치는 영원히 무응답 |

특히 첫 번째가 위험합니다. 개발 호스트는 보통 두 망에 동시에 붙어 있어
(`ens3 = 관리망`, `ens4 = NAT`) "첫 번째 주소" 를 쓰면 NAT 주소가 나갈 수 있는데,
이때 프로버는 **오류를 내지 않고** 관리 서버를 못 찾습니다. 그래서 서버가
기본 라우트 인터페이스를 후순위로 두고 관리망 대역을 우선해 주소를 **스스로**
고릅니다.

---

## 3. ⚠️ 왜 ZIP 이 아니라 tar.gz 인가

번들은 **네트워크 장비에서** 풀립니다. 그 장비들의 사정이 개발 PC 와 다릅니다.

| 장비 | `unzip` | `tar` / `gzip` |
| --- | --- | --- |
| 라우터 (Alpine 기반) | ❌ 없음 | ✅ BusyBox 기본 포함 |
| 스위치 (Open vSwitch) | ❌ 없음 | ✅ 포함 |
| VM (Ubuntu) | 보통 있음 | ✅ 포함 |

즉 **ZIP 으로 주면 라우터·스위치에서 아무것도 못 합니다.** `tar`·`gzip` 은
사실상 모든 배포판에 있으므로, `tar -xzf` 한 줄로 풀리는 형식이 안전합니다.

### 3.1 구현

JDK 의 `java.util.zip` 은 ZIP 만 만듭니다. tar 헤더를 직접 인코딩하면
체크섬·긴 이름·모드 비트를 손으로 관리해야 하고, 실수하면 장비에서
`tar: invalid tar magic` 으로 **조용히** 풀리지 않습니다. 그래서
**Apache Commons Compress** 를 씁니다.

```java
try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(buffer);
     TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
    // Installer/ 아래에 파일을 담고, .sh 와 바이너리는 0755 로 둔다
    put(tar, "default.conf", ...);
    put(tar, "README.txt", ...);
    putFileIfPresent(tar, "sonar_validator_prober");  // 스테이징된 바이너리 포함
    putFileIfPresent(tar, "Installer.sh");            // 스크립트
    putFileIfPresent(tar, "restart.sh");
    putFileIfPresent(tar, "default_template.sqlite"); // DB 템플릿
    tar.finish();          // 마지막 1024바이트 0 블록
}
// ⚠️ toByteArray() 는 반드시 스트림을 모두 닫은 뒤!
final byte[] bundle = buffer.toByteArray();
```

### 3.2 바이너리 포함 (2026-10-06 변경사항)

프로버는 정적 링크 바이너리로 빌드되는데, 빌드 산출물이라 저장소에 커밋되지 않습니다.
운영 환경에서는 Docker 빌드 단계에서 정적 바이너리를 먼저 만들고, `/tmp/sonar_stage`
디렉터리에 배치합니다. 그러면 설치 번들은 그 바이너리를 찾아 자동으로 `Installer/`
폴더에 포함시킵니다.

| 상황 | 번들 내용 | 설치 절차 |
| --- | --- | --- |
| **바이너리가 스테이징됨** (Docker 배포) | `sonar_validator_prober` + 설정 | 번들 풀기 → `Installer.sh` 실행 |
| **바이너리가 없음** (개발 환경) | 설정·스크립트만 | ① 바이너리 빌드 ② `Installer.sh` 실행 |

바이너리가 없어도 번들 생성은 실패하지 않습니다(스크립트와 설정은 필수지만 바이너리는
선택). 대신 화면의 미리보기에 경고를 표시하고 README 에서 상세히 안내합니다.

### 3.3 ⚠️ 구현 중 잡은 함정 — 잘린 tar.gz

`ByteArrayOutputStream.toByteArray()` 를 **스트림 close 전에** 부르면 gzip
트레일러(CRC·길이)가 아직 안 쓰여 **잘린 파일**이 됩니다.

```
tar.finish();
return buffer.toByteArray();   // ❌ 블록 안에서 부름 → 잘림
```

오류가 나지 않습니다. 장비에서 `unexpected end of file` 로만 드러납니다.
새로 추가한 테스트가 이 실수를 잡아냈고, `toByteArray()` 를 try-with-resources
블록 **밖**으로 옮겨 해결했습니다.

또한 실행 파일(`.sh` 와 `sonar_validator_prober`)에 **0755** 를 주지 않으면
tar 기본값(0644)이 되어 `./Installer.sh: Permission denied` 로 보입니다.

---

## 4. ⚠️ 포트가 무시되던 결함 (가장 조용한 것)

화면은 `Management Server Port` 를 받아 **미리보기**에 보여주고 있었습니다.
그런데 실제 번들의 `default.conf` 는 서버 설정값(`server.port`)을 썼습니다.

| 위치 | 포트 |
| --- | --- |
| 화면 미리보기 | **8443** (입력값) |
| 실제 `default.conf` | **3000** (서버 설정값) |

운영자는 미리보기를 믿고 방화벽에 8443 을 열었다가, 프로버가 3000 으로 접속하는
것을 봅니다. **미리보기가 거짓말을 하고 있었습니다.**

수정: `server_port` 를 요청 파라미터로 받아 **주소와 같은 자리에서** 확정합니다.
범위(1~65535)를 벗어나거나 숫자가 아니면 조용히 무시하지 않고 **로그를 남기고**
서버 설정값으로 되돌립니다.

```
WARN server_port override out of range, using 3000: 70000
```

---

## 5. 화면: 같은 카드를 두 곳에서

번들 생성 UI 가 **프로젝트 목록의 Add Agent 에만** 있었습니다. Agent 목록
(`/agent`)에서 장비를 확인하던 운영자는 그 화면에 IP/Port 입력이 없어
"여기서는 서버 주소를 지정할 수 없다" 고 읽었습니다.

그래서 프로젝트 목록과 **같은** `AgentDeployCard` 를 Agent 목록에도 노출했습니다.
컴포넌트를 재사용하므로 IP·Port 지정 방식과 번들 생성이 두 화면에서 어긋날 수
없습니다.

| 화면 | projectId | 배포 예정 등록 |
| --- | --- | --- |
| `/project` (Add Agent) | 넘김 | ✅ |
| `/project/create` (Create Prober) | 넘김 | ✅ |
| `/agent` (설치 번들 만들기) | **안 넘김** | ❌ (감춤 + 이유 안내) |

Agent 목록은 프로젝트를 고르지 않고 들어오므로, 프로젝트 없이 등록하면 목록의
프로젝트 필터가 어긋납니다. 그래서 **등록 UI 만 감추고** 그 이유와 해결
(프로젝트 목록에서 Add Agent)을 카드가 안내합니다. (감추기만 하면
"왜 등록 버튼이 없지" 로 보입니다)

### 5.1 ⚠️ `Content-Disposition` 은 CORS 로 노출해야 한다

화면은 Blob 으로 받은 뒤 서버가 정한 **파일 이름**을 씁니다. 그런데 CORS 는
안전을 위해 응답 헤더를 기본적으로 가리므로, `Content-Disposition` 을
노출하지 않으면 JS 가 읽지 못하고 임시 이름을 씁니다.

```java
.exposedHeaders("Content-Disposition")
```

> 공유 링크(`<a href>`) 대신 Blob 을 받는 이유도 같은 계열입니다.
> `href` 로 열면 401/500 일 때 브라우저가 **오류 JSON 을 파일로 저장**하고,
> 운영자는 그것이 설치 번들인지 오류인지 구분하지 못합니다.

---

## 6. 번들 내용

```
sonar-agent-Gateway-Router.tar.gz
└── Installer/
    ├── default.conf            # 서버가 값을 채움
    ├── README.txt              # 이 장치에 맞춘 절차
    ├── Installer.sh  (0755)    # 스테이징되어 있으면
    ├── restart.sh    (0755)
    └── default_template.sqlite
```

**바이너리(`sonar_validator_prober`)는 넣지 않습니다.** 빌드 산출물이라 저장소에
없고, `README.txt` 가 HTTP 로 내려받는 절차를 안내합니다. (장비에 `curl` 이
없으면 `wget` 을 쓰라고 명시합니다 — 라우터에 `curl` 이 없습니다)

스테이징 디렉터리(`/tmp/sonar_stage`)에 스크립트·템플릿이 없으면 **조용히
건너뛰고**, `info` 응답에 `missing_assets` 경고를 실어 화면에서 보이게 합니다.
(설정만 있으면 프로버는 동작하므로 실패시키지 않습니다)

---

## 7. API

| 목적 | 호출 |
| --- | --- |
| 미리보기 | `GET /api/v1/agents/bundle/info?agent_id=&node_type=&server_ip=&server_port=` |
| 번들 | `GET /api/v1/agents/bundle/{agentId}?node_type=&server_ip=&server_port=&data_directory=` |

응답 헤더:

| 헤더 | 값 |
| --- | --- |
| `Content-Type` | `application/gzip` |
| `Content-Disposition` | `attachment; filename="sonar-agent-<이름>.tar.gz"` |

`info` 응답은 `archive_format`(`tar.gz`)·`installer_dir`(`Installer`)·
`server_ip_source`(`request`/`config`/`detected`/`fallback`)를 함께 줍니다.
후자가 중요한 이유는, 주소를 어디서 얻었는지 모르면 운영자가 "이 주소가 맞나" 를
판단할 근거가 없기 때문입니다.

---

## 8. 검증 (실측)

| 항목 | 결과 |
| --- | --- |
| gzip 매직 바이트 | `1f 8b` (ZIP 의 `50 4b` 가 아님) |
| Content-Type | `application/gzip` |
| 파일 이름 | `sonar-agent-smoke-agent.tar.gz` |
| 표준 `tar -tzf` 추출 | ✅ `Installer/default.conf`, `Installer/README.txt` |
| 포트 override 반영 | ✅ `SERVER_PORT=8443;` (설정값 3000 이 아니라) |
| 잘못된 포트(`abc`,`0`,`70000`,`-1`) | ✅ 경고 로그 후 설정값 복귀 |
| 실행 권한 | ✅ `.sh` 에 0755 |
| Backend 테스트 | ✅ **325 tests, 0 failures** (신규 9건 포함) |
| Frontend | ✅ `tsc` clean, eslint 0 errors |
| 문서 빌드 | ✅ Docusaurus build success |

브라우저 실측(`/agent`):

| 요소 | 실측 |
| --- | --- |
| 버튼 | `설치 번들 만들기` |
| 카드 | `Deploy & Download` + `Setup Management Server` |
| IP 입력 | `Set Management Server IP` (비어 있음) |
| Port 입력 | `Set Management Server Port` = `3000` |
| 안내 | `여기 넣은 IP·Port 는 아래 설정 미리보기와 설치 번들(default.conf)에 그대로 들어갑니다` |
| 다운로드 버튼 | `설정 포함 설치 번들 받기 (tar.gz)` |

---

## 9. 재발 주의 (한 줄 요약)

| 함정 | 교훈 |
| --- | --- |
| ZIP 은 장비에서 안 풀림 | 번들은 **푸는 주체의 도구**를 기준으로 형식을 고른다 |
| `toByteArray()` 를 close 전에 호출 | 압축 스트림은 **완전히 닫은 뒤** 바이트를 얻는다 |
| 미리보기와 실제 파일의 값이 다름 | 화면이 보여주는 값은 **실제 산출물과 같은 경로**로 흘려보낸다 |
| `Content-Disposition` 이 JS 에 안 보임 | CORS 는 응답 헤더를 기본적으로 가린다 — `exposedHeaders` |
| 등록 UI 를 그냥 감춤 | 감출 때는 **이유와 해결**을 함께 보여준다 |

---

## 10. 관련 이슈

| 키 | 내용 |
| --- | --- |
| [SONAR-45](https://shseo2023.atlassian.net/browse/SONAR-45) | 번들 `server_port` 무시 — 미리보기와 실제 파일의 포트가 다름 |
| [SONAR-46](https://shseo2023.atlassian.net/browse/SONAR-46) | 설치 번들이 ZIP 이라 라우터·스위치에서 안 풀림 → tar.gz 전환 |
| [SONAR-47](https://shseo2023.atlassian.net/browse/SONAR-47) | Agent 목록 화면에 IP/Port 지정·번들 생성 UI 없음 |

> 배포 절차는 Confluence **[Agent 배포 가이드 v2.1](https://shseo2023.atlassian.net/wiki/spaces/SONAR/pages/1769552)** §1.5 참고.