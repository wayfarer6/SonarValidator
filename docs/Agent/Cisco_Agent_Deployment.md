# Cisco Catalyst 8000v — Agent 배포 방법

**대상 장비**: `CiscoCatalyst8000V-Router` (`10.20.0.1`)
**플랫폼**: Cisco IOS-XE + Guest Shell (CentOS Stream 8 LXC)
**검증일**: 2026-09-26 — **배포 성공** (`connected=true`)
**도구**: `deployment/real-to-virtual/cisco_console.py`

> 진단·원인 분석은 별도 문서를 보세요: [`Cisco_Guestshell_Troubleshooting.md`](./Cisco_Guestshell_Troubleshooting.md)

---

## 1. 왜 콘솔인가 (SSH 불가)

| 방법 | 결과 |
| --- | --- |
| `ssh cisco@10.20.0.1` | ❌ **거부** — IOS 관리자 계정은 CLI 전용 |
| SSH 공개키 등록 (`ip ssh pubkey-chain`) | ❌ **거부** — fingerprint 는 일치했으나 로그인 실패 |
| **GNS3 콘솔 (`telnet 5018`)** | ✅ **유일하게 성공한 경로** |

**이유**: guestshell 은 `192.168.35.0/24` 전용 대역에 격리되어 있고,
`GUEST_NAT_ACL` 로 **Gi1(NAT)로만** 나갑니다.
관리망에서 guestshell 의 sshd 로 들어가는 경로가 애초에 없습니다.

> 포트 포워딩(`ip nat inside source static tcp 192.168.35.2 7023 ...`)으로 우회할 수는 있으나
> guestshell sshd 를 따로 켜야 해서 이득이 없습니다.

---

## 2. 사전 조건

| 항목 | 값 | 확인 |
| --- | --- | --- |
| GNS3 호스트 SSH | `ssh1032007@192.168.122.1` (키 인증) | 필수 |
| 콘솔 포트 | GNS3 호스트의 `127.0.0.1:5018` | 필수 |
| 정적 바이너리 | `SonarValidator_Prober/build_static/sonar_validator_prober` | ELF 64-bit **statically linked** |
| 백엔드 | `192.168.122.32:3000` 실행 중 | 필수 |
| HTTP 서버 | 배포 파일 제공용 (예: `:8899`) | 필수 |
| 관리자 비밀번호 | `~/.sonar_cisco_pw` (권한 600) | 자동 인식 |

### 2.1 왜 정적 빌드인가

guestshell 은 **CentOS Stream 8 (glibc 2.28)** 입니다.
동적 빌드는 의존성 불일치로 실행되지 않으므로 **정적 링크가 필수**입니다.

```bash
file sonar_validator_prober
# ELF 64-bit LSB executable, x86-64, statically linked
```

### 2.2 비밀번호 관리 (커밋 금지)

평문 비밀번호를 셸 이력이나 프로세스 목록에 남기지 않도록 파일로 둡니다.

```bash
# GNS3 호스트에서 한 번만
umask 077
printf %s '<CISCO_PW>' > ~/.sonar_cisco_pw
chmod 600 ~/.sonar_cisco_pw
```

`cisco_console.py` 는 다음 순서로 비밀번호를 찾습니다.

1. `--password` 인수
2. `SONAR_CISCO_PW` 환경변수
3. **`~/.sonar_cisco_pw` 파일** ← 권장

---

## 3. 토폴로지 (실측)

```
                          ┌──────────────────────────────┐
                          │  서버(osboxes)                │
                          │  ens3 10.20.0.3   (관리망)    │
                          │  ens4 192.168.122.32 (NAT)   │
                          └───────────┬──────────────────┘
                                      │
        ┌─────────────────────────────┴─────────────────┐
        │                                               │
   10.20.0.0/24                                  192.168.122.0/24
        │                                               │
   ┌────┴─────────────────────┐              ┌──────────┴───────────┐
   │  Cisco 8000v             │              │  GNS3 호스트          │
   │  Gi4  10.20.0.1          │              │  192.168.122.1       │
   │  Gi1  192.168.122.254    │◄─────────────┤  콘솔 127.0.0.1:5018 │
   │  VPG0 192.168.35.1       │              └──────────────────────┘
   └──────────┬───────────────┘
              │  192.168.35.0/24
        ┌─────┴──────────────┐
        │  guestshell        │
        │  192.168.35.2      │
        │  (CentOS Stream 8) │
        └────────────────────┘
```

### 3.1 ⚠️ 핵심 제약: `SERVER_IP` 는 NAT 주소

