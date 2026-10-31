# Prober 실행과 수명 관리

## 작업 스레드

main은 신호를 받아 중지를 요청합니다. 수집·정책 관리·터미널 채널은 각각 하나의 jthread를 사용합니다. DB 전용 스레드·promise/future 작업 큐·추가 종료 감시 스레드는 없습니다.

터미널 작업 스레드는 기존 Asio WebSocket 이벤트와 PTY 입출력을 처리합니다. 셸 프로그램은 별도 프로세스가 필요합니다. 사용자 코드의 fork 후 C++ 할당 경로를 없애고 posix_spawn으로 실행합니다. 이는 셸 자체를 스레드로 실행한다는 의미가 아닙니다.

## 수집 완료 이벤트와 DB

수집 완료 시 PersistSnapshot이 저장 함수를 직접 호출합니다. DatabaseService는 같은 호출 스레드에서 작업을 실행하며 mutex로 SQLite 핸들을 직렬화합니다. 새로운 종류의 수집 이벤트는 telemetry_store의 저장 함수와 완료 이벤트 핸들러에 추가합니다.

저장 성공은 큐 등록 성공이 아니라 실제 커밋 성공입니다. 행 묶음마다 트랜잭션을 사용하고, BEGIN 실패 시 쓰지 않습니다. 행 실패·COMMIT 실패는 롤백합니다. SQLite busy timeout은 2초입니다. 여러 표 전체를 하나의 원자적 스냅샷으로 묶는 모델은 현재 구현하지 않습니다.

일반 종료는 생산 작업 스레드를 정지·join한 다음 DB를 닫습니다. 큐에 남는 비동기 쓰기가 없고, Close도 실행 중인 저장이 끝날 때까지 기다립니다. 성공을 가장한 _Exit(0) 강제 종료를 제거했습니다. 저장 장치 오류·SIGKILL까지 데이터 보존을 보장하는 설계는 아닙니다.

## 터미널 자원

셸마다 새 세션과 PTY를 만듭니다. 표준 입출력을 연결한 뒤 나머지 FD를 닫아 서버 소켓·DB 파일·EEM 실행 잠금이 셸에 상속되지 않게 합니다. EOF와 자식 셸의 waitid 상태를 함께 확인합니다. 셸이 먼저 종료되고 배경 작업이 PTY를 잡고 있어도 terminal-exit을 보내고 남은 작업을 정리·회수합니다.

브라우저 닫기·Agent 채널 단절·프로그램 중지는 같은 Close 경로를 사용합니다. job control로 분리된 전경·배경 프로세스 그룹도 같은 세션으로 찾아 종료합니다. TERM 후 최대 200ms를 기다리고 남은 프로세스에 KILL을 보내 직접 자식을 waitpid로 회수합니다. PTY는 nonblocking이며 입력 정체 대기는 최대 250ms입니다.

검증 원본: `tests/prober/run.sh`, `tests/robot/suites/lab_ui.robot`. [Linux posix_spawn 명세](https://man7.org/linux/man-pages/man3/posix_spawn.3.html)는 속성 적용 뒤 파일 작업을 실행하는 순서와 세션 생성 동작의 근거입니다.

현재 수집/정책 전략과 동기 옵저버 구성은 [장비 확장 UML](device-extensions.md)을 기준으로 합니다. 통신 서비스는 장비별 정책 구현을 상속하거나 소유하지 않고 명령 실행 포트와 전략 registry를 연결합니다.
