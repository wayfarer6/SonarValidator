# Lab 1 Prober SSH 배포

대상은 기존 Lab1 프로젝트 **PRJ-86F69C4D**입니다. DB를 초기화하거나 새 프로젝트를 만들지 않았습니다.
이번 작업은 Prober 배포·수집과 UI 검토입니다. 정책 푸시·격리 실험은 실행하지 않습니다.

## 주소와 접근

| 대상 | 관리 IP | 데이터·인터넷 | Guest Shell SSH |
| --- | --- | --- | --- |
| 관리 서버 WSL | 192.168.148.180 | 기본 경로는 WSL eth0 | 기존 id_ed25519 키 |
| R1 | Gi1 192.168.148.181/20 | Gi2 10.10.20.1/24, Gi3 192.168.42.254/24 → 192.168.42.1 | guestshell@192.168.148.181:2222 |
| R2 | Gi1 192.168.148.182/20 | Gi2 10.10.20.2/24 → R1 | guestshell@192.168.148.182:2222 |
| 두 Guest Shell | 내부 192.168.100.2/24 | 각 VPG0 192.168.100.1 | 각 라우터 NAT로 구분 |

관리 대역은 실제 /20의 네트워크 주소인 192.168.144.0/20입니다.
SSH 키는 `/home/ssh1032007/.ssh/id_ed25519`를 사용합니다. 개인 키와 공유 시크릿 원문은 보고서에 넣지 않습니다.
UI는 `http://localhost:5173`, Backend는 `http://localhost:3000`입니다. 이번에는 기존 admin 계정으로 접속했습니다.

```bash
ssh -o BatchMode=yes -p 2222 guestshell@192.168.148.181
ssh -o BatchMode=yes -p 2222 guestshell@192.168.148.182
```

## 배포 순서와 이유

1. 기존 DB와 프로젝트를 확인합니다. 이 환경은 Backend/data의 H2 local 프로필입니다.
2. 현재 소스를 정적으로 빌드합니다. 기존 Installer 실행 파일에는 현재 Cisco EEM 캐시 기능이 없었습니다.
3. 실행 파일·설치 자산을 관리 서버의 스테이징 디렉터리에 준비합니다.
4. Lab1-R1과 Lab1-R2를 배포 예정 Agent로 등록합니다. expected_ip는 .181과 .182입니다.
5. 같은 이름으로 서버 생성 번들을 받습니다. server_ip는 **.180**, server_port는 **3000**, node_type은 Router입니다.
6. SSH 표준입력으로 tar.gz를 전달해 각 `/home/guestshell/sonar/Installer`에 풉니다. 비밀번호 입력은 필요하지 않습니다.
7. 번들의 MANAGEMENT_PREFIX=192.168.144.0/20을 확인합니다. 구형 번들에 없으면 보완합니다.
8. 홈 경로의 시작 스크립트와 IOS EEM을 설정해 조회 결과를 수집하고 Prober를 실행합니다.
9. connected 표시뿐 아니라 실제 IOS 인터페이스와 라우트가 반복 수집되는지 확인합니다.

서버가 넣은 AGENT_NAME을 바꾸면 등록된 장비가 계속 silent로 남고 다른 이름이 추가될 수 있습니다.

## 실제 설치 경로

| 경로 | 용도 |
| --- | --- |
| ~/sonar/Installer/sonar_validator_prober | 정적 실행 파일 |
| ~/sonar/Installer/default.conf | 서버 주소·Agent 이름·공유 인증 값, 권한 600 |
| ~/sonar/Installer/default_template.sqlite | SQLite 템플릿 |
| ~/sonar/data | Prober DB·실행 데이터 |
| ~/sonar/data/ios_cli | IOS 조회 결과 캐시 |
| ~/sonar/start.sh | 캐시 복사·중복 실행 방지·실행 유지 |
| ~/sonar/prober.log | 실행 로그, 10 MiB를 넘으면 다음 시작 때 한 세대 보관 |
| /bootflash/guest-share/sonar_show_*.out | IOS와 Guest Shell의 공유 조회 파일 |

R1의 /etc/shadow가 없어 sudo의 PAM account 검사가 실패했습니다. 비밀번호 인증을 느슨하게 바꾸지 않고 영속 홈 경로에 설치했습니다.
R2에서는 sudo가 정상입니다. 두 장비의 관리 방법을 일치시키려고 같은 홈 배포 형태를 사용합니다.

## EEM 수집

SSH나 오래 실행된 프로세스의 dohost는 IOSP 세션이 없어 Unexpected Error를 낼 수 있습니다.
현재 소스는 세 조회 명령의 300초 이내 EEM 스냅샷을 우선 읽습니다. 파일이 없거나 유효하지 않을 때 dohost 호환 경로를 사용합니다. 설정 명령을 캐시로 성공 처리하지 않습니다.
IOS는 show ip interface brief, show ip route, show ip arp 결과를 bootflash:guest-share에 저장합니다.
Guest Shell에서는 같은 공유 위치가 /bootflash/guest-share로 보입니다. IOS bootflash의 다른 경로가 모두 공유되는 것은 아닙니다.
같은 파일을 다시 쓸 때 confirm 프롬프트를 처리해야 EEM이 중간에서 멈추지 않습니다.

