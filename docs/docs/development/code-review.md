# 코드 품질과 설계 검토

기준은 현재 작업 트리의 실제 소스입니다. 생성 코드·외부 라이브러리까지 한 줄씩 전수 검토했다는 의미는 아닙니다. 핵심 경로를 읽고 단위/통합 시험과 실제 Lab 통신 시험으로 확인합니다. 구현 완료와 검증 범위를 구분합니다.

## 핵심 기능에서 수정한 문제

| 문제 | 현재 처리 | 검증 |
| --- | --- | --- |
| HTTP 요청 저장을 장비 적용 성공으로 오인 | request_id/revision과 PENDING/APPLIED/FAILED, 마지막 확인 차단 상태 저장 | Backend 격리 수명 주기 시험, 실제 Lab |
| 이전 작업의 지연 ACK가 새 작업을 덮음 | 현재 계획 revision과 요청 ID가 다르면 거부, 노드별 DB 잠금 | Backend ACK/경합 회귀 시험 |
| 해제 실패가 목록에서 사라짐 | pending_releases와 재시도 표시 | Backend 상태 시험, UI 모달 |
| GuestShell Linux 포트를 내려 IOS 격리로 간주 | IOS EEM / EOS native 포트 제어, VPG/관리 포트 보호 | native 실행기 시험, Lab1/2 |
| 원래 DOWN인 포트도 해제 시 UP | 변경 전 admin journal을 저장하고 소유 변경만 복원 | interface_quarantine_test |
| FRR 포트 DOWN 후 기본 경로 유실 | 포트/경로 대신 소유 iptables filter 체인 사용 | 실제 Lab4, 원본 filter/NAT/route 비교 |
| 기존 ESTABLISHED 허용이 격리를 우회 | 전용 체인을 우선 적용, 관리 경로만 예외 | 분리한 namespace의 기존/새 TCP 시험 |
| 내려간 인터페이스의 경로가 계속 그림에 남음 | admin/oper/부모 상태와 선택된 경로 검사 | Backend 토폴로지 시험, 실제 Lab |
| IOS shutdown 시 마스크 근거가 사라져 서브넷 메타데이터 삭제 | 같은 포트의 같은 host 주소는 기존 CIDR 보존 | 자동 동기화 회귀 시험 |
| Arista L2 포트 상태 누락 | JSON linkStatus 수집과 포트 별칭 정규화 | collector fixture, 실제 VLAN 시험 |
| 15초 목록 갱신이 활성 터미널을 제거하고 새 셸을 시작 | 같은 프로젝트는 데이터/컴포넌트를 유지하고 직렬 주기 조회 사용 | 수동 갱신과 16초 대기 후 WebSocket/셸 시작 횟수, exit를 검사하는 Robot |
| UI 조회 경합과 수동 새로고침 의존 | 완료 후 다음 조회 예약, 이전 프로젝트 응답 무시, 마지막 정상 그림 보존 | Frontend 회귀 시험과 Robot |
| 세로 그래프를 가로 폭 전체로 확대해 수천 px로 표시 | SVG 본래 크기와 높이 제한에 맞춰 초기 배치, 확대/이동 유지 | Robot 실제 다이어그램 높이 검사 |
| Mermaid 노드 ID 충돌과 라벨 구문 삽입 | 코드 포인트 기반 ID와 라벨 escape | Frontend 회귀 시험 |
| 정책 응답 대기 중 COMMAND 유실(CR-06) | 같은 스레드의 bounded deque에 보관 | 실제 loopback WebSocket 통합 시험 |
| OVS 일반 ACL의 삭제 후 개별 추가(CR-09) | 소유 cookie 삭제/추가를 한 bundle로 교체, 결과 재조회 | 실패 주입과 명령 계약 시험 |
| Cisco ACL에 포트가 없어도 성공(CR-07) | 변경 전 유일한 데이터 포트 확인, 적용 후 ACL/바인딩 재조회 | 가짜 장비 실행기 시험. 일반 ACL 전체를 실장비 완료로 주장하지 않음 |

CR-02 API 쓰기 역할 검사, CR-03 오프라인 입력 거부 전 상태 보존, CR-04 불완전 수집 보호, CR-14 영구 암호화 키 검사, CR-17 설정 저장 경합, CR-18 HTTP 본문 시간 제한은 기존 회귀 시험에 유지합니다. 현재 검증한 변경은 main에 커밋하고 반영합니다. 통합되지 않은 원격 브랜치는 삭제하지 않습니다.

