# Real-to-Virtual-Infrastructure (RVI) — 랩 접속 자격증명

> **이 문서의 범위**: RVI 랩(GNS3) 노드 접속 정보와 API 자격증명 상태.
> **D-AI-PBL-PoC 네트워크와 다른 랩입니다.** 관리망 대역이 `10.20.0.0/24` 입니다.

작성일: 2026-09-26

> ### ⚠️ 자격증명 취급 규칙
>
> 이 저장소는 **public** 입니다. 그래서 이 문서는 **비밀번호 평문을 담지 않고**
> 환경변수 이름만 적습니다. 랩에서 쓰려면 아래를 export 하세요.
>
> ```bash
> export SONAR_CISCO_PW='...'      # Cisco 8000v
> export SONAR_OPNSENSE_PW='...'   # OPNsense root
> export SONAR_ARISTA_PW='...'     # Arista admin
> export SONAR_UBUNTU_PW='...'     # Ubuntu VM
> ```
>
> 실제 값은 저장소 밖(개인 노트/비밀 관리 도구)에 보관하십시오.
> API Key/Secret 도 원문을 담지 않았습니다 (기존 키는 401 로 무효였습니다).
출처: `git show 2ec4053:"Poc용 네트워크-D-AI-PBL/네트워크_설정_및_id_pw.md"` (현 트리에서 제거됨) + 실측

---

## 1. 노드 접속 정보

| 노드 | 관리 IP | 접속 방식 | 계정 | 비밀번호 | 실측 |
| --- | --- | --- | --- | --- | --- |
| CiscoCatalyst8000V-Router | `10.20.0.1` | telnet `localhost:5018` (guestshell) | — | `$SONAR_CISCO_PW` | ⚠️ SSH 인증 실패 (22 open) |
| OPNsense-Firewall | `10.20.0.2` | SSH `root` | `root` | `$SONAR_OPNSENSE_PW` | ✅ 접속 성공 |
| AristaEOS-Switch | `10.20.0.4` | SSH `admin` → `enable` → `bash` | `admin` | `$SONAR_ARISTA_PW` | ✅ 접속 성공 |
| Ubuntu-24-VM | `10.0.8.0/24` (Subnet A) | telnet `localhost:5021` | `ubuntu` | `$SONAR_UBUNTU_PW` | 미확인 (직접 라우팅 없음) |
| Ubuntu-24-VM1 | `10.0.9.100` (Subnet B) | telnet `localhost:5027` | `ubuntu` | `$SONAR_UBUNTU_PW` | 미확인 (Arista 경유 필요) |
| Management-Console | `10.20.0.3` | vnc `localhost:5900` | — | — | 테스트 실행 호스트 |
| GNS3 호스트 | `192.168.122.1` | SSH `ssh1032007` | `ssh1032007` | (키 인증) | ⚠️ **키 미등록** — 2026-09-26 재확인 |

### 1.1 GNS3 콘솔 포트 매핑

| 노드 | 포트 | 노드 | 포트 |
| --- | --- | --- | --- |
| CiscoCatalyst8000V-Router | 5018 | OPNsense-Firewall | 5052 |
| AristaEOS-Switch | 5038 | Ubuntu-24-VM | 5021 |
| Ubuntu-24-VM1 | 5027 | Management-Console | 5900 (vnc) |

> ⚠️ **콘솔 포트는 GNS3 호스트의 `127.0.0.1` 에만 바인딩됩니다.**
> Management-Console(`10.20.0.3`)에서 직접 telnet 할 수 없고,
> GNS3 호스트(`192.168.122.1`)에 SSH 로 들어간 뒤 `gns3_console.py` 로 접속해야 합니다.
>
> 현재 GNS3 호스트 SSH 키가 미등록이라 콘솔 경유 작업은 **보류** 상태입니다.

### 1.1.1 GNS3 호스트 접속 상태 (2026-09-26 실측)

