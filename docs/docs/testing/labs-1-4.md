# Lab 1~4 기능 검증 시나리오

프로젝트의 실제 장비 수집과 사용자 조작을 함께 검증합니다. 주요 기능 6개를 각 Lab에서 실행하며 시나리오별 WebM, PNG, JSON 근거를 남깁니다. 정책 예제는 임시 프로젝트에서 만들고 종료 시 제거합니다. F01~F06은 조회와 복원 가능한 설정 시험입니다. 아래 Q01/Q02에서는 실제 장비 격리와 해제를 별도로 검증합니다. AI는 루프백 모의 공급자를 사용하며 조회 명령만 실제 Prober로 실행합니다.

## 실험 구성

| Lab / 프로젝트 | Prober | API 장비 | 데이터 경로 | 관리 서버 |
| --- | --- | --- | --- | --- |
| 1 / PRJ-86F69C4D | Cisco R1, R2 GuestShell | 없음 | R2의 Gi2에서 R1 Gi3 NAT | 192.168.148.180 |
| 2 / PRJ-LAB2 | Cisco, Arista, Ubuntu | OPNsense | VLAN20, 10.20.20.0/24 | 192.168.150.10 |
| 3 / PRJ-LAB3 | Ubuntu A, B | OPNsense | 10.30.10.0/24와 10.30.20.0/24 | 192.168.151.10 |
| 4 / PRJ-LAB4 | FRR, OVS, Ubuntu | 없음 | VLAN40, 10.40.40.0/24 | 192.168.152.10 |

주소·SSH 대상·GNS3 노드 식별자의 실행 기준은 `tests/labs/inventory.json`입니다. OPNsense에는 Linux Prober를 설치하지 않고 Backend의 API 연결 장비로 등록합니다. Lab3는 컨테이너 관리 NIC를 추가하지 않습니다. SSH 설치에는 기존 방화벽 ProxyJump를 쓰며 Agent가 TCP 3000으로 먼저 연결합니다.

## 기능별 시나리오

| ID | 화면과 조작 | 실제 확인 | 기록 |
| --- | --- | --- | --- |
| F01 | 프로젝트 목록에서 Lab과 소속 노드 확인 | Agent 연결뿐 아니라 NIC·경로·VLAN 수집과 기대 CIDR 확인 | collection.json, 프로젝트 PNG |
| F02 | 관리 화면 또는 API 연결 노드에서 연결 테스트 | 인터넷 HTTPS, VLAN과 관리 경로, OPNsense API 5종 | network.json, API/경로 PNG |
| F03 | 노드 설정의 서브넷·역할·CSO 드롭다운, 수집 주기 변경 | 임시 초안은 폐기, 변경 주기는 Prober ACK 후 원래 값으로 복원 | 연결 모달·적용 상태 PNG |
| F04 | 실제 웹 터미널에서 표식 출력과 사용자 조회, exit | 인증된 Agent 채널 응답과 셸 종료 | 터미널 PNG·WebM |
| F05 | 임시 정책의 Confidential/Open 위반과 Sensitive/Open 허용 비교, JSON 출력 | BDD 모델 판정과 단일 JSON 파일의 분석 범위 | 정책 화면 PNG, 다운로드 JSON |
| F06 | 모의 AI 제안 확인, 대상·명령 검토, 확인 체크 후 조회 실행, 미지원 명령 차단 | 장비별 실제 조회, verified=true, changed=false | AI 명령·결과 PNG, 실행 JSON |

등급 예제는 기능 시험을 위한 데이터입니다. 실제 Lab의 보안 등급을 임의로 확정하거나 기존 운영 정책을 덮어쓰지 않습니다. F03 연결 초안도 저장하지 않습니다.

## Lab별 네트워크 확인

### Lab1

두 GuestShell의 인터넷 HTTPS와 EEM NIC·경로 캐시를 확인합니다. R1/R2 내부 IP가 같아도 관리 NAT 주소로 구분합니다. AI 조회 대상은 R1의 show ip route입니다.

### Lab2

Arista VLAN20의 Et1·Et2와 Ubuntu 인터넷을 확인합니다. Ubuntu의 관리 NIC 통신과 데이터 NIC를 강제한 관리 서버 접근을 구분합니다. Cisco GuestShell이 서버에 먼저 연결하도록 Gi2에서 목적지를 192.168.150.10 한 대로 제한한 PAT를 사용합니다. 기존 인터넷 NAT는 Gi1입니다.

OPNsense의 firmware, interfaces, rules, nat, aliases를 Backend API로 조회합니다. NAT API 목록은 API 모델 조회 결과이며 실제 전체 pf NAT 적용 여부를 대신하지 않습니다. 인터넷 HTTPS 검사는 별도로 기록합니다. AI 조회는 Arista show vlan입니다.

### Lab3

각 Ubuntu의 게이트웨이·인터넷·서버 TCP 3000 통신은 허용되고, A/B 사이 ICMP와 관리 서버 ICMP, 관리 서버에서 컨테이너로의 직접 SSH는 차단되는지 확인합니다. 같은 SSH 서버에 ProxyJump로 접근할 수 있음을 함께 확인해 서비스 부재와 방화벽 차단을 구분합니다. AI는 Ubuntu B에서 ip -j route show를 실행하며 Backend에서 새 인바운드 관리 포트를 열지 않습니다.

### Lab4

FRR 연결 경로, OVS eth0·eth2의 tag=40, NORMAL flow와 관리 eth1의 데이터 브리지 미포함을 확인합니다. Ubuntu 인터넷과 데이터 NIC를 강제한 관리 서버 접근 실패를 기록합니다. OVS가 L2 VLAN에 라우터 주소나 CIDR을 만들어 내지 않는지 수집 결과도 확인합니다. AI 조회는 ovs-vsctl Port 목록입니다.

