# Cisco guestshell dohost 실장비 진단 (2026-10-10)

대상: Cisco Catalyst 8000v `10.20.0.1` (guestshell ssh `-p 2222`)
검증: 실장비 SSH 접속 후 `dohost` 실행 관찰 + 에이전트 서비스 로그/설정 대조
증거 원본: [`cisco-dohost-probe.json`](./cisco-dohost-probe.json)

---

## 1. 결론

Cisco 에이전트가 명령을 못 처리하는 이유는 **프로버 코드가 아니라 실행 환경**입니다.
세 가지가 겹쳤습니다.

| # | 문제 | 확인된 증거 | 성격 |
| --- | --- | --- | --- |
| A | guestshell↔IOS CLI 세션(IOSP)이 없어 `dohost` 가 무조건 실패 | `dohost "show version"` → `Unexpected Error` (rc=0) | **환경** |
| B | 설치된 `default.conf` 가 **수정 안 된 템플릿** | 서버 IP `172.16.255.245`, `NODE_TYPE=VM`, `TERMINAL_SHARED_SECRET=`(빈 값) | **배포** |
| C | 에이전트가 실패 텍스트를 텔레메트리로 오인 | `commands=3, empty=0` (출력은 있었지만 파싱 불가) | **코드 (수정 완료)** |

부수적으로 SIGTERM 종료 결함(D)도 원인을 찾아 수정했습니다.

> **서버 주소 정정 (2026-10-10)**: 과거 문서의 "Cisco 는 `10.20.0.3` 실패,
> `192.168.122.32` 만 성공" 기록은 **일시적 현상**이었습니다. guestshell 에서
> `10.20.0.3` 으로 ping 0% 손실, TCP 3000 6/6 성공, WebSocket 업그레이드
> **101** (terminal/management/telemetry 전부)이 확인되었습니다.
> 이제 **`SERVER_IP=10.20.0.3`** 을 씁니다. 근거: [`cisco-reachability.json`](./cisco-reachability.json).
> 백엔드 호스트는 `ens3=10.20.0.3/24`(Cisco Gi4 와 같은 관리망),
> `ens4=192.168.122.32/24` 를 함께 가지고 있고 리스너는 `*:3000` 입니다.
> ufw 는 off, nftables 체인은 비어 있어 방화벽 문제가 아닙니다.

---

## 2. 실장비 검증 (dohost)

guestshell 에 키 인증으로 들어가 실제 실행한 결과입니다.

```
$ dohost "show version"
Unexpected Error
$ echo $?
0

$ dohost "show version" "show clock"
Unexpected Error

$ dohost_sh "show version"
dohost_sh: No SETUPINPROGRESS Var
dohost_sh: ERROR: IOSP_SESSION environment variable not set

$ python3 -c 'from cli import clip; clip("show version")'
Unexpected Error          # 반환값 None

$ python3 -c 'from cli import cli; cli("show version")'
IOSPUnexpectedError: Unexpected Error
```

핵심: `dohost` 는 **IOS 가 명령을 거부해도, 세션 자체가 없어도 종료코드 0** 을 돌려줍니다.
그래서 종료코드로는 성공/실패를 절대 구분할 수 없습니다.

---

## 3. 근본 원인 A — IOSP 세션이 없다

`/usr/bin/dohost` 는 `python3 /usr/lib/python3.6/site-packages/dohost.py` 를 실행하고,
그 안에서 Cisco `cli.clip()` 을 호출합니다. `cli/__init__.py` 의 세션 준비 코드는:

```python
def check_and_set_iosp_env():
    if 'IOSP_SESSION' in os.environ:        # ① 환경변수
        return
    if not os.path.isfile("/cisco/cisco_cli/app-session-info"):   # ② 세션 파일
        return                              # 둘 다 없으면 세션 준비 안 됨
```

실장비 확인 결과 **둘 다 없습니다**.