| 확인 | 결과 |
| --- | --- |
| SSH 포트 22 | 열려 있음 |
| 비밀번호 프롬프트 | **응답함** (키 등록 전에도 시도 가능) |
| 공개키 인증 | ❌ 거부 — `Permission denied (publickey,password)` |
| GNS3 콘솔 포트 5018/5038/5052/5021/5027 | **전부 closed** (`localhost` 바인딩) |

**즉 GNS3 호스트가 콘솔 경유 작업의 유일한 관문**입니다.
이 호스트에 접속하지 못하면 다음이 모두 막힙니다.

- Cisco 8000v Agent 배포 (telnet 5018 → guestshell)
- Ubuntu VM ×2 Agent 배포 (telnet 5021/5027)
- 컨테이너 노드 접근 (docker exec)

**해결 방법** — 아래 중 하나를 사용자가 실행합니다.

```bash
# 방법 1: 공개키 등록 (권장)
ssh-copy-id ssh1032007@192.168.122.1

# 방법 2: 비밀번호를 아는 경우 — 이 대화에 값을 붙이지 말고
#         직접 로그인해 ~/.ssh/authorized_keys 에 추가
ssh ssh1032007@192.168.122.1
```

등록 후 확인:

```bash
ssh -o BatchMode=yes ssh1032007@192.168.122.1 'echo OK'
```

> ⚠️ 콘솔 포트가 `localhost` 에만 바인딩되어 있으므로, 이 호스트에서
> `python3 gns3_console.py <포트>` 를 실행해야 합니다.
> (`deployment/_shared/gns3_console.py`)

### 1.2 Arista 접속 절차 (실측 확정)

```
ssh admin@10.20.0.4        # pw: $SONAR_ARISTA_PW
ARISTA> enable
ARISTA# bash
root@ARISTA:~#             # EOS 는 CentOS 7 기반 (glibc 2.17)
```

- `admin` 키 인증은 거부됨 → **비밀번호 인증 필수** (`PubkeyAuthentication=no`)
- `admin` 계정은 초기 셸이 EOS CLI 이므로 `enable` → `bash` 를 거쳐야 Linux 명령 사용 가능
- Agent 바이너리는 **정적 링크 필수** (CentOS 7 glibc 2.17)

### 1.3 OPNsense 접속 절차 (실측 확정)

```
ssh root@10.20.0.2         # pw: $SONAR_OPNSENSE_PW
# 로그인 직후 메뉴가 뜬다 → 8 = shell
Enter an option: 8
root@OPNsense:~ #          # FreeBSD 14.3-RELEASE-p16 / OPNsense 26.1
```

> ⚠️ 초기 셸은 **csh** 입니다. `$(...)`, `2>/dev/null`, `for` 가 동작하지 않으므로
> (`Illegal variable name`) Linux 명령을 쓰려면 `/bin/sh` 로 전환하세요.
> Agent 를 배포하지는 않지만, API 키 발급·설정 확인 시 필요합니다.

---

## 2. OPNsense API 자격증명

### 2.1 결론 — ✅ **API 연동 완료** (2026-09-26 정정)

| 항목 | 상태 |
| --- | --- |
| 실제 API 호출 | ✅ **HTTP 200** — 정상 동작 |
| 백엔드 연결 검증 | ✅ `status=OK`, `detected_version=26.1.11_10` |
| 진단 5개 대상 | ✅ **5/5 PASS** (firmware·interfaces·rules·nat·aliases) |
| Agent 배포 | ❌ **하지 않음 — REST API 전용** |

> ⚠️ **이전 판정 정정**: 과거 이 문서는 *"HTTP 401 → 사용 불가 → 건너뜀"* 로 기록했습니다.
> 실제로는 **키 자체가 무효가 아니라, 다른 키를 사용했기 때문**이었습니다.
> 유효한 키로 호출하면 관리망(`http://10.20.0.2`)에서 **정상적으로 200** 이 옵니다.

### 2.1.1 ⚠️ OPNsense 는 Agent 를 배포하지 않습니다

| 항목 | 값 |
| --- | --- |
| OS | **FreeBSD 14.3** (Linux 아님) |
| prober 요구사항 | Linux (`/proc`, `/sys`) |
| Linux 바이너리 실행 | Linuxulator 커널 모듈 + `/compat/linux` + glibc 트리 필요 |
| **방침** | **Agent 미배포 · REST API 로만 검증** |