| guestshell → | 결과 |
| --- | --- |
| `192.168.122.32` (NAT) | ✅ **TCP_OK** |
| `10.20.0.3` (관리망) | ❌ **TCP FAIL** |
| `10.20.0.1` (Gi4 자신) | ✅ ping OK |

guestshell 은 NAT 로만 나가므로 **`SERVER_IP=192.168.122.32`** 를 써야 합니다.
관리 주소를 넣으면 Agent 가 **조용히 무응답**이 됩니다.

> 근본 원인: Gi4 가 관리망에 붙어 있으나 `ip nat inside` 가 없어
> NAT 출구가 Gi1 로 고정되고 반환 경로가 깨집니다. → **SONAR-30**

---

## 4. 배포 절차

### 4.1 배포 자산 준비 (서버에서)

```bash
cd /home/osboxes/SonarValidator
rm -rf /tmp/cisco_deploy && mkdir -p /tmp/cisco_deploy

cp SonarValidator_Prober/build_static/sonar_validator_prober /tmp/cisco_deploy/
cp SonarValidator_Prober/Installer/default_template.sqlite      /tmp/cisco_deploy/
chmod 644 /tmp/cisco_deploy/*
```

> `default.conf` 는 **전송하지 않습니다.** 서버 주소를 주입해야 하므로
> `cisco_console.py` 가 배포 시점에 생성해 base64 로 보냅니다.

### 4.2 HTTP 서버 기동 (서버에서)

```bash
cd /tmp/cisco_deploy
nohup python3 -m http.server 8899 --bind 0.0.0.0 > /tmp/cisco_http.log 2>&1 &

# 확인
curl -s -o /dev/null -w "%{http_code}\n" http://192.168.122.32:8899/sonar_validator_prober
# → 200
```

### 4.3 Agent 등록 (서버에서)

배포 예정으로 등록해야 Agent List 에 **한 줄**로 표시됩니다.

```bash
curl -s -c /tmp/sv_cookies.txt -X POST http://localhost:3000/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'

curl -s -b /tmp/sv_cookies.txt -X POST http://localhost:3000/api/v1/agents/expected \
  -H 'Content-Type: application/json' \
  -d '{
        "agent_id": "CiscoCatalyst8000V-Router",
        "project_id": "PRJ-97537C11",
        "device_type": "CISCO",
        "node_type": "Router",
        "expected_ip": "10.20.0.1"
      }'
```

> ⚠️ `agent_id` 는 프로버의 `AGENT_NAME` 과 **정확히 같아야** 합니다.
> 다르면 같은 장치가 두 줄로 나타납니다.

### 4.4 도구 전송 (서버 → GNS3 호스트)

```bash
cd deployment/real-to-virtual
scp cisco_console.py ssh1032007@192.168.122.1:/tmp/
```

### 4.5 배포 실행 (GNS3 호스트에서)

```bash
ssh ssh1032007@192.168.122.1 \
  'python3 /tmp/cisco_console.py --action deploy \
     --http 192.168.122.32:8899 \
     --server-ip 192.168.122.32'
```

### 4.6 정상 배포 출력 (실측)

```
=== IOS copy → bootflash:guest-share (공유 디렉터리) ===
  sonar_validator_prober    → 전송 완료
  default_template.sqlite   → 전송 완료
=== guestshell 홈 확인 ===
  home=/home/guestshell
  → 배포 경로: /home/guestshell/svdir
=== 디렉터리 준비 ===
MKDIR_OK
=== 실행 래퍼 생성 (base64 전송) ===
B64_OK
=== 설정 파일 생성 (서버 주소 주입) ===
  SERVER_IP=192.168.122.32
  AGENT_NAME=CiscoCatalyst8000V-Router
=== 파일 배치 (guest-share → guestshell 홈) ===
  COPIED_sonar_validator_prober
  COPIED_default_template.sqlite
B64_OK
=== 실행 ===
STARTED pid=1187
```

### 4.7 검증

```bash
# GNS3 호스트에서 — 프로세스/로그/설정
ssh ssh1032007@192.168.122.1 'python3 /tmp/cisco_console.py --action status'

# 서버에서 — 백엔드 연결
curl -s -b /tmp/sv_cookies.txt http://localhost:3000/api/v1/agents | python3 -m json.tool

# 서버에서 — 텔레메트리
curl -s -b /tmp/sv_cookies.txt \
  http://localhost:3000/api/v1/agents/CiscoCatalyst8000V-Router/telemetry
```

**성공 기준**

| 항목 | 기대값 |
| --- | --- |
| 프로세스 | `RUNNING` |
| 백엔드 `connected` | `true`, `last_seen` 갱신 |
| 텔레메트리 | `product=Cisco 8000v`, `vendor=Cisco 8000v` |
| `settings.conf` | `SERVER_IP=192.168.122.32`, `NODE_TYPE=Router` |