| 확인 | 결과 |
| --- | --- |
| `env \| grep IOSP` | 비어 있음 |
| `/cisco/cisco_cli/app-session-info` | **없음** (`/cisco/cisco_cli/` 디렉터리가 빔) |
| `find / -name app-session-info` | 결과 없음 |
| `cisco-.iosp_socket.mount`, `cisco-cisco_cli.mount` | active (소켓은 있으나 세션 없음) |
| `setup-cisco.service` | inactive (dead), `/cisco/lib/setup.sh` 미존재 |
| `iosp_client` 직접 호출 | `/tmp/rp/.iosp_client_dmi` 없음 오류 |

`dohost_sh` 헤더 주석이 원인을 정확히 설명합니다:

> Environment variables `IOSP_*` are set when a 'container-shell-session' is created,
> by calling `shell_exec_*()` API from inside IOS
> (or by executing `guestshell run ...` from terminal, which also invokes that API).

즉 **IOSP 세션은 IOS 측이 만들어야** 합니다. 컨테이너에 ssh 로 들어가거나
systemd 로 서비스를 띄우는 것만으로는 절대 생기지 않습니다.
현재 이 장비는 IOS 측 guestshell CLI 세션이 살아 있지 않은 상태입니다.

---

## 4. 근본 원인 B — 템플릿 설정이 그대로 설치됨
설치된 `/etc/sonar_validator_prober/default.conf`:

```ini
SERVER_IP=172.16.255.245;
SERVER_PORT=3000;
NODE_TYPE=VM;
AGENT_NAME=test-vm
TERMINAL_SHARED_SECRET=;
```

이 값은 저장소의 `SonarValidator_Prober/Installer/default.conf` 템플릿과 **완전히 동일**합니다.
(scp 한 `/home/guestshell/Installer/default.conf` 도 같은 템플릿, 타임스탬프 10-09 12:33)

그 결과:

- `[TERMINAL] disabled: ... must contain at least 32 characters` → **터미널 키 문제의 정체**
  (키가 틀린 게 아니라 **비어 있었습니다**)
- 서버 주소가 랩 서버(`10.20.0.3`)가 아니라 템플릿 기본값 `172.16.255.245`
- `NODE_TYPE=VM`, `AGENT_NAME=test-vm`

> 참고: 제품 분류(`Cisco 8000v`)는 `dohost` 존재 여부로 따로 탐지되므로 `NODE_TYPE` 과 무관합니다.
> 그래서 로그에는 `product=Cisco 8000v` 로 찍혔습니다.

또 서비스 환경에는 IOSP_* 가 없습니다:

```
Environment=SONAR_CONFIG_PATH=... SONAR_TEMPLATE_PATH=... SONAR_DATA_DIR=...
```

systemd 는 IOS 가 만든 세션 환경을 물려받을 수 없으므로, **`app-session-info` 파일이
존재해야만** python `cli` 폴백이 동작합니다.

---

## 5. 근본 원인 C — 에이전트 오판 (코드 수정 완료)
수정 전 `ExecuteIosCli` 는:

```cpp
for (const auto& command : cli_commands)
    output += RunCommandOutput("dohost \"" + command + "\"");   // 실패해도 "Unexpected Error" 누적
```

- `dohost` 실패 텍스트가 그대로 `outputs` 에 들어가 `empty=0` 이 됨
- `BuildStateFromOutputs` 는 "Unexpected Error" 를 파싱하지 못해 `no usable state`
- 호출부의 `!output.empty()` 판정이 **항상 성공으로 오판**

**수정**: `components/terminal/ios_cli.hpp` 를 추가해 판정을 분리했습니다.

- `BuildScript()`: 설정 시퀀스(`configure terminal`, `interface ...`, `no shutdown`)를
  **dohost 한 번**에 `;` 로 결합하고 `end` 를 붙입니다.
  (명령마다 dohost 를 따로 띄우면 첫 프로세스가 끝날 때 설정 모드도 끝나
   뒤 명령이 `% Invalid input` 으로 거부됩니다 — 이게 dohost 의 두 번째 함정입니다.)
- `CheckOutput()`: 아래를 실패로 판정하고 빈 출력을 돌려줍니다.
  - 세션 없음: `Unexpected Error`, `System Error`, `IOSP_SESSION`, `app-session-info`, `No SETUPINPROGRESS Var`
  - 명령 거부: `% Invalid input`, `% Incomplete command`, `% Ambiguous command`, `% Error`
