# 교수님 발표용 Lab 1~4 데모 시나리오

20220555 서성훈 · SonarValidator 중간 발표 · 2026-11-01 작성

## 발표에서 확인할 핵심

침해 징후가 외부에서 보고되었다고 **가정**하고 운영자가 격리합니다. 악성코드 실행, 공격 트래픽 생성, 자동 침해 탐지는 수행하지 않습니다. Robot은 실제 UI의 격리 범위 드롭다운과 요청·해제 버튼을 누릅니다. 요청 저장만으로 성공 처리하지 않고 현재 요청의 장비 ACK, 실제 패킷, 관리 SSH, 새 텔레메트리와 SVG 갱신을 검사합니다.

5분 발표에서는 본문 8장과 짧은 녹화만 사용합니다. 추가 9~12장은 각 Lab의 라이브 데모·질의응답용입니다. 네 Lab을 모두 콜드 부팅해 시연하면 VM 부팅 시간이 추가됩니다. 발표 전에 원하는 Lab을 준비하고 한 Lab을 라이브로 실행하는 구성을 권장합니다. 모든 Lab을 동시에 켜지 않습니다.

## 실행 준비와 공통 명령

WSL Ubuntu의 현재 저장소에서 실행합니다. 기존 Backend 3000, Frontend 5173, GNS3 서버와 TAP 자동 복원 서비스가 준비되어 있어야 합니다. UI에서 해당 프로젝트와 Agent가 보이는지 확인합니다. 기존 SSH 키 `/home/ssh1032007/.ssh/id_ed25519`를 사용하며 비밀번호를 소스나 명령 인자에 적지 않습니다.

```bash
cd /home/ssh1032007/SonarValidator
read -rsp 'UI password: ' SONAR_UI_PASSWORD; echo
export SONAR_UI_PASSWORD
python3 tests/labs/run-labs.py lab1 demo --scenario presentation --keep-active
```

- `lab1`을 `lab2`, `lab3`, `lab4`로 바꾸면 해당 프로젝트의 시연 한 편을 실행합니다.
- `python3 tests/labs/run-labs.py all demo --scenario presentation`은 순차 실행 후 시작 전 VM 실행/정지 상태로 돌립니다. 현재 Lab을 계속 켜둘 때만 `--keep-active`를 사용합니다.
- 창 표시가 안 되는 WSL 환경에서는 `SONAR_HEADLESS=true`를 앞에 붙입니다. 화면이 없어도 동일한 브라우저 동작을 WebM으로 녹화합니다.
- 기본 속도는 동작당 400ms, 장면당 2초입니다. `SONAR_SLOW_MO=600ms SONAR_STEP_PAUSE=4s`로 설명 시간을 늘릴 수 있습니다.
- 결과 위치는 콘솔의 `Reports and recordings` 경로입니다. `ROBOT_OUTPUT_DIR`로 지정할 수 있습니다. `report.html`, `log.html`, `screenshots`, `videos`, `evidence/isolation.json`을 함께 보관합니다.
- 이미 다른 운영자가 격리한 상태이면 시작을 거부합니다. 실패 시 teardown이 이번 시연의 격리 해제를 시도합니다. 강제 종료·WSL 종료 시에는 teardown을 보장할 수 없으므로 UI에서 상태와 해제 ACK를 확인합니다.
- 이 명령은 재배포·재부팅을 수행하지 않습니다. 이미 설치된 현재 Prober를 사용합니다. 최초 부팅 대기는 최대 10분이며 네트워크 변경 확인은 단계당 최대 150초입니다.

이미 Lab이 켜져 있으면 GNS3 시작/정지를 생략하고 다음 명령으로 Robot만 실행할 수 있습니다.

```bash
bash tests/robot/run.sh lab1 demo presentation
```

## 공통 화면 진행 순서