## 준비와 실행

WSL에서 기존 SSH 키와 GNS3 서버 설정을 사용합니다. 개인키를 장비로 복사하지 않습니다. API 키는 다음 비공개 파일에서 읽으며 Robot 로그나 산출물에 넣지 않습니다.

- ~/.config/sonarvalidator/labs/lab2-opnsense-api.env
- ~/.config/sonarvalidator/labs/lab3-opnsense-api.env

```bash
cd /home/ssh1032007/SonarValidator
# 새 이미지에는 수집 도구가 포함됩니다. 이전 이미지에 오프라인 패키지가 필요할 때만 실행
python3 tests/labs/labctl.py cache-tools
# 실행 중인 다른 Lab을 멈추고 선택한 Lab을 시작합니다
python3 tests/labs/labctl.py activate lab2
# 최초 배포 또는 바이너리 갱신. 실제 로그인 값은 환경변수로 전달
SONAR_UI_PASSWORD=... SONAR_DEVICE_PASSWORD=... python3 tests/labs/labctl.py bootstrap lab2
# 별도 터미널에서 모의 공급자 실행
node tests/robot/fixtures/ai-provider.mjs
# 사람이 볼 수 있는 속도. headless 환경에서는 SONAR_HEADLESS=true 추가
SONAR_UI_PASSWORD=... tests/robot/run.sh lab2 demo features
```

bootstrap은 서버 생성 번들의 Agent 이름·서버 주소를 사용하고 MANAGEMENT_PREFIX를 해당 Lab에 맞춥니다. 바이너리는 .next로 받은 뒤 교체합니다. Cisco는 저장한 EEM, Arista는 부팅용 event-handler, FRR은 OpenRC local, Ubuntu/OVS는 기존 시작 경로의 작은 훅으로 시작합니다. 새 DB 스레드나 명령별 Prober 스레드를 추가하지 않습니다.

새 Ubuntu 이미지는 `./dev.sh --build-images=ubuntu`로 준비하며 iproute2/nftables가 기본 포함됩니다. 배포 도구는 대상에 필수 명령이 있으면 패키지 캐시를 요구하지 않습니다. 이전 이미지의 오프라인 자산은 `/etc/sonar-validator/packages`에 두어 기존 부팅 훅이 복원합니다. `cache-tools`는 깨끗한 Ubuntu 26.04에서 의존 패키지를 내려받습니다.

12GiB 환경에서는 Cisco VM 여러 대를 포함한 모든 Lab을 동시에 켜지 않습니다. Lab별로 실행하고 종료 후 원래 활성 Lab을 복원합니다. 부팅 등록이 네트워크 설정을 다시 초기화하거나 SonarValidator 전체를 WSL 부팅 때 자동 실행하는 것은 아닙니다.

## 결과 해석

BDD는 POLICY_HEADER_SPACE 검증입니다. data_plane_analyzed=false를 화면과 JSON에서 확인합니다. 실제 경로·NAT·방화벽 도달성은 F02의 통신 근거로 따로 판단합니다. 모의 AI 응답은 기능 검증용이며 실제 원인 분석 품질 검증과 구분합니다.

권한·중복 요청·관리망 보호·적용 read-back 불일치는 Backend·Prober 단위/통합 테스트와 [AI 기능 검증](../features/ai-advice.md)을 함께 사용합니다. F01~F06의 조회 검증과 Q01/Q02의 실제 데이터 평면 변경은 별도 결과로 기록합니다.

실행 결과는 test-results 아래의 Lab별 report.html, log.html, videos, screenshots, evidence, downloads에 있습니다. .auth.json은 suite 종료 시 제거합니다. 기존 내부 키·환경 파일·원문 DB는 결과 묶음에 포함하지 않습니다.

## 실제 격리와 토폴로지 회귀

`inventory.json`의 isolation_cases가 실행 기준입니다. Q01은 API 요청 후 실제 통신, 현재 작업 ACK, 최신 관측과 자동 SVG 변화를 함께 검사합니다. Q02는 범위 미선택 시 요청 버튼 비활성화, 명시적 선택과 UI 요청, 실제 차단 및 복구를 검사합니다. 두 시험 모두 관리 SSH를 유지하고 종료 시 원래 통신과 라우팅/인터페이스 상태를 비교합니다. FRR은 원본 filter/NAT 규칙 순서도 비교합니다.

| Lab | 실제 대상 | 방식 | 차단 확인 |
| --- | --- | --- | --- |
| 1 | R1 WAN, R2 transit | IOS EEM native port | GuestShell의 WAN gateway ping |
| 2 | Cisco transit, Arista VLAN20 | IOS/EOS native port | Ubuntu HTTPS |
| 3 | Ubuntu A, B | 소유 nft 테이블 | 각 Ubuntu HTTPS |
| 4 | FRR forwarding, OVS VLAN40 | 소유 iptables 체인 / OVS bundle | Ubuntu HTTPS |

```bash
SONAR_UI_PASSWORD=... SONAR_DEVICE_PASSWORD=... \
  python3 tests/labs/run-labs.py lab2 demo --scenario isolation --prepare
```

기본 runner는 초기 GNS3 시작/중지 상태를 복원합니다. 진단을 위해 `--keep-active`를 사용하면 직접 원래 상태로 돌려야 합니다. 캡처와 원본 WebM, 장비 확인 JSON, Robot HTML/XML을 함께 보관합니다. 실패한 실행을 지우고 성공한 것처럼 쓰지 않습니다. 스크립트를 새로 늘리지 않고 기존 LabControl, LabEvidence, run.sh의 시나리오를 확장합니다.