- 세션 없음은 별도 플래그로 표시해 운영자에게 **"guestshell 앱을 재시작하라"** 고 로그로 안내합니다.

`ExecuteIosCli` 는 이제 `ShellQuote` 로 스크립트를 감싸 `dohost` 에 넘깁니다.

---

## 6. 부수 원인 D — SIGTERM 미종료 (수정 완료)

원인은 두 곳이었습니다.

1. **`timed_websocket_operation.hpp` 의 무한 drain**
   ```cpp
   socket.close(ignored);
   context.restart();
   while (!done) context.run_one();   // run_one 은 할 일이 없으면 블록 → 영원히 대기
   ```
   타임아웃 후 완료 핸들러를 기다리는 루프가, 핸들러가 오지 않으면 **무한 블록**했습니다.
   워커 스레드가 살아 있으니 `main` 의 `join` 이 반환되지 않아 SIGTERM 을 무시하는 것처럼 보였고,
   systemd 는 `TimeoutStopSec`(30s) 후 SIGKILL 했습니다.

2. **프로세스 그룹 시그널 실패 시 자식이 남는 경우**

수정:

- 완료 상태를 heap(`shared_ptr`)으로 옮겨 늦게 오는 핸들러가 스택을 건드리지 않게 함
- drain 을 1초로 제한하고, 그래도 안 끝나면 `context.stop()` 후
  `boost::asio::error::timed_out` 반환
- `command_runner.cpp`: 그룹 시그널과 함께 자식 PID 에도 직접 SIGTERM/SIGKILL
- `main.cpp`: 정지 요청 후 **8초 종료 예산 watchdog**. 초과 시 `_Exit(0)`.
  (정적 소멸자 블록까지 회피)
- `prober-cisco.service`: `After=network.target` 제거(컨테이너에서 `systemd-networkd` 를
  끌어오지 않도록) + `TimeoutStopSec=15` (watchdog 8s 보다 여유)

검증(`shutdown-results.json`): refused 1.8s / silent-handshake 4.9s / idle-websocket 0.2s 모두 exit 0.

---

## 7. 복구 절차

### 7.1 ⚠️ 정정 (2026-10-10 실측) — `disable/enable` 로는 해결되지 않습니다

아래 7.1 원안은 **틀렸습니다.** 실측 결과를 먼저 적습니다.

| 시도 | 결과 |
| --- | --- |
| `guestshell disable` → `guestshell enable` | 세션 파일이 **생기지 않음**. systemd 로 띄운 프로버는 여전히 `empty=3` |
| `guestshell run dohost "show version"` (IOS 콘솔) | ✅ 실제 IOS 출력. **세션은 IOS 가 띄운 프로세스에만** 주어짐 |
| `guestshell run env` | `IOSP_SESSION` · `IOSP_TOKEN` · `IOSP_SOCKET` · `IOSP_TIMEOUT=3600` **전달됨** |
| `guestshell run <래퍼>` 로 **프로버 자체를 실행** | ✅ **ARP 10건 · 라우트 9건 · 인터페이스 5건 수집 성공** |

**결론**: `/cisco/cisco_cli/app-session-info` 파일은 이 이미지에서 **누구도 만들지 않습니다.**
따라서 systemd(또는 SSH 로 직접 띄운) 프로세스는 **영원히 dohost 를 쓸 수 없습니다.**
프로버를 **IOS 가 띄운 세션에서 실행**해야 합니다.

```
Router# guestshell run /usr/local/bin/sonar_prober_session.sh
```

- 래퍼: [`sonar_prober_session.sh`](./sonar_prober_session.sh) — SONAR_* 환경변수를 채우고
  **이미 실행 중이면 즉시 종료**(중복 실행 방지)한 뒤 `exec` 로 프로버를 띄웁니다.
  세션은 그 프로세스가 살아 있는 동안 유지되므로 `exec` 여야 합니다.
- systemd 유닛은 **함께 쓰면 안 됩니다**(같은 SQLite/WebSocket 중복). `systemctl disable`.

### 7.2 ⚠️ EEM 자동 시작은 IOS CLI 수집에 부족합니다

재부팅 자동화를 위해 다음 applet 을 넣었습니다.

