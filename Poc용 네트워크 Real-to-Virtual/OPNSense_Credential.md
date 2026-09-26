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

---

## 2. OPNsense API 자격증명

### 2.1 결론 — **API 자격증명 사용 불가 (건너뜀)**

| 항목 | 상태 |
| --- | --- |
| 설정(`/conf/config.xml`)의 API 키 항목 | **존재함** (root 사용자 소유) |
| 실제 API 호출 결과 | **HTTP 401 `Authentication Failed`** |
| 판정 | **사용 불가** → 이번 RVI 테스트에서 OPNsense 검증 **건너뜀** |

### 2.2 실측 근거

발견한 기존 자격증명 (`~/OPNSense_API_TEST/payload.sh` 에서 확인):

```
API Key    : jFXy…(masked — 401 무효였으므로 보관 가치 없음)
API Secret : (masked — 원문 미보관)
엔드포인트 : http://10.20.0.2/api/core/firmware/info
```

호출 결과:

```bash
curl -k -u "<KEY>:<SECRET>" http://10.20.0.2/api/core/firmware/info
# → HTTP 401
# → {"status":401,"message":"Authentication Failed"}
```

OPNsense 측 설정 상태:

```
/conf/config.xml 의 <user><name>root</name> 아래:
  <apikeys>jFXy…(masked — 401 무효였으므로 보관 가치 없음)|$6$$(masked)?</apikeys>
```

- Key 항목은 **설정에 남아 있음** (root 소유)
- 그러나 위 Secret 으로 인증이 통과하지 않음 → **Key 와 Secret 짝이 현재 유효하지 않음**
  (Secret 재발급/변경, 또는 Key 만 남고 Secret 원문이 소실된 상태로 추정)

### 2.3 유효한 자격증명을 만들려면 (사용자가 직접 수행)

OPNsense WebUI (`http://10.20.0.2`, 80 포트만 열려 있음 / 443 closed) 에서:

1. `root` / `$SONAR_OPNSENSE_PW` 로 로그인
2. **System → Access → Users → root** (또는 전용 API 사용자)
3. 하단 **API keys** 영역에서 **+** 클릭
4. 표시되는 **Key / Secret 을 즉시 복사** (Secret 은 다시 표시되지 않음)
5. 이 문서 §2.4 에 기록

> OPNsense API 는 **Key 를 사용자 이름 자리에 넣어 Basic 인증**합니다:
> `Authorization: Basic base64(apiKey + ":" + apiSecret)`
> 반대로 넣으면 `Authentication Failed` 만 나와 원인을 찾기 어렵습니다.

### 2.5 UI 검증 결과 (2026-09-26 실측)

**자격증명 자체는 무효지만, 애플리케이션의 등록·검증 경로는 전부 정상 동작합니다.**

| UI 동작 | 결과 |
| --- | --- |
| `/project` → Add Agent → OPNsense 카드 → 모달 | ✅ (API 준비 안내 + Basic 인증 형식 안내 표시) |
| 주소/Key/Secret 입력 → 저장 | ✅ 200, `status=FAILED` + 사유 표시 |
| **연결 테스트** 버튼 | ✅ 마지막 확인 시각 갱신 |
| **원문 조회** 버튼 | ✅ `HTTP 401` + `{"status":401,"message":"Authentication Failed"}` |
| 저장 후 마스킹 (`jFXy****`) | ✅ Secret 평문 미노출 |

> ⚠️ **UI 검증 중 실제 결함을 발견했습니다** (커밋 `b9ea29c`).
> 프론트는 `base_url`(snake_case)로 보내는데 백엔드 `CredentialRequest` record 가
> camelCase 로만 바인딩되어 **전 필드가 null** 이 되고
> `400 "OPNsense 주소(base_url)는 필수입니다"` 로만 보였습니다.
> `@JsonProperty` 를 명시해 수정했고, 수정 후 400 → 200 으로 정상화됐습니다.

**재발급 후에는 위 표 그대로 다시 수행하면 probe 까지 종단 검증이 됩니다.**

### 2.4 발급 시 기록 양식 (사용자 입력용)

```
API Key    :
API Secret :
발급일     :
발급 사용자:
검증 명령  : curl -k -u "<KEY>:<SECRET>" http://10.20.0.2/api/core/firmware/status
검증 결과  : (HTTP 200 이면 유효)
```

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

## 4. 이번 테스트에서 자격증명 때문에 **테스트 불가**인 항목

| # | 항목 | 사유 | 대체 검증 |
| --- | --- | --- | --- |
| B1 | **OPNsense 자격증명 등록·verify·probe** | API Key/Secret 짝이 401 → 사용 불가. 재발급은 사용자 작업 | `GET /opnsense/credentials` → `{total:0}`, UI 모달 열림/후보 목록만 확인 |
| B2 | **Cisco 8000v Agent 배포** | SSH 인증 실패 + GNS3 콘솔(telnet 5018) 경유 필요 | guestshell 경로 코드는 존재, 실배포 보류 |
| B3 | **GNS3 콘솔 경유 모든 작업** | GNS3 호스트(`192.168.122.1`) SSH 키 미등록 | — |
| B4 | **Ubuntu-24-VM / VM1 Agent 배포** | `10.0.8/9.x` 직접 라우팅 없음 + 콘솔 경유 필요 (B3) | — |

**대체 검증 가능 항목 (자격증명 확보됨)**: Arista vEOS Agent 배포·텔레메트리, 프론트엔드·백엔드 통합 시나리오(S1~S9), OPNsense UI 존재/모달 동작

---

*RVI 랩 접속 자격증명 — 2026-09-26*