## 실제 적용한 패턴

| 패턴/원칙 | 위치 | 목적과 한계 |
| --- | --- | --- |
| Strategy | Backend DeviceConfigParser / DevicePolicy / StrategyRegistry | 벤더별 해석과 정책 생성을 등록. 같은 최고 우선순위가 모호하면 거부 |
| Strategy와 구성 | Prober TelemetryStrategy / PolicyExecutor / DeviceCommandExecutor | 동작 있는 공통 상위 클래스 대신 실행 포트를 주입. 정책 명령 구현의 큰 파일은 여전히 있음 |
| Observer | TelemetryEvents, TelemetryEventPublisher / TelemetryObserver | 수집 완료 후 저장·전송·등록·서브넷 동기화. 동기 호출이며 메시지 브로커가 아님 |
| 명시적 상태 전이 | QuarantineState / QuarantineService / quarantine_plan | 요청/확인/실패 분리. GoF State 클래스 계층을 억지로 추가하지 않음 |
| 단일 자원 소유 | main + 수집/관리/터미널 jthread | 소켓·PTY는 각 worker가 소유. SQLite는 mutex로 동기 저장 |

UML과 이벤트 순서는 [장비 확장 구조](../architecture/device-extensions.md), 스레드·DB·종료 순서는 [실행 구조](../architecture/prober-runtime.md)를 기준으로 합니다. 프레임워크 visitor/JPA/인터페이스 상속까지 모두 없애는 것이 목표는 아닙니다.

## 남은 설계 문제와 완료 조건

| ID | 우선 | 남은 문제 | 해결 조건 |
| --- | --- | --- | --- |
| CR-01,13 | P1 | 장비 관리/수집 WebSocket의 노드별 인증과 TLS가 부족함 | 노드별 폐기 가능한 자격증명, TLS 검증, ID 사칭/폐기/재접속 통합 시험. 현재 관리망 Lab 밖 배포 승인 근거로 쓰지 않음 |
| CR-05 | P1 | 일반 자동 정책은 첫 서브넷을 기준으로 생성함 | 노드의 모든 인터페이스/VLAN 계획을 집계하고 순서 무관성과 실제 L3 적용 검증. 격리 계획은 별도의 전체 target snapshot 사용 |
| CR-07 후속 | P1 | IOS 일반 ACL 교체는 detach/재생성 방식이며 EEM 명령 허용 범위와 함께 검증 필요 | 별도 준비 ACL을 검증한 후 바인딩 전환, 실패 시 이전 ACL 유지. 이번 native 격리 시험과 구분 |
| CR-12 | P1 | 일반 수동 push와 pull의 명령 계약/적용 ACK가 통합되지 않음 | 공통 PolicyPlan 또는 명시적 refresh-policy 계약과 종단 시험. 전송 성공을 적용 성공으로 표시하지 않음 |
| CR-10 | P2 | 프로젝트 전체 PUT에 낙관적 편집 revision이 없음 | 충돌 409와 초안 보존, 자동 동기화와 동시 편집 시험 |
| CR-15 | P2 | SQLite 수집 이력의 보존/용량 정책 부족 | 최대 일수/크기, 삭제 중 수집 일관성, 디스크 부족 시험 |
| CR-16 | P2 | 오프라인 파일에 모든 장비의 전체 전달 상태를 보존하지 않음 | 방화벽/OVS fixture 왕복과 누락 능력 표시 |
| CR-19 | P2 | DB 마이그레이션이 수동 SQL과 Hibernate update에 분산 | 버전 마이그레이션 단일화, 운영 DB 엔진 CI |
| 유지보수 | P2 | cli_output_parser와 일부 Frontend 페이지/QuarantineService가 큼 | 기능별 작은 함수/컴포넌트로 단계 분리. 공통 프레임워크를 더 늘리지 않고 동작 회귀로 보호 |
| 분석 범위 | P2 | 자체 BDD는 정책/헤더 모델이며 전체 Batfish 데이터 평면이 아님 | 라우팅/VRF/NAT/ACL/방화벽 전달 모델의 실제 엔진 연동과 differential 검증 |

위 항목을 남긴 채 모든 기능의 모든 버그를 제거했다고 표현하지 않습니다. Lab의 자동 일반 정책 집행 설정을 임의로 활성화하지 않습니다. 직접 OPNsense 격리는 지원한다고 가장하지 않고 요청을 거부합니다.

