# RVI Agent 배포 및 연결 복구

대상은 **Cisco Router(10.20.0.1), Arista Switch(10.20.0.4), OPNsense(10.20.0.2)** 입니다.
Alpine/FRR 기반 PoC 네트워크와 별개입니다. 검증 스크립트와 결과는 저장소 루트의
`astraa e2e-RVI/`에 보관합니다. OPNsense에는 Linux Agent를 설치하지 않습니다.

## 1. 확인된 문제와 수정

| 증상 | 확인 및 수정 |
| --- | --- |
| Cisco 서비스 시작 실패 | `Requires=systemd-networkd.service` 제거. Cisco 전용 유닛은 `guestshell` 사용자와 HOME/PATH 지정. 컨테이너 네트워크 서비스 시작을 요구하지 않음 |
| 설정의 터미널 키가 인증 실패 | `값 ; // 주석`에서 공백·세미콜론·주석을 제거. 환경변수 `SONAR_TERMINAL_SHARED_SECRET`가 비어 있지 않으면 우선 사용 |
| 서버 주소를 수정해도 연결 안 됨 | `settings.conf`의 옛 주소 대신 `default.conf`의 서버 주소·포트·장비 유형·이름을 재시작마다 반영 |
| SIGTERM을 보내도 종료 안 됨 | WebSocket handshake/write에 비동기 deadline 적용. 수집 명령 프로세스 그룹 중단, 워커 종료 후 DB 큐 배출, join 이후 완료 로그 |
| 다운로드 번들에 서비스 누락 | 일반/Cisco systemd 및 OpenRC 파일을 번들과 Docker 스테이징에 포함 |
| OPNsense NAT 경로 404 | RVI에서 `/api/firewall/filter/get` 우선. `/search_nat`는 구버전 404 폴백으로만 사용 |
| OPNsense HTTP 주소가 코드에서 거부됨 | 서버의 `SONAR_OPNSENSE_HTTP_ORIGINS`에 명시한 origin만 예외 허용. 기본 HTTPS 동작과 redirect 금지는 유지 |
| Cisco `dohost` 가 항상 `Unexpected Error` | guestshell↔IOS CLI 세션(IOSP) 부재. IOS 에서 `guestshell disable` → `guestshell enable` 로 세션 재생성. 상세: [`astra e2e-RVI/cisco-dohost-diagnosis.md`](../../../../astra%20e2e-RVI/cisco-dohost-diagnosis.md) |
| 설치된 `default.conf` 가 템플릿 그대로 | 번들의 `default.conf` 를 장비 값으로 채우지 않으면 서버 IP·키가 빈 템플릿으로 설치됨(터미널 비활성). 배포 전 반드시 확인 |
| `dohost` 실패를 텔레메트리로 오인 | `dohost` 는 실패해도 종료코드 0. `ios_cli` 가 오류 문자열을 판정해 빈 출력을 반환하도록 수정 |

`[COLLECT] no usable state ... commands=3, empty=0`은 명령 출력이 있었지만
파싱할 상태가 없었다는 뜻입니다. 서버 연결 성공 여부와 별도로, **서비스 사용자로**
`dohost` 출력이 IOS 상태인지 오류 메시지인지 확인해야 합니다.

> ⚠️ **Cisco 는 guestshell SSH(2222)만으로는 `dohost` 가 동작하지 않습니다.**
> IOSP 세션은 IOS 가 `guestshell run ...` 으로 만들 때만 생기며, systemd 서비스는
> 그 환경을 물려받지 못합니다. `/cisco/cisco_cli/app-session-info` 가 있어야
> python `cli` 폴백이 동작합니다. 없으면 `dohost` 는 `Unexpected Error`(종료코드 0)만 냅니다.

## 2. 빌드 및 번들 준비

개발 호스트의 동적 바이너리를 CentOS 기반 guestshell/EOS에 그대로 복사하지 마세요.
정적 링크 빌드 예:

```bash
cmake -S SonarValidator_Prober -B SonarValidator_Prober/build-rvi \
  -DCMAKE_BUILD_TYPE=Release -DSONAR_STATIC_LINK=ON \
  -DCMAKE_EXE_LINKER_FLAGS=-static \
  -DANTLR4_RUNTIME_ROOT="$HOME/tools/antlr4-install"
cmake --build SonarValidator_Prober/build-rvi --target installer_bundle -j2
file SonarValidator_Prober/Installer/sonar_validator_prober
```