[시작 스크립트](/scripts/lab1-prober-start.sh)를 참조합니다. Prober는 파일 잠금으로 중복 실행을 막고 숫자 관리 IP로 서버에 연결합니다.
현재 바이너리·CMake 조건은 [빌드 안내](../development/prober-build-debug.md)에 있습니다.

## 서버 수동 시작

환경은 `/home/ssh1032007/.config/sonarvalidator/lab1.env`에 저장되어 있습니다. 암호화 키와 터미널 공유 값을 재생성하지 않습니다.
스테이징은 `/home/ssh1032007/.local/share/sonarvalidator/stage`입니다.
Backend는 SonarValidator_Backend를 작업 디렉터리로, 기존 Maven Wrapper의 spring-boot:run local 프로필로 실행합니다.
환경 파일을 systemd EnvironmentFile로 주입하고 힙은 512 MiB로 제한했습니다.
Frontend는 npm run build 결과를 Vite preview의 5173 포트로 제공합니다. 현재 transient unit은 sonar-lab1-backend와 sonar-lab1-preview입니다. 앱의 부팅 자동 시작은 설정하지 않았습니다.

프로젝트 자동 집행 환경 값은 false입니다. 실제 받은 정책은 policies=[]와 source=external-enforcement로 확인했습니다.
IOS의 connected route로 /24·/20 대역을 확인하고, 자동 CSO는 미분류로 둡니다. [UI 검토](../frontend/lab1-review.md)에 수정 결과를 기록했습니다. 역할·보안 등급을 검토해 저장한 뒤 별도 정책 실험을 진행하세요.

## 검증 범위

실제 실행 결과는 아래 배포 검증 기록에 추가합니다. 과거 전체 통합 PASS와 이번 결과를 합치지 않습니다.
Cisco EEM CLI 세션 설정은 [공식 명령 안내](https://www.cisco.com/c/en/us/td/docs/ios-xml/ios/eem/command/eem-cr-book/eem-cr-e1.html)를 참조합니다.

## Prober용 EEM 추가 설정

기존 Gi1 관리망·Gi2 전송망·Gi3 NAT 설정 위에 추가하는 설정입니다.
실행 계정은 EEM CLI 세션의 문맥이며 새 IOS 로그인 계정을 만들지 않습니다.
시작 스크립트를 먼저 설치하고 적용합니다. timer applet은 event manager run으로 수동 실행하지 않고 다음 60초 주기를 기다립니다.

```text
configure terminal
event manager session cli username guestshell privilege 15
no event manager applet SONAR-Lab1-Collect
event manager applet SONAR-Lab1-Collect authorization bypass
 event timer watchdog time 60 maxrun 55
 action 010 cli command "enable"
 action 015 cli command "terminal length 0"
 action 020 cli command "show ip interface brief | redirect bootflash:guest-share/sonar_show_ip_interface_brief.out" pattern "confirm|#"
 action 021 cli command ""
 action 030 cli command "show ip route | redirect bootflash:guest-share/sonar_show_ip_route.out" pattern "confirm|#"
 action 031 cli command ""
 action 040 cli command "show ip arp | redirect bootflash:guest-share/sonar_show_ip_arp.out" pattern "confirm|#"
 action 041 cli command ""
 action 050 cli command "guestshell run /bin/bash /home/guestshell/sonar/start.sh"
end
write memory
```

셸의 setsid와 시작 후 2초 대기는 IOS 명령 세션이 닫히기 전에 자식 프로세스가 분리되도록 합니다.
R1에서 실제 Prober에 SIGTERM을 보내 정상 종료한 뒤 다음 EEM 주기에 새 PID로 다시 연결되는 것을 확인했습니다.
SSH로 수동 시작하려면 `/bin/bash /home/guestshell/sonar/start.sh`를 실행합니다.
기존 프로세스가 있으면 파일 잠금 때문에 중복 실행하지 않습니다.

## 관리 서버 시작 명령

WSL에서 실행합니다. 이미 실행 중인 경우 같은 이름의 unit을 중복 생성하지 않습니다.

```bash
repo=/home/ssh1032007/SonarValidator
sudo systemd-run --unit=sonar-lab1-backend --collect \
  --uid=ssh1032007 --working-directory="$repo/SonarValidator_Backend" \
  --property=EnvironmentFile=/home/ssh1032007/.config/sonarvalidator/lab1.env \
  -- "$repo/SonarValidator_Backend/mvnw" -q spring-boot:run \
  -Dspring-boot.run.profiles=local \
  '-Dspring-boot.run.jvmArguments=-Xmx512m -Ddebug=false'
nodebin=/home/ssh1032007/.nvm/versions/node/v26.13.0/bin
sudo systemd-run --unit=sonar-lab1-preview --collect \
  --uid=ssh1032007 --working-directory="$repo/SonarValidator_Frontend" \
  --setenv="PATH=$nodebin:/usr/local/bin:/usr/bin:/bin" \
  -- "$nodebin/node" node_modules/vite/bin/vite.js preview --host 0.0.0.0 --port 5173 --strictPort
```

종료: `sudo systemctl stop sonar-lab1-preview sonar-lab1-backend`.
상태: `systemctl status sonar-lab1-backend sonar-lab1-preview --no-pager`.

파일 검증·캐시 우선순위·감시와 적용의 구분은 [Cisco EEM 감시 경로](cisco-eem.md)를 기준으로 합니다.