```
event manager applet SONAR-PROBER
 event timer watchdog time 180
 action 1.0 cli command "enable"
 action 2.0 cli command "guestshell run /usr/local/bin/sonar_prober_session.sh"
```

- **연결·정책·터미널은 정상**입니다. (백엔드 `connected=true`)
- 그러나 이 경로로 뜬 프로세스에는 `IOSP_TOKEN`·`IOSP_SOCKET`·`IOSP_LOG` 는 있어도
  **`IOSP_SESSION` 이 없습니다.** (`/proc/<pid>/environ` 실측)
  `dohost` 는 `IOSP_SESSION` 이 없으면 세션을 못 잡아 `Unexpected Error` →
  수집이 빈손이 됩니다.
- 그래서 **IOS CLI 데이터(라우트·ARP·인터페이스)는 EEM 만으로는 채워지지 않습니다.**
  현재는 IOS CLI 상세가 필요할 때 콘솔에서 `guestshell run <래퍼>` 로 띄웁니다.

### 7.3 원안 (참고용, 위 정정으로 대체됨)

guestshell 앱을 IOS 에서 재시작해야 `app-session-info` 가 다시 생깁니다.
**IOS CLI(콘솔 또는 enable)에서** 실행합니다.

```
Router# guestshell disable
Router# guestshell enable
! 또는
Router# app-hosting stop appid guestshell
Router# app-hosting start appid guestshell
```

확인:

```
Router# guestshell run dohost "show version"     ! IOS CLI 출력이 나와야 정상
```

컨테이너 쪽 확인 지점:

```bash
ls -l /cisco/cisco_cli/app-session-info          # 파일이 있어야 함
env | grep IOSP                                   # IOSP_SESSION 이 보여야 함
```

> 이 랩의 IOS CLI 접근은 SSH(22)가 인증을 거부하므로 **GNS3 콘솔**
> (`192.168.122.1` → 텔넷 `127.0.0.1:5018`)로 `enable` 후 진행합니다.
> `enable` 비밀번호는 저장소에 저장하지 않습니다.

### 7.2 배포 번들 설정 (필수)

`Installer/default.conf` 를 장비 값으로 채운 뒤 배포해야 합니다.
명령마다 `;` 앞까지만 값으로 읽습니다(주석/세미콜론 안전).

```ini
SERVER_IP=10.20.0.3;
SERVER_PORT=3000;
NODE_TYPE=Router;
AGENT_NAME=Cisco-Router;
TERMINAL_SHARED_SECRET=<백엔드와 동일한 32자 이상>;
MANAGEMENT_PREFIX=10.20.0.0/24,192.168.35.0/24,192.168.122.0/24;
```

키 값은 저장소에 기록하지 않습니다. `astra e2e-RVI/artifacts/Cisco/Installer/` 의
준비된 번들을 쓰면 됩니다.

### 7.3 재배포 확인

```bash
scp -P 2222 -r <bundle>/Installer guestshell@10.20.0.1:/home/guestshell
ssh -p 2222 guestshell@10.20.0.1
cd /home/guestshell/Installer && sudo env SONAR_INSTALL_PROFILE=cisco sh Installer.sh
sudo journalctl -u sonar_validator_prober -n 20 --no-pager
```

정상 로그 기대값:

```
[TERMINAL] shared secret loaded (length=64)     ← 새 진단 로그
[CONFIG] agent=Cisco-Router server=10.20.0.3:3000 product=Cisco 8000v
(그리고 [COLLECT] 에 상태가 파싱되어 나옴)
```

세션이 없으면 이제 다음처럼 **원인이 명시**됩니다:

```
[IOS] guestshell↔IOS CLI 세션이 없습니다. dohost 실패: IOSP_SESSION/app-session-info 부재.
      IOS 에서 `guestshell disable` → `guestshell enable` 로 guestshell 앱을 재시작해야 합니다.
```

---

## 8. 코드 수정 목록