## 사람이 이해할 수 있는 코드의 기준

수집 → 저장/전송, 정책 조회 → 적용/재조회 → ACK, 종료 요청 → worker join → DB close 순서를 함수 이름과 문서에서 따라갈 수 있어야 합니다. 새로운 worker, promise, barrier, 별도 DB 큐를 추가하지 않습니다. EEM은 IOS CLI 접근, Bash는 기존 배포/시작 절차에 한정합니다. 의미 없는 장식 주석과 중복 빈 줄은 만진 코드에서 정리하고, 명령 형식·관리 경로·원상 복구 조건을 설명하는 주석은 남깁니다. 외부 ANTLR 소스와 라이선스는 보존합니다.


## 발표 전 추가 정리와 검증

핵심 파일을 읽고 책임 경계를 점검했습니다. 자동 생성 ANTLR 파일과 외부 라이브러리는 수동 리팩토링 대상에서 제외합니다. 모든 소스 줄을 수동으로 검토했다는 뜻은 아닙니다.

| 영역 | 적용한 정리 | 이유 |
| --- | --- | --- |
| Frontend | Agent 페이지의 격리 모달을 `components/agent/IsolationModal.tsx`로 추출 | 페이지는 API와 상태를 소유하고 모달은 표시와 입력에 집중 |
| Frontend | ` PolicyManagement.tsx`의 선행 공백 제거 및 import 수정 | 경로 탐색과 도구 처리를 예측 가능하게 유지 |
| Backend | `QuarantineTopologyView`에 토폴로지용 상태 집계 분리 | 트랜잭션과 조회는 서비스에 남기고 순수 계산을 분리 |
| Prober | IOS 인터페이스 선택과 CIDR 판정을 `ios_acl_target.hpp`로 분리 | 정책 명령 생성과 대상 선택의 책임을 구분 |
| Prober | IPv4 옥텟 문자열을 최대 3자리로 제한 | 지나치게 긴 입력의 정수 누적 오버플로 방지 |

추가 worker, promise, 클래스 상속 계층 또는 실행용 스크립트를 도입하지 않았습니다. Frontend 시험/프로덕션 빌드, Backend 전체 시험, Prober core 컴파일과 policy_safety_test로 변경을 확인했습니다. 실제 Lab1 모달 격리·복구 회귀 결과는 별도 실행 기록을 기준으로 합니다. 이 초기 검토 시점에는 Backend/Prober 분리 변경을 개발 트리에서만 검증했습니다. 이후 새 바이너리를 실제 배포한 결과는 아래 최종 재검증 절을 기준으로 합니다.

패키지 정리에서 남은 점은 Backend의 `Service`/`Model` 등 대문자 패키지 관례, 큰 `LogManagement`/`ProjectEditor` 페이지와 CLI parser입니다. 발표 직전 전체 패키지 이동은 하지 않았습니다. 후속 작업은 기능별 화면 컴포넌트와 순수 파싱 함수의 작은 추출부터 진행하고, 수집·격리·복구 계약은 기존 시험으로 유지합니다.


## main 반영 후 재검토

검토 기준 시작 커밋: `8afaf68`. 아래는 재확인한 사항이며 미해결 항목은 완료로 표시하지 않습니다.

| 항목 | 실제 문제 | 조치 / 확인 |
| --- | --- | --- |
| DB-01 | 현재 소스와 달리 추적 중인 `SonarValidator_Prober/main` ELF에는 이전 `DatabaseWorker`가 남음 | 오래된 main과 DB 시험 실행 파일을 Git에서 제거하고 ignore 추가. 소스로 다시 빌드하도록 통일 |
| DB-02 | 동기 DatabaseService의 매개변수가 여전히 queue라는 이름이라 구조를 오해하게 함 | database_service로 명확히 변경. DB 전용 스레드·작업 큐는 없음 |

DB 회귀 시험은 생성 전후 스레드 수가 같고 Execute가 호출자 스레드에서 실행됨을 확인합니다. 동시 저장 80회와 진행 중 트랜잭션의 영속성, Close의 대기와 종료 후 쓰기 거절도 검사합니다. mutex는 여러 worker의 설정 저장과 수집 저장 간 충돌을 막기 위해 유지합니다. SQLite 핸들은 worker 종료 이후 RAII로 해제합니다.

추가 점검 순서는 WebSocket 입력/노드 식별, 일반 정책의 적용 범위, UI 모달과 화면 경계, Prober 종료와 설정/수집 계약입니다. 디자인 패턴을 새로 늘리기보다 기존 Strategy와 동기 Observer의 책임 및 실패 처리를 검사합니다.

