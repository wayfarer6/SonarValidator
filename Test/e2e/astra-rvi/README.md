# RVI 수정·검증 기록

대상: Cisco Router `10.20.0.1`, Arista Switch `10.20.0.4`, OPNsense `10.20.0.2`.
Alpine/FRR PoC 결과와 분리합니다. 날짜: 2026-10-10 (Asia/Seoul).

## 현재 상태

- Arista: 사용자가 해결했다고 확인. 추가 원격 변경 없음.
- Cisco: **2026-10-10 guestshell(ssh 2222)에 실제 접속해 `dohost` 를 검증**했다.
  `dohost`/`dohost_sh`/`cli`/`clip` 모두 실패(`Unexpected Error`, 종료코드 0)했고,
  원인은 guestshell↔IOS CLI 세션(IOSP) 부재였다.
  이어서 수정한 정적 Release 바이너리를 재배포해 **SIGTERM 정상 종료, 터미널 키·핸드셰이크,
  관리 채널 정책 수신, dohost 실패 감지**를 실장비에서 확인했다(`cisco-agent-verify.json`).
  남은 한 단계는 IOS CLI 콘솔에서 `guestshell` 진입/재기동으로 IOSP 세션을 만드는 것이다.
  IOS CLI(포트 22)는 키·비밀번호 인증을 모두 거부해 원격 실행이 불가하다.
- 백엔드: 초기 조사 당시 TCP 3000 리스너가 없었음. 서버를 기동하거나 운영 데이터를 변경하지 않음.
- OPNsense: 수정된 백엔드 API 클라이언트로 실장비 진단 **5/5 성공**.
- 터미널 키: 사용자 제공 장비 키와 디버그 Agent 키가 같았으나 백엔드 환경변수는 없었음.
  외부 `SonarValidator_Backend/config/application-local.properties`에 같은 키를 설정하고
  **실제 Spring 설정 로더로 64자·일치 여부를 확인**함. 키 값은 결과에 저장하지 않음.
- 소스의 `Installer/default.conf`는 키가 빈 템플릿. 서버 생성 번들 또는 아래 준비된 번들을 사용.

## 결과 파일

| 파일 | 내용 |
| --- | --- |
| `terminal-config-check.json` | 백엔드 유효 설정과 준비된 Agent 설정의 키 길이·일치 여부 (원격 파일 직접 조회는 아님) |
| `opnsense-endpoints.json` | 기존 엔드포인트 직접 비교: search_nat 404, filter/get 200, firmware/info 시간 초과 |
| `opnsense-backend-probe.json` | 수정된 API 클라이언트의 firmware/interfaces/rules/nat/aliases 실장비 결과 |
| `backend-tests.txt` | 백엔드 번들·OPNsense·터미널 인증 회귀 테스트 결과 |
| `prober-tests.txt` | C++ 설정·네트워크·명령 중단·PTY 관련 7개 테스트 |
| `shutdown-results.json` | 실제 정적 Agent 프로세스의 SIGTERM 종료 시간: 연결 거부/무응답 handshake/유휴 WebSocket |
| `installer-build.json` | 정적 바이너리 SHA-256과 준비된 번들 위치 |
| `cisco-dohost-probe.json` | 실장비(10.20.0.1) `dohost`/IOSP 세션 진단 원본 |
| `cisco-reachability.json` | `10.20.0.3` 도달성 검증 (과거 "실패" 기록 정정) |
| `cisco-agent-verify.json` | 수정 바이너리 재배포 후 실장비 검증 로그 |
| `cisco-dohost-diagnosis.md` | Cisco 명령 처리 실패 원인 분석·복구 절차·코드 수정 내역 |
| `deploy_cisco_agent.sh` | Cisco 재배포 스크립트 (키는 파일에서만 읽음) |
| `run_ios_cmd.py` | IOS CLI 진단 도우미 (`~/.sonar_cisco_pw` 또는 `SONAR_CISCO_PW`) |

`/api/core/firmware/status`는 실장비에서 정상 JSON을 반환했고 `/info`는 20초에도
응답하지 않았습니다. 따라서 status 우선, 404일 때 info 폴백입니다.
NAT는 정상 응답한 `/api/firewall/filter/get`을 우선하며,
404인 구버전에서만 `/search_nat`를 시도합니다.
HTTP 허용은 명시한 origin에만 적용하고, 인증 없는 접근·다른 HTTP 주소·redirect 허용으로
확장하지 않습니다.

## 준비된 번들

- `artifacts/Arista/Installer/`: 서버 `10.20.0.3:3000`, 이름 `Arista-Switch`.
- `artifacts/Cisco/Installer/`: 과거 RVI guestshell 경로인 `192.168.122.32:3000`, 이름 `Cisco-Router`.
  현재 Cisco TCP 도달성은 재검증하지 않았으므로 배포 재개 시 확인해야 합니다.
- 양쪽 `default.conf` 키는 로컬 백엔드 키와 일치. 이 디렉터리는 git에서 제외합니다.
- 기존 Installer 아래 사용자의 삭제 파일들은 그대로 유지했습니다.

재생성:

```bash
python3 'astraa e2e-RVI/stage_installer.py' \
  --binary SonarValidator_Prober/build-debug/sonar_validator_prober \
  --secret-config .vscode/debug-default.conf
```

이번 `build-debug`의 실제 빌드 구성은 **Release + static**입니다.
깨끗한 새 디렉터리에서 재빌드하려면 [배포 가이드](../docs/docs/PoC%20Network%20검증/Poc용%20네트워크%20Real-to-Virtual/RVI-배포-복구-가이드.md)의 CMake 명령을 사용합니다.

## 재현

```bash
python3 'astraa e2e-RVI/test_shutdown.py' \
  SonarValidator_Prober/build-debug/sonar_validator_prober \
  SonarValidator_Prober/Installer/default_template.sqlite
```

Java 진단은 백엔드 클래스와 Maven 의존성 classpath를 사용합니다.

```bash
cd SonarValidator_Backend
./mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/sonar-rvi-classpath
java -cp "target/classes:$(cat /tmp/sonar-rvi-classpath)" \
  '../astraa e2e-RVI/CheckTerminalConfig.java' \
  '../astraa e2e-RVI/artifacts/Arista/Installer/default.conf' \
  '../astraa e2e-RVI/terminal-config-check.json'
# SONAR_RVI_OPNSENSE_KEY / SONAR_RVI_OPNSENSE_SECRET를 현재 셸에 주입한 뒤:
java -cp "target/classes:$(cat /tmp/sonar-rvi-classpath)" \
  '../astraa e2e-RVI/OpnsenseProbe.java' '../astraa e2e-RVI/opnsense-backend-probe.json'
```

실제 프로젝트 등록과 브라우저→Agent 터미널 세션까지의 E2E는 실행하지 않았습니다.
키 인증 회귀 테스트와 로컬 설정 일치 확인이 원격 PTY 검증을 대신하지는 않습니다.