---

## 5. 사용한 명령 (전체 요약)

### 5.1 IOS (콘솔)

| 명령 | 용도 |
| --- | --- |
| `enable` → 비밀번호 | 관리자 모드 |
| `terminal length 0` | 페이징 해제 (스크래핑 필수) |
| `show ip interface brief` | 인터페이스 IP/상태 |
| `show ip route` | 라우팅 테이블 |
| `show app-hosting list` | guestshell 상태 |
| `show app-hosting detail` | NIC·자원 |
| `show running-config \| include ip nat` | NAT 규칙 |
| `show ip nat statistics` | **Inside/Outside 인터페이스 확인** |
| `show ip access-lists GUEST_NAT_ACL` | NAT 대상 대역 |
| `copy http://<서버>/<파일> bootflash:guest-share/<파일>` | 파일 전송 |
| `guestshell` | guestshell 진입 |
| `write memory` | 설정 저장 |

> `show guestshell` 은 이 IOS-XE 에서 **미지원**입니다. `show app-hosting ...` 을 쓰세요.

### 5.2 guestshell (bash)

| 명령 | 용도 |
| --- | --- |
| `printf 'GHOME=%s\n' "$HOME"` | 홈 경로 확인 |
| `mkdir -p <dir>/data` | 배포 디렉터리 |
| `base64 -d <f>.b64 > <f>` | base64 전송 파일 복원 |
| `cp -f /bootflash/guest-share/<f> <dir>/` | 공유 디렉터리에서 복사 |
| `chmod +x sonar_validator_prober` | 실행 권한 |
| `sh <dir>/run.sh` | 실행 |
| `pgrep -f sonar_validator_prober` | 프로세스 확인 |
| `tail -25 <dir>/run.log` | 로그 |
| `cat <dir>/data/settings.conf` | 적용된 설정 |
| `timeout 4 bash -c 'echo > /dev/tcp/<ip>/3000'` | TCP 도달성 |

---

## 6. 파일 전송 경로 (핵심 함정)

`/bootflash` 루트는 guestshell 이 **읽을 수 없습니다** (`nobody:network-admin` 소유).

```
❌ copy http://... bootflash:sonar_validator_prober
   → guestshell: cp /bootflash/... → MISSING (권한 없음)

✅ copy http://... bootflash:guest-share/sonar_validator_prober
   → guestshell: cp /bootflash/guest-share/... → COPIED
```

**IOS 와 guestshell 이 공유하는 `/bootflash/guest-share` 를 경유해야 합니다.**

```
             ┌───────────────┐
 서버 HTTP ──►│  IOS copy     │──► /bootflash/guest-share/<파일>
             └───────────────┘              │
                                            │  (공유 디렉터리)
                                            ▼
                              guestshell: cp → /home/guestshell/svdir/
```

소량 파일(`default.conf`, `run.sh`)은 **base64 로 직접 전송**합니다.
telnet 콘솔에 heredoc 을 쓰면 들여쓰기·변수 확장이 얽혀 조용히 깨지기 때문입니다.

---

## 7. 런타임 레이아웃 (guestshell)

```
/home/guestshell/svdir/
├── sonar_validator_prober      ← 정적 바이너리 (chmod +x)
├── default_template.sqlite     ← 정책 템플릿
├── default.conf                ← SERVER_IP 주입됨
├── run.sh                      ← 실행 래퍼
├── run.log                     ← stdout/stderr
└── data/
    └── settings.conf           ← 프로버가 생성 (AGENT_NAME 등)
```

### 7.1 실행 래퍼가 하는 일

```sh
export SONAR_DATA_DIR="$ROOT/data"
export SONAR_TEMPLATE_PATH="$ROOT/default_template.sqlite"
export SONAR_CONFIG_PATH="$ROOT/default.conf"

# 이전 인스턴스는 SIGTERM 으로 종료 (kill -9 는 SQLite hot journal 을 남김)
for p in /proc/[0-9]*; do
  e=$(readlink "$p/exe" 2>/dev/null) || continue
  case "$e" in *sonar_validator_prober) kill -TERM "${p#/proc/}" ;; esac
done
sleep 2
cd "$ROOT"
nohup ./sonar_validator_prober > run.log 2>&1 &
```

> **root 가 아님(uid 1000)** 이므로 모든 경로가 `$HOME` 아래여야 합니다.
> `$HOME` 확장이 깨지는 경우가 있어 **절대경로**(`/home/guestshell/svdir`)를 씁니다.

---

## 8. 중지 / 재배포