`installer_bundle`은 Installer 아래 서비스 파일을 원본으로부터 다시 생성합니다.
이번 작업에서는 기존 로컬 삭제 파일을 복원하지 않도록 별도의 완전한 번들도
`astraa e2e-RVI/artifacts/{Arista,Cisco}/Installer/`에 준비합니다. 재빌드할 때 기존 배포 설정은 보존하세요.

## 3. 서버와 키 설정

먼저 **실제 백엔드가 포트 3000에서 listen 중인지** 확인합니다.

```bash
ss -ltn '( sport = :3000 )'
```

공통 키는 백엔드 `SONAR_TERMINAL_SHARED_SECRET`와 각 Agent의
`TERMINAL_SHARED_SECRET`에 동일하게 넣습니다. 키는 32자 이상이며 저장소에 기록하지 않습니다.
Agent의 환경변수가 설정되어 있으면 파일 값보다 우선합니다.
`default.conf`는 셸 스크립트가 아니므로 `source`로 실행하지 않습니다.

Arista의 `default.conf`:

```ini
SERVER_IP=10.20.0.3;
SERVER_PORT=3000;
NODE_TYPE=Switch;
AGENT_NAME=Arista-Switch;
TERMINAL_SHARED_SECRET="<백엔드와 동일한 실제 키>";
MANAGEMENT_PREFIX=10.20.0.0/24;
```

Cisco도 **다른 Agent와 같이 관리망 서버 주소 `10.20.0.3`를 씁니다.**
(2026-10-10 실측 정정: 과거 "`10.20.0.3` 실패" 기록은 일시적 현상이었습니다.
guestshell에서 `10.20.0.3`으로 ping·TCP·WebSocket 101이 모두 정상이었고,
`192.168.122.32`는 폴백입니다.)

```bash
# guestshell 내부에서 실행. 둘 다 exit code 0 이어야 정상.
timeout 5 bash -c 'echo > /dev/tcp/10.20.0.3/3000'
timeout 5 bash -c 'echo > /dev/tcp/192.168.122.32/3000'

# 에이전트와 같은 WebSocket 업그레이드까지 확인 (101 이면 정상)
curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'Connection: Upgrade' -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
  http://10.20.0.3:3000/api/v1/terminal/agent
```

```ini
SERVER_IP=10.20.0.3;
SERVER_PORT=3000;
NODE_TYPE=Router;
AGENT_NAME=Cisco-Router;
TERMINAL_SHARED_SECRET="<백엔드와 동일한 실제 키>";
MANAGEMENT_PREFIX=10.20.0.0/24,192.168.35.0/24,192.168.122.0/24;
```

`192.168.122.32`는 관리망 주소가 죽었을 때의 폴백입니다. 어느 쪽이든
캐시 삭제나 DB 초기화는 필요 없습니다.
설정은 설치 대상 `/etc/sonar_validator_prober/default.conf`에 복사되어야 적용됩니다.

### 이번 로컬 환경의 백엔드 설정

`SonarValidator_Backend/config/application-local.properties`에 사용자 제공 장비 키와
동일한 키를 넣었습니다. 파일은 git 제외 대상이며 권한은 0600입니다.
`SonarValidator_Backend` 디렉터리에서 백엔드를 실행하면 기본 `local` 프로필이 읽습니다.
이 파일은 빌드 JAR에 포함되지 않습니다. 다른 호스트나 Docker에서는 환경변수로 주입합니다.

```bash
cd SonarValidator_Backend
./mvnw spring-boot:run
```

환경변수 `SONAR_TERMINAL_SHARED_SECRET`가 있으면 로컬 파일의 기본값보다 우선합니다.
RVI HTTP 허용 주소도 이 외부 설정에 지정했습니다.
`Installer/default.conf` 원본은 키가 빈 템플릿입니다. 그대로 배포하는 대신 서버에서
생성한 번들 또는 이번에 준비한 `astraa e2e-RVI/artifacts/Arista/Installer/`를 사용합니다.
준비된 번들은 백엔드와 같은 키를 담으며 git 제외 대상입니다.

## 4. Arista 설치

```bash
scp -r SonarValidator_Prober/Installer admin@10.20.0.4:/home/admin
# Arista의 bash에서 default.conf를 위 장비 설정으로 편집 후:
cd /home/admin/Installer
sudo sh Installer.sh
sudo systemctl status sonar_validator_prober --no-pager
sudo journalctl -u sonar_validator_prober -n 60 --no-pager
```

SFTP를 지원하지 않는 EOS에서는 `scp -O -r ...`로 전송합니다.
Arista에서는 일반 서비스 프로필을 사용하며 `FastCli` 권한을 위해 root로 실행합니다.

## 5. Cisco guestshell 설치