1. 프로젝트 토폴로지에서 정상 관측 연결을 보여줍니다. 실제 데이터 경로가 통신하고 관리 SSH가 되는지 검사합니다.
2. Agent 목록에서 대상 노드의 `격리 설정`을 누릅니다. 처음에는 범위가 선택되지 않으며 요청 버튼이 잠겨 있습니다.
3. 아래 Lab별 범위를 직접 선택합니다. `격리 요청`을 눌러 장비의 현재 요청 ACK와 실제 차단을 기다립니다.
4. 토폴로지에서 격리 적용 표시를 확인합니다. 포트를 내린 장비는 관측 연결이 제거되고, ACL 방식은 연결을 유지한 채 차단 상태를 표시합니다. 두 방식을 같은 그림으로 오해하지 않도록 설명합니다.
5. 원인 조치가 끝났다고 가정하고 Agent 목록의 `서브넷 해제` 또는 `장치 격리 해제`를 누릅니다.
6. 해제 ACK, 실제 데이터 통신, 원래 인터페이스·경로 및 관측 연결 복구를 확인합니다. FRR은 원래 필터·NAT 규칙의 순서도 비교합니다.

## Lab 1 - R2 경유 구간 격리 / Cisco EEM

- 프로젝트: `PRJ-86F69C4D`, 대상 `Lab1-R2`.
- 상황: R2 경유 구간에서 의심스러운 외부 접속이 보고되어 일시 격리가 필요하다고 가정합니다.
- 선택 범위: `10.10.20.0/24` (R2의 데이터 transit 구간). 장치 전체 격리를 선택하지 않습니다.
- 정상 확인: R2 GuestShell에서 `192.168.42.1`까지 ICMP가 됩니다.
- 적용: Cisco EEM을 경유해 대상 데이터 포트의 원래 상태를 기록하고 shutdown 합니다. Gi1 관리 경로와 GuestShell 관리 연결은 보존합니다.
- 차단 확인: 같은 ICMP가 실패하고 R2의 `guestshell@192.168.148.182:2222` 키 SSH는 유지됩니다. 해당 관측 데이터 연결이 토폴로지에서 사라집니다.
- 해제: `서브넷 해제`를 누릅니다. 원래 포트 상태와 경로가 돌아오고 ICMP 및 관측 연결이 복원됩니다.

발표 멘트: “침해 징후를 R2 구간에서 보고받았다고 가정했습니다. 운영자가 해당 대역을 골라 격리합니다. 실제 통신은 막히지만 관리 접속을 유지하므로 원인 조사와 복구가 가능합니다.”

```bash
python3 tests/labs/run-labs.py lab1 demo --scenario presentation --keep-active
```

## Lab 2 - VLAN20 단말의 침해 확산 방지 / Arista

- 프로젝트: `PRJ-LAB2`, 격리 대상 `Lab2-SW1`, 패킷 검사 노드 `Lab2-Ubuntu`.
- 상황: VLAN20 Ubuntu의 의심스러운 외부 접속이 보고되어 스위치 데이터 경로를 일시 격리한다고 가정합니다.
- 선택 범위: `장치 IPv4 통신 · 관리 유지` (빈 CIDR 값). **VLAN20만 선택하는 시연이 아니라 Arista의 데이터 포트 전체를 대상으로 합니다.** 이 Lab의 데이터 VLAN이 VLAN20입니다.
- 정상 확인: Ubuntu에서 `https://example.com`이 HTTP 200을 반환합니다.
- 적용: Arista FastCli 배치로 원래 포트 상태를 기록하고 관리 포트를 제외한 데이터 포트를 내립니다. AEM은 도입하지 않았습니다.
- 차단 확인: Ubuntu의 외부 HTTPS가 실패하고 `admin@192.168.150.3` 키 SSH는 됩니다. VLAN20 포트와 관측 연결 상태가 갱신됩니다.
- 해제: `장치 격리 해제`. 원래 UP/DOWN 상태를 복구하고 HTTPS 및 연결 복원을 검사합니다. OPNsense는 경유 방화벽이며 이 데모에서 방화벽 API 격리를 수행하지 않습니다.

발표 멘트: “하위 Ubuntu가 VLAN20으로 연결되어 있습니다. 이번에는 스위치 단위로 데이터 포트를 격리했습니다. 관리 포트는 계속 연결되어 있고 해제 후 원래 통신과 토폴로지가 돌아옵니다.”

```bash
python3 tests/labs/run-labs.py lab2 demo --scenario presentation --keep-active
```

## Lab 3 - 방화벽 뒤 Ubuntu A 격리 / nftables