## main UI·입력 경계 회귀 수정

| ID | 재현 조건과 영향 | 수정 |
| --- | --- | --- |
| WS-01 | WebSocket에 JSON `null`을 보내면 역직렬화 후 접근에서 예외 | 빈 envelope를 명시적으로 거부하고 router에 전달하지 않음 |
| WS-02 | 잘못된 JSON의 parser 진단에 입력 일부가 포함되어 응답/로그로 노출될 수 있음 | 짧은 고정 오류만 반환하고 원문 진단 출력 제거 |
| UI-01 | 격리 요청 중 Escape/배경 클릭으로 모달을 닫거나 실패해도 입력이 사라짐 | 처리 중 닫기를 막고 실패 시 입력과 오류를 모달에 유지 |
| UI-02 | R1 범위 조회를 기다리다가 R2를 열면 늦은 R1 응답이 R2 선택지를 덮음 | 요청 번호로 최신 노드 조회만 반영하고 닫을 때 무효화 |
| UI-03 | 낮은 화면에서 모달이 화면 밖으로 넘어가고 dialog 역할 중첩 | 공통 모달 높이와 스크롤 제한, dialog 역할과 접근성 이름의 소유 위치 통일 |

추가 스레드나 상태 관리 프레임워크를 넣지 않았습니다. UI 요청 번호는 서버 요청을 취소하거나 장비 적용 성공을 대신하는 값이 아니라, 오래된 화면 응답을 버리는 용도입니다. 적용 결과는 기존 request_id/revision과 ACK 경로를 유지합니다.

검증: Backend 539개 시험, Frontend 27개 시험 및 프로덕션 빌드, lint 오류 0개(기존 React refresh 경고 7개), Prober CTest 일반 시험 40개와 별도 네트워크 namespace의 root 통신 시험 통과. Robot B01~B06은 6개 모두 통과했습니다. B05는 실패 응답을 주입해 모달/입력 보존을 검사하며 실제 장비를 격리하지 않습니다. B06은 응답 순서를 뒤집어 노드 간 선택지 혼입을 재현합니다. 실제 장비 격리/복구 결과는 Lab 종단 시험 기록과 구분합니다.

재현 명령은 기존 실행기에 통합했습니다. `SONAR_UI_PASSWORD`를 환경변수로 설정하고 `python3 tests/labs/run-labs.py all check --scenario isolation-ui`를 실행하면 8개 격리 대상에 대해 실제 드롭다운/요청 버튼을 사용합니다. 별도 실행 스크립트는 늘리지 않았습니다. Cisco `write memory`는 콜드 부팅 후 12초를 넘길 수 있어 저장 명령만 최대 60초 기다리고, 실패 시 어느 명령에서 멈췄는지 표시합니다. 장비 변경 명령은 자동으로 반복 실행하지 않습니다.

### 실장비 상태 복구 기록

R1의 `data/quarantine_interfaces.json`에 JSON 대신 ARP 출력 텍스트가 들어 있어 기존 해제 요청이 FAILED로 남았습니다. Prober가 잘못된 복원 자료를 거부한 동작은 유지했습니다. Gi3의 running/startup 설정, 실제 통신, 관리 SSH와 `active=false / blocked=false`를 확인하고 잘못된 파일을 별도로 보관한 다음 이미 복구된 상태의 빈 journal을 재구성했습니다. 기존 해제 요청을 재시도하여 `APPLIED / Original device interface states restored and verified`를 확인했습니다. 이 자료가 ARP 출력으로 바뀐 원인은 확정하지 못했으며, 이를 GNS3 버그나 DB 스레드 문제로 단정하지 않습니다. 잘못된 journal을 자동으로 지우거나 무시하는 코드는 추가하지 않았습니다.

### 배포 아티팩트 식별

빌드 폴더와 Backend의 `SONAR_DEPLOY_STAGE_DIR`가 별도여서 새 빌드 뒤에도 이전 파일을 내려받는 운영 실수를 확인했습니다. 배포 폴더를 갱신하고 다운로드 API의 번들 내부 바이너리 SHA-256이 빌드 결과와 같은지 검사했습니다. Lab 실행기는 `SONAR_EXPECTED_PROBER_SHA256`가 지정되면 다운로드 단계에서 다른 빌드를 거부하며, SSH 전송 후에도 설치된 파일의 해시를 번들과 비교합니다. 잘못된 기대 해시를 주었을 때 설치 전에 거부하는 검사도 통과했습니다. 검증 빌드 해시는 실행 결과에 기록합니다.