| 파일 | 변경 |
| --- | --- |
| `components/terminal/ios_cli.hpp` (신규) | dohost 명령 결합(`BuildScript`) + 오류/세션 판정(`CheckOutput`) |
| `components/terminal/ios_cli_test.cpp` (신규) | 위 로직 회귀 테스트 (실장비 오류 문자열 포함) |
| `module/management_module/management_service.cpp` | `ExecuteIosCli` 를 단일 dohost + 실패 판정으로 교체 |
| `module/management_module/management_service.hpp` | `ExecuteIosCli` 계약 주석 갱신 |
| `components/backend_communication/timed_websocket_operation.hpp` | 무한 drain 제거, 1초 한정 |
| `components/terminal/command_runner.cpp` | 그룹+PID 직접 시그널 |
| `main.cpp` | 종료 예산 watchdog(8s) |
| `workers/TerminalAgentWorker.cpp` | 시크릿 길이 진단 로그(로드된 길이 노출) |
| `components/system_service_registration/systemd/prober-cisco.service` | `network.target` 제거, `TimeoutStopSec=15` |
| `Installer/systemd/sonar_validator_prober-cisco.service` | 위와 동일 (번들 사본) |
| `CMakeLists.txt` | `ios_cli_test` 추가 |

---

## 9. 검증 결과

### 9.1 실장비 재배포 검증 (2026-10-10, 완료)

수정한 정적 Release 바이너리(9,411,792 bytes)를 guestshell 에 배포하고 확인한 결과입니다.
원본: [`cisco-agent-verify.json`](./cisco-agent-verify.json)

| 항목 | 실장비 로그 | 판정 |
| --- | --- | --- |
| SIGTERM 정상 종료 | `[INFO] Clean shutdown complete.` → `Succeeded.` | ✅ **수정 확인** |
| 터미널 키·핸드셰이크 | `[TERMINAL] shared secret loaded (length=64)` → `handshake completed` → `terminal-hello sent` | ✅ **수정 확인** |
| 서버/설정 반영 | `[CONFIG] agent=Cisco-Router server=10.20.0.3:3000 product=Cisco 8000v` | ✅ |
| 관리 채널 | `[INFO] Management worker received policy: {... pol-router-0001 ...}` | ✅ |
| dohost 실패 감지 | `[IOS] guestshell↔IOS CLI 세션이 없습니다 ...` + `empty=3` | ✅ **수정 확인** |

수정 전/후 대비:

- **종료**: 구버전 바이너리는 같은 유닛에서 `deactivating` 상태로 **90초**(`TimeoutStopSec`)를
  멈춰 있었습니다(실측). 신버전은 즉시 종료합니다.
- **수집 오판**: `empty=0`(실패 텍스트를 데이터로 셈) → `empty=3`(정상적으로 빈 출력 처리).

### 9.2 남은 차단 요소

`show ip interface brief` / `show ip route` / `show ip arp` 는 여전히 빈 출력입니다.
**네트워크·설정·코드 문제가 아니라 IOS 측 IOSP 세션이 없기 때문**입니다.
해결은 IOS CLI 에서 한 번만 하면 됩니다.

```
Router# guestshell
[guestshell@guestshell ~]$ exit          ! 세션 생성만으로 app-session-info 가 생김
```

또는 앱 재기동:

```
Router# guestshell disable
Router# guestshell enable
```

> `guestshell` 프롬프트로 들어가는 것만으로 IOS 가 container-shell-session 을 만들고,
> 그때 `/cisco/cisco_cli/app-session-info` 와 `IOSP_*` 가 생성됩니다. 시스템 서비스는
> 이 환경을 물려받지 못하지만, `app-session-info` 파일이 있으면 python `cli` 폴백이
> 그 세션을 씁니다.

이 호스트에서 IOS CLI(22)는 키·비밀번호 인증을 모두 거부하고 콘솔 포트도 로컬에
없어 원격 실행이 불가했습니다. **콘솔(enable)에서 위 명령을 실행**하면 마지막 단계가 끝납니다.

### 9.2.1 guestshell 내부에서 가능한 우회는 모두 실패 (2026-10-10 실장비)

대화형 SSH(`ssh -tt -p 2222 guestshell@10.20.0.1`)로 들어가 다음을 직접 시험했고,
전부 동일하게 실패했습니다(`Unexpected Error`, rc=0).