```bash
# 중지
ssh ssh1032007@192.168.122.1 'python3 /tmp/cisco_console.py --action stop'

# 재배포 (덮어쓰기 — copy 프롬프트 자동 승인)
ssh ssh1032007@192.168.122.1 \
  'python3 /tmp/cisco_console.py --action deploy \
     --http 192.168.122.32:8899 --server-ip 192.168.122.32'
```

파일이 이미 있으면 IOS `copy` 가 `over write? [confirm]` 을 묻습니다.
도구가 `confirm="\r\n"` 으로 자동 승인합니다.

---

## 9. 트러블슈팅

| 증상 | 원인 | 대응 |
| --- | --- | --- |
| Agent 가 **무응답** | `SERVER_IP` 에 관리 주소 사용 | **NAT 주소**로 바꾸기 (`192.168.122.32`) |
| `MISSING_<파일>` | `/bootflash` 루트에 복사함 | `bootflash:guest-share/` 로 |
| `No such file or directory` | 바이너리 미전송 또는 권한 없음 | `ls -la <dir>`, `chmod +x` |
| `bash: show: command not found` | guestshell 안에서 IOS 명령 실행 | `exit` 후 IOS 프롬프트에서 |
| `% Invalid input` (`show guestshell`) | 이 IOS-XE 미지원 | `show app-hosting ...` |
| SSH 로그인 거부 | guestshell 격리 (설계) | 콘솔 경유 (제1장) |
| `nic_status` 가 `Unexpected` | guestshell 은 컨테이너 — 호스트 IOS 인터페이스 안 보임 | 알려진 한계 (§10) |
| 프로버 종료 안 됨 | SONAR-25 (타임아웃 부재) | `kill -TERM`, 이후 `kill -9` |

---

## 10. 알려진 한계

| 한계 | 원인 | 영향 |
| --- | --- | --- |
| `nic_status: Unexpected` | guestshell(LXC)은 호스트 IOS 인터페이스를 볼 수 없음 | 인터페이스 기반 정책이 Cisco 실제 포트를 못 봄 |
| 관리망 직접 연결 불가 | Gi4 `ip nat inside` 미지정 + 서버 반환 경로 없음 (**SONAR-30**) | NAT 주소로만 배포 가능 |
| SSH 배포 불가 | guestshell 전용 대역 + NAT 격리 | 배포마다 콘솔 스크래핑 |
| SIGTERM 미종료 | connect/handshake 타임아웃 부재 (**SONAR-25**) | `kill -9` 필요 |

---

## 11. 관련 문서

### 11.1 저장소 문서

| 문서 | 내용 |
| --- | --- |
| `Cisco_Guestshell_Troubleshooting.md` | 진단·원인 분석, Cisco 매뉴얼 링크 |
| `docs/Agent/Appendix_SIGTERM_Hang_Analysis.md` | SIGTERM 문제 분석 |
| `deployment/real-to-virtual/cisco_console.py` | 배포 도구 (inspect/deploy/status/stop) |
| `deployment/real-to-virtual/cisco_mgmt_path.py` | 관리망 경로 진단 |

### 11.2 Cisco 공식 문서

| 주제 | 링크 |
| --- | --- |
| **Guest Shell (프로그래밍 가이드)** | [Cisco IOS-XE Guest Shell](https://www.cisco.com/c/en/us/td/docs/ios-xml/ios/prog/configuration/171/b_171_programmability_cg/guest_shell.html) |
| Application Hosting | [Application Hosting Configuration Guide](https://www.cisco.com/c/en/us/td/docs/ios-xml/ios/app-hosting/configuration/xe-17/app-hosting-xe-17-book.html) |
| `app-hosting` 명령 | [Cisco IOS-XE Command Reference — app-hosting](https://www.cisco.com/c/en/us/td/docs/ios-xml/ios/app-hosting/command/ah-cr-book.html) |
| IOx 개발자 가이드 | [IOx Developer Guide (DevNet)](https://developer.cisco.com/docs/iox/) |
| Guestshell 자동화 | [Guestshell Automation (DevNet)](https://developer.cisco.com/docs/ios-xe/#!guestshell) |
| Catalyst 8000v | [Cisco Catalyst 8000V Edge Software](https://www.cisco.com/c/en/us/products/routers/catalyst-8000v-edge-software/index.html) |

### 11.3 관련 Jira

| 키 | 내용 |
| --- | --- |
| **SONAR-30** | Gi4 관리망 연결 + `ip nat inside` 미지정 → guestshell 관리망 미도달 |
| SONAR-25 | SIGTERM 으로 프로버가 종료되지 않음 |

---

*Cisco Catalyst 8000v Agent 배포 방법 — 2026-09-26 검증*