**Agent 는 Linux 계열 노드(Cisco guestshell · Arista vEOS · Ubuntu)에만 배포합니다.**
OPNsense 는 공식 REST API 가 인터페이스·규칙·NAT 를 **구조화된 형태**로 제공하므로
Agent 없이 **더 정확한 데이터**를 얻습니다.

### 2.2 실측 근거 (갱신)

검증된 자격증명 (`~/OPNSense_API_TEST/payload.sh`):

```
API Key    : jFXy…(masked — 평문 보관 금지)
API Secret : (masked)
엔드포인트 : http://10.20.0.2/api/core/firmware/info
```

호출 결과:

```bash
curl -k -u "<KEY>:<SECRET>" http://10.20.0.2/api/core/firmware/info
# → HTTP 200  ✅  (평문 http 80 만 열려 있음 — 443 closed)
```

**과거 401 의 원인** — 함께 보관돼 있던 **다른 키**를 사용했습니다.
키 자체가 무효라고 단정한 것이 오진이었습니다.

> 상세 진단 기록: [RVI 실장비 연동 테스트 v2.0](https://shseo2023.atlassian.net/wiki/spaces/SONAR/pages/1704055)

### 2.3 API 인증 방식

OPNsense API 는 **Key 를 사용자 이름 자리에 넣어 Basic 인증**합니다.

```
Authorization: Basic base64(apiKey + ":" + apiSecret)
```

> ⚠️ Key 와 Secret 을 **반대로** 넣으면 `Authentication Failed` 만 나와
> 원인을 찾기 어렵습니다. 순서가 중요합니다.

### 2.4 UI 검증 결과 (2026-09-26 실측 — 갱신)

**등록·검증·진단 전 경로가 실제 데이터로 동작합니다.**

| UI 동작 | 결과 |
| --- | --- |
| `/project` → Add Agent → OPNsense 카드 → 모달 | ✅ |
| 주소/Key/Secret 입력 → 저장 | ✅ 200, **`status=OK`** + 버전 감지 |
| **연결 테스트** 버튼 | ✅ 마지막 확인 시각 갱신 |
| **원문 조회** 버튼 | ✅ **HTTP 200** — 인터페이스 8건·규칙 25건 |

**수집된 실제 데이터**

| identifier | description | addr4 | status |
| --- | --- | --- | --- |
| wan | WAN | 172.128.0.2/24 | up |
| opt1 | Data_Plane_Transit | 172.18.10.1/24 | up |
| **lan** | **LAN** | **`10.20.0.2/24`** | **up** |
| lo0 | Loopback | 127.0.0.1/8 | up |

> 필터 규칙 **25건**(Default deny 포함), NAT 규칙 **0건** (주소 변환 미사용 구성)

### 2.5 ⚠️ OPNsense API 경로는 버전마다 바뀝니다 (실측)

| 용도 | 26.1 동작 경로 | 비고 |
| --- | --- | --- |
| 인터페이스 | `/api/interfaces/overview/interfacesInfo` | `…interface/get` 는 **404** |
| NAT | `/api/firewall/filter/get` → `filter.snatrules` | `search_nat` **404**, `search_rule?type=nat` 은 **type 무시** |
| 필터 규칙 | `/api/firewall/filter/search_rule?rowCount=-1` | |
| 별칭 | `/api/firewall/alias/search_item?rowCount=-1` | |

> 응답이 **200 이어도 의도한 대상이 아닐 수 있습니다**. 실장비 검증이 필요합니다. (`SONAR-31`)

### 2.6 저장 후 마스킹

| 항목 | 결과 |
| --- | --- |
| API Key 노출 | `jFXy****` 로 마스킹 |
| API Secret 노출 | ✅ **평문 미노출** (AES-256-GCM 암호화 저장) |

> ⚠️ **UI 검증 중 실제 결함을 발견했습니다** (커밋 `b9ea29c`).
> 프론트는 `base_url`(snake_case)로 보내는데 백엔드 `CredentialRequest` record 가
> camelCase 로만 바인딩되어 **전 필드가 null** 이 되고
> `400 "OPNsense 주소(base_url)는 필수입니다"` 로만 보였습니다.
> `@JsonProperty` 를 명시해 수정했고, 수정 후 400 → 200 으로 정상화됐습니다.

> ⚠️ **백엔드 시크릿 키를 반드시 주입하세요.**
> `SONAR_SECRET_KEY` 미주입 시 임시 키가 생성되어
> **재시작하면 저장된 Secret 복호화가 실패**합니다 (`SONAR-32`).
>
> ```bash
> openssl rand -base64 32 > ~/.sonar_secret_key
> chmod 600 ~/.sonar_secret_key
> export SONAR_SECRET_KEY="$(cat ~/.sonar_secret_key)"
> ```

---

## 3. RVI 네트워크 접속에 필요한 비밀번호 요약

| 대상 | 계정 | 비밀번호 |
| --- | --- | --- |
| Arista vEOS | `admin` | `$SONAR_ARISTA_PW` |
| OPNsense | `root` | `$SONAR_OPNSENSE_PW` |
| Cisco 8000v | (문서상) | `$SONAR_CISCO_PW` |
| Ubuntu VM / VM1 | `ubuntu` | `$SONAR_UBUNTU_PW` |
| GNS3 호스트 | `ssh1032007` | 키 인증 (등록 필요) |

> ⚠️ 이 문서는 **랩 전용 자격증명**입니다. 운영/외부 저장소에 커밋하지 마세요.
> GNS3 호스트 키 등록은 사용자가 아래를 실행합니다.
>
> ```bash
> ssh-copy-id ssh1032007@192.168.122.1
> ```

---

## 4. 해소 현황 (2026-09-26 갱신)

초기 기록의 "테스트 불가" 항목 중 **대부분 해소**되었습니다.

| # | 항목 | 초기 사유 | **현재 상태** |
| --- | --- | --- | --- |
| B1 | OPNsense 자격증명·verify·probe | API Key 401 → 불가 | ✅ **해소** — `status=OK`, 진단 **5/5 PASS** |
| B2 | Cisco 8000v Agent 배포 | SSH 인증 실패 | ✅ **해소** — 콘솔 경유 배포 완료, `connected=true` |
| B3 | GNS3 콘솔 경유 작업 | GNS3 호스트 SSH 키 미등록 | ✅ **해소** — 키 등록 완료, 콘솔 경유 정상 |
| B4 | Ubuntu-24-VM / VM1 배포 | `10.0.8/9.x` 직접 라우팅 없음 | ⏳ **미해소** — GNS3 콘솔(5021/5027) 경유 필요 |

**해소되지 않은 항목**

| # | 항목 | 막힌 지점 | 다음 단계 |
| --- | --- | --- | --- |
| B4 | Ubuntu VM 2대 Agent | 관리망 → 데이터평면 경로 없음 | GNS3 콘솔 경유 배포 |
| B5 | Cisco 관리망 직접 연결 | `Gi4 ip nat inside` 미지정 + 서버 반환 경로 없음 | `SONAR-30` |
| B6 | Cisco 인터페이스 기반 정책 | guestshell 이 호스트 IOS 포트를 못 봄 | IOS CLI 수집 경로 신설 |
| B7 | SIGTERM 정상 종료 | connect/handshake 타임아웃 부재 | `SONAR-25` |

**최종 배포·연동 현황**

| 노드 | 방식 | 상태 |
| --- | --- | --- |
| CiscoCatalyst8000V-Router | Agent (guestshell) | ✅ 연결·텔레메트리 |
| AristaEOS-Switch | Agent (SSH) | ✅ 연결 유지 |
| **OPNsense-Firewall** | **REST API 전용** (Agent 미배포) | ✅ 진단 5/5 |
| Ubuntu-24-VM / VM1 | Agent | ⏳ 미배포 |

---

*RVI 랩 접속 자격증명 — 2026-09-26*