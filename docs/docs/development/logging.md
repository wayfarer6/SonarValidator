# 작업 중심 로그

기본 출력은 날짜·시각·시간대, 등급, 짧은 작업 메시지입니다. 인증 진단은 비밀값 대신 결과를 기록하고, 평상시 정책 JSON·SQL·HTTP 요청/응답 본문을 계속 나열하던 경로를 줄였습니다.

## Prober

- 시작/종료, 관리 서버 연결, 내용이 바뀐 정책 처리, 수집 주기 변경, 터미널 열기/닫기, 장비 명령 결과는 INFO입니다.
- 수집·전송 건수 요약은 최초 한 번과 이후 5분 이상 지난 수집 시점에 표시합니다. 수집 주기 자체는 그대로 유지합니다.
- 동일한 위치에서 동일한 경고가 반복되면 1분에 한 번 출력합니다. 계속 반복될 경우 생략한 횟수를 다음 경고에 붙입니다. 다른 원인의 오류와 복구는 바로 표시합니다.
- 도구 설치 여부 같은 boolean 조회의 음성 결과는 DEBUG입니다. 상위 정책 처리 실패와 명령 timeout은 경고로 유지합니다.
- 일반 메시지 본문은 줄임표를 제외하고 320 byte, DEBUG는 1024 byte로 제한하고 줄바꿈·제어 문자를 정리합니다. UTF-8 문자를 중간에서 끊지 않습니다.
- 로그 출력은 기존 worker에서 짧은 mutex 구간으로 처리합니다. 로깅용 스레드나 DB 스레드를 추가하지 않습니다.
- 장비의 시간대 offset이 시각 뒤에 붙습니다. UTC인 장비는 `+0000`, 한국 시간인 호스트는 `+0900`입니다.

| 장비 | 로그 위치 |
| --- | --- |
| Cisco GuestShell | `/home/guestshell/sonar/prober.log` |
| Arista EOS | `/mnt/flash/sonar/prober.log` |
| Ubuntu/FRR | `/etc/sonar-validator/prober.log` |
| Open vSwitch | `/etc/switch/sonar/prober.log` |

SSH로 로그인한 뒤 `tail -n 20 <로그 경로>` 또는 `tail -f <로그 경로>`로 확인합니다. 기존 로그 이력은 보존하므로 갱신 전 내용이 앞에 남아 있을 수 있습니다. 새 `Prober 시작` 행 이후가 새 형식입니다. EOS의 root 소유 로그는 admin 계정에서 `sudo tail`로 읽습니다.

## Backend

프로젝트 생성·저장·삭제, 장비 등록/연결, 수집 주기 저장, 터미널 작업, 정책 전달, AI 명령 결과 등을 짧게 기록합니다. 같은 관리 세션의 반복 등록·정책 조회/생성·정상 파싱은 DEBUG로 내렸습니다. 재연결 시 이전 소켓이 닫히는 사건을 새 연결 종료처럼 표시하지 않습니다.

전역 `debug=false`, 프레임워크 기본 WARN, 애플리케이션 기본 INFO입니다. Hibernate SQL·바인딩/추출·JDBC query logger와 `show_sql`은 끕니다. SQL 포맷팅과 Spring 배너도 비활성화했습니다. 한 메시지는 최대 400자로 제한하며 예외는 원인 첫 줄을 메시지와 합칩니다. 기본 로그에 전체 stack trace를 붙이지 않습니다. 시작 시 실제 경고·오류와 JVM/실행기 자체 메시지는 별도로 보일 수 있습니다.

WSL의 현재 Backend 서비스는 다음으로 확인합니다.

```sh
sudo journalctl -u sonar-lab1-backend -f -o cat
```

## 진단 모드와 검증

추가 진단이 필요할 때 시작 환경에 `SONAR_LOG_LEVEL=DEBUG`를 넣고 해당 프로세스를 재시작합니다. 실행 중인 프로세스에 환경변수만 설정해서는 반영되지 않습니다. Backend 애플리케이션 DEBUG는 전역 Spring debug나 SQL 출력을 켜지 않습니다. Prober DEBUG에는 짧게 제한된 장비 조회 결과도 포함됩니다.

Prober의 `components/diagnostics/logging_test.cpp`는 출력 수준, 경고 제한 시간, 줄바꿈/UTF-8, 여러 스레드의 메시지 혼합 방지, 출력이 숨겨져도 장비 조회가 실행되는지를 검사합니다. Backend `OperationLoggingTest`는 실제 Logback 출력의 시각·한 줄 길이·예외 요약과 SQL/바인딩/프레임워크 DEBUG 비활성화를 확인합니다.

Spring 설정과 로그 패턴은 [Spring Boot Logging](https://docs.spring.io/spring-boot/reference/features/logging.html), [Logback Layouts](https://logback.qos.ch/manual/layouts.html)에 따릅니다.

## 현장 검증

2026-11-01 검증에서 Prober CTest 37건과 Backend JUnit XML 집계 528건이 통과했습니다. Lab 1~4 Prober 10개를 같은 바이너리로 갱신하고 비어 있지 않은 새 수집을 확인했습니다. 로그 정리 검증 당시 다운로드 번들의 바이너리도 같은 SHA-256이었습니다. 이후 ANTLR 내장 빌드의 검증 상태는 [빌드 문서](./prober-build-debug.md)를 따릅니다.

`19ffd5d838ff863cc5b0810cccf51e4ab4d7412b7e70fbb482a9748809079908`

최종 R1/R2는 각 45초 동안 수집 시각이 갱신됐고 추가 로그는 각각 0줄이었습니다. 각각 프로세스 1개·스레드 4개·idle 자식 프로세스 0개를 확인했습니다. Backend 실제 서비스의 새 실행 로그에서 SQL·DEBUG 출력도 0건이었습니다. 4개 프로젝트/등록 Agent 10개를 유지하고 Lab 1 실행·나머지 Lab 정지 상태로 복원했습니다.

화면의 짧은 오류 표시와 요청/장비 로그 상세 모달은 [로그 기능 안내](../features/logs-compliance.md#짧은-오류와-상세-보기)를 참고하세요.
