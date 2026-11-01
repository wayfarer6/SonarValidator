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

CR-02 API 쓰기 역할 검사, CR-03 오프라인 입력 거부 전 상태 보존, CR-04 불완전 수집 보호, CR-14 영구 암호화 키 검사, CR-17 설정 저장 경합, CR-18 HTTP 본문 시간 제한은 기존 회귀 시험에 유지합니다. GitHub 작업은 수행하지 않습니다.

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

추가 worker, promise, 클래스 상속 계층 또는 실행용 스크립트를 도입하지 않았습니다. Frontend 시험/프로덕션 빌드, Backend 전체 시험, Prober core 컴파일과 policy_safety_test로 변경을 확인했습니다. 실제 Lab1 모달 격리·복구 회귀 결과는 별도 실행 기록을 기준으로 합니다. 이번 Backend/Prober 분리 변경은 개발 트리에서 검증했고, Lab의 기존 검증된 실행 바이너리를 교체한 것으로 해석하면 안 됩니다.

패키지 정리에서 남은 점은 Backend의 `Service`/`Model` 등 대문자 패키지 관례, 큰 `LogManagement`/`ProjectEditor` 페이지와 CLI parser입니다. 발표 직전 전체 패키지 이동은 하지 않았습니다. 후속 작업은 기능별 화면 컴포넌트와 순수 파싱 함수의 작은 추출부터 진행하고, 수집·격리·복구 계약은 기존 시험으로 유지합니다.