검증 결과는 기존 바이너리로 수행한 실행과 해시가 일치하는 새 빌드의 실행을 분리합니다. 이전 실행의 성공을 새 바이너리의 검증으로 집계하지 않습니다.

### 재부팅 후 복원 기록의 내구성 보강

R2에서도 격리·해제 직후에는 정상인 journal이 VM 재시작 후 ARP 내용으로 되돌아오는 현상을 관찰했습니다. 기존 journal 쓰기는 `ofstream` 종료와 rename만 수행하여 파일 내용과 디렉터리 변경의 디스크 반영을 보장하지 않았습니다. 정확히 어느 가상화/파일시스템 계층에서 이전 데이터가 보였는지는 단정하지 않되, 이 저장 보장 누락은 수정했습니다.

`utils/atomic_json_file.hpp`의 공유 저장 함수가 고유 임시 파일(0600)에 기록하고 파일 fsync, rename, 부모 디렉터리 fsync를 차례로 수행합니다. 원본 포트 상태는 이 저장이 성공한 뒤에만 장비에 적용하며, 복원 완료 기록도 저장에 성공한 뒤 ACK합니다. ExportSnapshot은 공개 파일 내보내기 진입점 하나를 유지하고 같은 내부 저장 함수를 재사용합니다. worker나 DB 큐는 추가하지 않았습니다.

기존 회귀 시험에 ARP 텍스트를 잘못된 journal로 주입한 경우의 거부·원본 보존과 별도 프로세스에서의 원본 포트 복원을 추가했습니다. 일반 CTest 40개와 root 네트워크 namespace 시험은 통과했습니다. 새 Robot `isolation-reboot` 시나리오는 Lab1 두 Cisco를 실제 격리한 상태로 재시작하고 journal 동일성, 재적용, 해제 후 통신 복구를 검사합니다. 단순 JSON 파싱 성공이나 이전 빌드 결과를 재부팅 검증 대신 사용하지 않습니다.


### 최종 main 재검증 결과

2026-11-02 KST에 새 Backend와 Frontend, SHA-256이 `caab8e14db81c86d05cece13c3e4d0b3ee82d2be920c52818dd6f98ee7fb41d6`인 Prober로 Lab1~4를 재검증했습니다. 10개 Prober의 설치 파일이 이 해시와 일치합니다. 이 바이너리에는 이전 DatabaseWorker 심볼이 없습니다.

| 검증 | 결과 |
| --- | --- |
| Backend | 539개 통과 |
| Frontend | 27개 통과, 프로덕션 빌드 성공, lint 오류 0 / 기존 경고 7 |
| Prober | 일반 CTest 40개 통과, 별도 root 네트워크 namespace 시험 통과 |
| Robot UI 회귀 | B01~B06 6개 통과 |
| Lab1~4 주요 기능 | 각 6개, 합계 24개 통과 |
| 실제 UI 격리·복구 | Cisco 3구간, Arista VLAN20, Ubuntu 2대, FRR, OVS 총 8개 통과 |
| Lab1 격리 중 VM 재시작 | R1·R2 2개 통과: journal 동일성, 재적용, 해제 후 복구 |
| Lab1 일반 UI / 종료·EEM 복구 | 14개 / 1개 통과 |

Robot 합계는 55개 통과, 실패·건너뜀 0개입니다. 실제 격리 검사는 현재 요청의 ACK, 데이터 패킷, 관리 SSH, 수집된 토폴로지와 화면을 함께 검사합니다. 종료 시험은 동작 중인 터미널과 자식 작업을 정리하고 EEM이 Prober를 다시 시작하는지 확인합니다. 운영 코드에 DB worker 또는 새 스레드를 추가하지 않았습니다.

격리 중 재부팅 검증은 Lab1 Cisco 2대에 한정됩니다. 이번 결과를 모든 장비의 모든 전원 손실 상황에 대한 보장으로 확대하지 않습니다. BDD는 POLICY_HEADER_SPACE 범위이고 전체 Batfish 데이터 평면 분석이 아닙니다. AI는 모의 공급자로 제안·검토·실제 조회 명령·미지원 명령 거부를 확인했습니다. 위 미해결 P1/P2 항목은 그대로 남아 있습니다.