- 프로젝트: `PRJ-LAB3`, 대상 `Lab3-Ubuntu-A` (`10.30.10.10`). B는 `10.30.20.10`의 별도 노드입니다.
- 상황: A의 의심스러운 외부 접속이 보고되어 해당 컨테이너의 데이터 통신을 차단한다고 가정합니다.
- 선택 범위: `장치 IPv4 통신 · 관리 유지`.
- 정상 확인: A에서 `https://example.com`이 HTTP 200을 반환합니다.
- 적용: Prober 소유 nftables 테이블로 IPv4 데이터를 차단합니다. 물리 인터페이스와 라우팅을 내리지 않으며 관리 예외를 유지합니다.
- 차단 확인: A의 외부 HTTPS가 실패합니다. `ssh -J root@192.168.151.2 root@10.30.10.10` 키 SSH가 유지됩니다. 토폴로지 연결은 남고 차단 표시가 붙습니다.
- 해제: `장치 격리 해제`. 전용 차단 규칙을 제거하고 HTTPS와 기존 인터페이스·라우팅 상태를 비교합니다.

발표 멘트: “관리망에는 방화벽만 직접 연결되어 있습니다. 관리자는 방화벽을 거쳐 내부 노드에 접근합니다. A의 데이터만 차단하며 관리 예외를 남기는 방식입니다. 이 경우 링크 자체는 살아 있어서 그림에는 연결과 차단 표시가 함께 보입니다.”

```bash
python3 tests/labs/run-labs.py lab3 demo --scenario presentation --keep-active
```

## Lab 4 - FRR 경유 서비스망 격리 / 전용 필터 체인

- 프로젝트: `PRJ-LAB4`, 격리 대상 `Lab4-FRR`, 패킷 검사 노드 `Lab4-Ubuntu`.
- 상황: VLAN40 서비스망에서 의심스러운 외부 연결이 보고되어 라우터 경유 데이터를 차단한다고 가정합니다.
- 선택 범위: `장치 IPv4 통신 · 관리 유지`.
- 정상 확인: Ubuntu에서 `https://example.com` HTTP 200과 FRR 경유 전달을 확인합니다.
- 적용: FRR 호스트의 전용 iptables 체인을 INPUT/FORWARD/OUTPUT 앞에 연결합니다. 인터페이스·기본 경로·NAT는 내리지 않습니다.
- 차단 확인: Ubuntu의 외부 HTTPS는 실패하고 `root@192.168.152.1` 관리 SSH는 유지됩니다. 토폴로지는 관측 경로를 유지하며 차단 상태를 표시합니다.
- 해제: `장치 격리 해제`. 전용 규칙을 제거하고 원래 경로·인터페이스·필터·NAT 규칙 순서와 HTTPS 복구를 비교합니다.
- 확장 질문: OVS VLAN40도 별도 Q01 시험에서 소유 cookie의 OpenFlow 규칙 적용·제거를 검증했습니다. 이번 P01은 FRR을 보여주는 시연입니다.

발표 멘트: “처음에는 포트를 내렸을 때 기본 경로가 사라져 복구가 불안정했습니다. 지금은 데이터 차단만 담당하는 전용 체인을 사용합니다. 관리 접속과 원래 경로를 유지하고, 해제 후 규칙 순서까지 비교합니다.”

```bash
python3 tests/labs/run-labs.py lab4 demo --scenario presentation --keep-active
```

## 발표 당일 복구와 예비 자료

장비 ACK가 늦으면 적용 확인 대기 상태를 그대로 설명하고 기다립니다. 버튼을 반복해서 누르거나 성공이라고 말하지 않습니다. 실패하면 `log.html`과 `evidence/isolation.json`의 현재 요청을 확인하고 해당 노드의 해제 상태를 확인합니다. 수집선은 관측 정보이며 임의의 통신 성공 증명이 아닙니다.

라이브 시연이 불가능하면 PPT의 해당 Lab 영상 또는 증적 묶음의 원본 WebM을 재생합니다. 녹화는 실제 리허설이며 영상 아래의 단계 자막은 Robot이 붙인 설명입니다. 실서비스 공격 탐지 화면으로 소개하지 않습니다. 5분 대본과 상세 데모를 별개로 사용합니다.

시나리오 본체는 `tests/robot/suites/presentation_demo.robot`, 장비·패킷 확인은 기존 `LabEvidence.py`, 기동은 기존 `run-labs.py`가 담당합니다. 별도 배포 도구나 상시 프로세스를 추가하지 않았습니다. 일반 자동 정책 적용은 켜지 않으며 모의 AI와도 독립된 수동 운영자 격리 시연입니다.