| 시도 | 결과 |
| --- | --- |
| `dohost "show version"` (따옴표 1인자) | `Unexpected Error` |
| `dohost show version` (인자 2개) | 오류 2줄 |
| PTY 강제: `script -qc 'dohost "show version"' /dev/null` | `Unexpected Error` (TTY 무관) |
| `dohost_sh "show version"` | `IOSP_SESSION environment variable not set` |
| `iosp_client -s /cisco/.iosp_socket -e "show version"` | `unable to read IOSP token from file "/tmp/rp/.iosp_client_dmi"` |
| `env \| grep IOSP` | 비어 있음 |
| `/cisco/cisco_cli/` | 비어 있음 (`app-session-info` 없음) |
| 전 프로세스 `environ` 스캔 | IOSP 세션 가진 프로세스 **0개** |
| 파일시스템 전체에서 `IOSP_SESSION=`/`app-session-info` 검색 | **없음** |
| guestshell → IOS `ssh cisco@10.20.0.1` | `Permission denied` |

`~/.bash_history` 에는 예전에 `dohost show version`,
`dohost show running-config \| include ip nat inside source static` 를 실행한 기록이 남아 있어,
**당시에는 IOSP 세션이 살아 있었음**을 알 수 있습니다. 즉 코드/설정이 아니라 **세션 상태**가 바뀐 것입니다.

결론: **guestshell 내부에서 IOSP 세션을 만드는 방법은 없습니다.** IOS 측이
`container-shell-session` 을 발급해야 하며, 이는 IOS CLI 에서 `guestshell` 진입 또는
앱 재기동으로만 가능합니다.

### 9.2.2 dohost 자체는 정상 — VTY 전용 세션 (2026-10-10 확정)

IOS 에서 `guestshell` 로 들어간 **VTY 셸**에서는 `dohost` 가 정상 동작했습니다.

```
[guestshell@guestshell ~]$ dohost "show version"
Cisco IOS XE Software, Version 17.15.04
... (정상 IOS 출력)
[guestshell@guestshell ~]$ exit
Router#
```

같은 시각, 별도 SSH(`-p 2222`) 셸에서는 실패했습니다.

| 실행 위치 | `dohost "show version"` |
| --- | --- |
| IOS `guestshell` VTY 셸 | ✅ 정상 (IOS 출력) |
| SSH `guestshell@host -p 2222` | ❌ `Unexpected Error` |

`/cisco/cisco_cli/` 는 SSH 셸 기준으로 **여전히 비어 있었습니다**(`app-session-info` 없음).
즉 `IOSP_SESSION`/`IOSP_TOKEN` 은 **IOS 가 VTY 를 열 때 그 셸에만 주입**되며,
SSH 세션과 systemd 서비스에는 상속되지 않습니다.

정리하면 **`dohost` 에는 버그가 없습니다.** `dohost`/`dohost.py`/`cli.clip()`/`iosp_client`
모두 정상이고, 세션(토큰)이 없을 때 실패할 뿐입니다.

에이전트(systemd)가 계속 실패하는 이유는 서비스가 VTY 세션 환경을 물려받지 못하고
폴백 파일(`/cisco/cisco_cli/app-session-info`)도 없기 때문입니다.
이 파일이 생기면 `cli.clip()` 의 `check_and_set_iosp_env()` 가 `IOSP_*` 를 채워
systemd 에서도 동작합니다.

### 9.3 산출물

| 파일 | 내용 |
| --- | --- |
| [`cisco-dohost-probe.json`](./cisco-dohost-probe.json) | 실장비 dohost/IOSP 진단 원본 |
| [`cisco-reachability.json`](./cisco-reachability.json) | 10.20.0.3 도달성 정정 근거 |
| [`cisco-agent-verify.json`](./cisco-agent-verify.json) | 재배포 후 실장비 검증 로그 |
| [`prober-tests.txt`](./prober-tests.txt) | Prober ctest 22개 전부 통과 |
| [`shutdown-results.json`](./shutdown-results.json) | SIGTERM 종료 시간 3 시나리오 |
| `deploy_cisco_agent.sh` | Cisco 재배포 스크립트(키는 파일에서만 읽음) |
| `run_ios_cmd.py` | IOS CLI 진단 도우미(비밀번호는 `~/.sonar_cisco_pw` 또는 환경변수) |