```bash
scp -P 2222 -r SonarValidator_Prober/Installer guestshell@10.20.0.1:/home/guestshell
# guestshell에서 default.conf를 위 장비 설정으로 편집 후:
cd /home/guestshell/Installer
sudo env SONAR_INSTALL_PROFILE=cisco sh Installer.sh
sudo systemctl show sonar_validator_prober -p User -p Requires -p After -p ActiveState
sudo journalctl -u sonar_validator_prober -n 60 --no-pager
sudo -u guestshell /usr/bin/dohost 'show ip interface brief'
sudo -u guestshell /usr/bin/dohost 'show ip route'
sudo -u guestshell /usr/bin/dohost 'show ip arp'
```

`dohost`의 위치가 다르면 `command -v dohost`로 확인합니다.
유닛은 `User=guestshell`이며 기존 root 소유 데이터 디렉터리를 재사용할 수 있게
설치기가 소유권을 변경합니다. 일반 설치에서는 `dohost`와 guestshell 계정으로 자동 감지하며,
명시적인 `SONAR_INSTALL_PROFILE=cisco`도 지원합니다.
`systemd-networkd`를 활성화하거나 네트워크 설정을 재작성하지 않습니다.

**설치 직후 `dohost` 세션을 반드시 확인합니다.** 위 세 명령이 IOS 상태를 내놓아야 정상이고,
`Unexpected Error` 가 나오면 IOS 측 guestshell 세션이 없는 것입니다.
IOS CLI(콘솔 → `enable`)에서 다음을 실행해 세션을 재생성합니다.

```
Router# guestshell disable
Router# guestshell enable
```

재기동 후 확인:

```bash
ls -l /cisco/cisco_cli/app-session-info     # 존재해야 함
env | grep IOSP                              # IOSP_SESSION 이 보여야 함
sudo -u guestshell /usr/bin/dohost 'show version'
```

재설치는 기존 서비스를 멈춘 후 바이너리·설정을 교체하고 재시작합니다.
과거 바이너리의 종료 결함이 있는 경우 첫 교체는 기존 systemd의 stop timeout까지 걸릴 수 있습니다.

## 6. OPNsense API 연결

RVI 장비는 `http://10.20.0.2`로 접근합니다. 백엔드 시작 환경 또는 Docker `.env`에:

```dotenv
SONAR_OPNSENSE_HTTP_ORIGINS=http://10.20.0.2
```

HTTP 예외는 주소·포트 단위로 한정됩니다. 이 값은 API 키가 아닙니다.
애플리케이션의 OPNsense 설정에는 `http://10.20.0.2`, API Key, API Secret을 입력합니다.
터미널 공유 키와 OPNsense API 자격증명은 서로 다른 값입니다.
`SONAR_SECRET_KEY`는 저장된 API Secret을 복호화할 수 있도록 백엔드 재시작 간 동일하게 유지합니다.

| 대상 | 호출 경로 |
| --- | --- |
| 연결/버전 | `/api/core/firmware/status`, 404일 때 `/api/core/firmware/info` 후보 |
| 인터페이스 | `/api/interfaces/overview/interfacesInfo` |
| 필터 | `/api/firewall/filter/search_rule?rowCount=-1` |
| NAT | `/api/firewall/filter/get` → `filter.snatrules`, `npt`, `onetoone` |
| 별칭 | `/api/firewall/alias/search_item?rowCount=-1` |

`curl -k`는 HTTP URL을 HTTPS로 바꾸지 않습니다. 302 로그인 페이지나 HTML 200은
정상 API 응답이 아닙니다. API 키 인증을 포함해 JSON 상태를 확인합니다.

## 7. 완료 판정

- 서버 listen → 장비 내부에서 서버 TCP 연결 → 서비스 실행 → management 등록 → telemetry 수신 → 터미널 인증 순서로 확인합니다.
- 텔레메트리는 기본 30초 간격이므로 60초 이상 관찰합니다.
- `[CONFIG]`에서 실제 서버 주소·제품명을 확인합니다. 키 값은 출력하지 않습니다.
- 종료 검증: `time sudo systemctl stop sonar_validator_prober`; 프로세스 소멸 및 완료 로그를 확인한 후 다시 start합니다.
- 실장비 접근 실패와 로컬 회귀 테스트 통과는 별개로 기록합니다. 최신 결과는 `astraa e2e-RVI/README.md`를 확인합니다.

참고: [기존 Cisco 네트워크 진단](../../../blog/2026-09-26-cisco-guestshell-troubleshooting.md),
[Boost.Beast timeout 설명](https://www.boost.org/doc/libs/latest/libs/beast/doc/html/beast/using_io/timeouts.html).
