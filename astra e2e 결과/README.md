# Astra E2E 결과

실행 환경: Management-Console `172.16.255.245`, 실제 Frontend `http://172.16.255.245`와 Backend `:3000`. 2026-10-09 KST 기준. 실제 장비 수집값, API 응답, Playwright 브라우저 조작 및 SSH 조회를 교차 검증했다.

## 최종 결과

요청한 **라우터 5대, 스위치 5대, Alpine 방화벽 1대**에 SFTP로 에이전트를 배포했다. 노드별 이름·유형·서버 주소·관리망 설정과 백엔드 터미널 시크릿 일치를 검증했고, 11대의 프론트엔드 터미널에서 `id`, `hostname`, 완료 마커 실행을 확인했다. `.106`은 사용자 지시에 따라 제외했다.

Manage와 Subnet Advance Configuration에 **VLAN 9종 / 장비별 14개 VLAN 항목**이 표시된다. 이름은 수집값 또는 `VLAN번호`로 생성되고 CSO는 미분류로 시작한다. 사람이 이름과 CSO를 편집·저장한 후 새로고침해도 유지된다. 기존 프로젝트는 저장된 서브넷 11개에 VLAN 초안 14개를 합쳐 편집 화면에 25개 항목을 표시한다.

**방화벽에는 에이전트만 배포했다.** 사용자 지시에 따라 VLAN 131~133 인터페이스를 복구하지 않았다. 배포 전후 `nft list ruleset`, IPv4 주소, 라우팅 출력이 동일하다.

[모든 단계의 캡처 전체 보기](%ED%99%94%EB%A9%B4-%EC%A0%84%EC%B2%B4.md)

최종 Agent 화면: 배포 대상 11대와 기존 test-vm이 함께 표시된다. 기존 test-vm은 이번 배포 대상이 아니다.

![최종 Agent 화면: 배포 대상 11대와 기존 test-vm이 함께 표시된다. 기존 test-vm은 이번 배포 대상이 아니다.](10-Firewall-%EB%B0%B0%ED%8F%AC/01-Firewall-%ED%8F%AC%ED%95%A8-Agent-%EC%B5%9C%EC%A2%85.png)

## 장비별 배포·수집

| 주소 | 이름 | 유형 | 확인 결과 |
| --- | --- | --- | --- |
| 172.16.255.1 | Gateway-Router | Router / FRR | 서비스·수집·터미널 통과 |
| 172.16.255.3 | DMZ-Router | Router / FRR | VLAN 141, 서비스·수집·터미널 통과 |
| 172.16.255.4 | C4I-Network-Router | Router / FRR | 서비스·수집·터미널 통과 |
| 172.16.255.5 | Survillance-Network-Router | Router / FRR | VLAN 111·112, 서비스·수집·터미널 통과 |
| 172.16.255.6 | VDI-Router | Router / FRR | VLAN 121·122, 서비스·수집·터미널 통과 |
| 172.16.255.101 | Switch-0 | Switch / Open vSwitch | VLAN 10, access/trunk 대조·터미널 통과 |
| 172.16.255.102 | Switch-1 | Switch / Open vSwitch | VLAN 111·112, access/trunk 대조·터미널 통과 |
| 172.16.255.103 | Switch-2 | Switch / Open vSwitch | VLAN 121·122, access/trunk 대조·터미널 통과 |
| 172.16.255.104 | Switch-3 | Switch / Open vSwitch | VLAN 131·132·133, access/trunk 대조·터미널 통과 |
| 172.16.255.105 | Switch-4 | Switch / Open vSwitch | VLAN 141, access/trunk 대조·터미널 통과 |
| 172.16.255.2 | Firewall | Firewall / Alpine nftables | 인터페이스 8·경로 3·규칙 5, 터미널 통과 |

공통 실행 파일 SHA-256: `347e5cecd9821db24f2cac99baa908da52ce1195ddcfd387b3dbb867fc42deed`.

라우터는 OpenRC 서비스로 실행한다. 스위치와 방화벽은 기존 컨테이너의 `/root/start.sh`에서 supervisor를 시작하도록 연결했다. supervisor는 에이전트 종료 후 재실행한다. 콜드 부팅 검증은 하지 않았다. 노드별 기존 파일은 `/root/sonar-backup-날짜-시간`에 보관했다.

설정은 `SERVER_IP=172.16.255.245`, `SERVER_PORT=3000`, `MANAGEMENT_PREFIX=172.16.255.0/24`와 각 노드의 정확한 `NODE_TYPE`, `AGENT_NAME`을 사용한다. 시크릿 원문은 보고서에 넣지 않고 일치 여부만 기록했다. 설정에 HTML 엔티티가 없음을 확인했다.

- [11대 설정·시크릿 일치 검증](06-%EC%B5%9C%EC%A2%85-%EA%B2%80%EC%A6%9D/config-audit.json)
- [라우터 실제 주소·경로와 API 대조](06-%EC%B5%9C%EC%A2%85-%EA%B2%80%EC%A6%9D/router-verification.json)
- [OVS 실제 VLAN·포트와 API 대조](06-%EC%B5%9C%EC%A2%85-%EA%B2%80%EC%A6%9D/switch-verification.json)
- [라우터·스위치 3분간 반복 수집](06-%EC%B5%9C%EC%A2%85-%EA%B2%80%EC%A6%9D/telemetry-continuous.json)
- [최종 백엔드 재배포 후 11대 수집 시각 갱신](10-Firewall-%EB%B0%B0%ED%8F%AC/all-agents-refresh.json)
- [라우터·스위치 10대 터미널 실행 결과](05-%ED%84%B0%EB%AF%B8%EB%84%90-%EA%B2%80%EC%A6%9D/all-results.json)
- [방화벽 터미널 실행 결과](05-%ED%84%B0%EB%AF%B8%EB%84%90-%EA%B2%80%EC%A6%9D/Firewall-results.json)

## VLAN 수집과 사용자 CSO 편집

Switch-3의 실제 OVS 설정에는 access VLAN 131·132·133과 uplink trunk 131·132·133이 있다. Prober가 `vlan_status`와 `trunk_status`를 보냈고 Backend의 정규화 결과에도 존재했다. 문제는 IP 주소가 없는 L2 VLAN을 서브넷 초안에서 제외하던 처리와 두 편집 화면의 서로 다른 로딩 경로였다.

프로젝트에 등록된 장비의 설정을 사용하는 `/api/v1/projects/{projectId}/editing`을 추가하고 두 화면이 같은 편집 데이터를 사용하도록 수정했다. VLAN ID를 저장 모델까지 전달하고, CIDR이 없는 L2 VLAN도 편집 항목으로 유지한다. 다른 장비의 동일 VLAN 번호만 보고 IP 대역을 복사하지 않는다. 예를 들어 Switch-1 VLAN111은 L2 정보로 남고 Survillance-Network-Router VLAN111에서는 실제 `10.20.111.0/24`가 표시된다.

[Switch-3가 백엔드로 보낸 실제 VLAN·trunk payload](06-%EC%B5%9C%EC%A2%85-%EA%B2%80%EC%A6%9D/vlan-wire-example.json)

실제 프로젝트: VLAN131 자동 이름과 미분류 CSO, IP 대역 미수집 표시.

![실제 프로젝트: VLAN131 자동 이름과 미분류 CSO, IP 대역 미수집 표시.](09-VLAN-CSO-%EC%B5%9C%EC%A2%85/01-%EC%8B%A4%EC%A0%9C%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8-VLAN131-%EB%AF%B8%EB%B6%84%EB%A5%98.png)
Manage에서도 수집된 VLAN 초안과 이름·CSO 편집을 제공한다.

![Manage에서도 수집된 VLAN 초안과 이름·CSO 편집을 제공한다.](09-VLAN-CSO-%EC%B5%9C%EC%A2%85/02-Manage-VLAN%ED%8E%B8%EC%A7%91.png)

저장 테스트는 실제 장비에 정책이 연결되지 않은 별도 E2E 프로젝트 `PRJ-4B6C7538`의 fixture 행으로 수행했다. `Astra VLAN131 업무망` / `Sensitive`로 저장한 뒤 새로고침하여 유지되는 것을 확인했다. 이후 `VLAN131` / 미분류로 복원했다. 실제 프로젝트 `PRJ-D488B7A4`에 사용자가 저장한 이름·CSO는 덮어쓰지 않았다.

격리된 E2E fixture: 이름과 Sensitive 저장 후 새로고침 결과. 위 수집 표가 0인 이유는 테스트 프로젝트에 실제 장비를 배정하지 않았기 때문이다.

![격리된 E2E fixture: 이름과 Sensitive 저장 후 새로고침 결과. 위 수집 표가 0인 이유는 테스트 프로젝트에 실제 장비를 배정하지 않았기 때문이다.](09-VLAN-CSO-%EC%B5%9C%EC%A2%85/03-%EC%9D%B4%EB%A6%84-CSO-%EC%A0%80%EC%9E%A5-%EC%83%88%EB%A1%9C%EA%B3%A0%EC%B9%A8.png)
미분류로 복원한 뒤 IP 대역·CSO가 없으면 검증 완료로 판정하지 않는다.

![미분류로 복원한 뒤 IP 대역·CSO가 없으면 검증 완료로 판정하지 않는다.](09-VLAN-CSO-%EC%B5%9C%EC%A2%85/04-Manage-%EB%AF%B8%EB%B6%84%EB%A5%98%EB%B3%B5%EC%9B%90-%EA%B2%80%EC%A6%9D%EB%B3%B4%EB%A5%98.png)
[브라우저 편집·저장·복원 assertion 결과](09-VLAN-CSO-%EC%B5%9C%EC%A2%85/result.json)


## 터미널·수집 중단 수정

Frontend의 API 기본 주소가 빈 문자열인 배포 환경에서 WebSocket URL을 만들 때 `Invalid URL`이 발생했다. 현재 페이지 origin을 기준으로 URL을 만들도록 수정하고 백엔드 허용 origin에 실제 프론트 주소를 반영했다. 노드별 시크릿도 백엔드와 일치시켰다.

라우터는 root의 로그인 프로필에서 `vtysh`를 실행한다. 따라서 처음 보이는 FRR 프롬프트는 root 권한 오류가 아니다. `show ip route`로 조회한 뒤 `exit`으로 shell에 들어가 `uid=0(root)`와 hostname을 확인했다.

Prober의 동기 WebSocket 읽기가 응답 없는 상태에서 계속 기다려 다음 텔레메트리 주기를 막던 문제를 수정했다. 비동기 읽기를 주기 사이에 유지해 부분 프레임을 보존하고 종료·재접속 때 취소한다. 실장비 10대에서 3분 동안 각각 5~6회 수집됐고, 최종 방화벽 포함 검증에서도 수집 시간이 갱신됐다.

Gateway-Router: FRR 경로 조회 후 root shell 명령 실행.

![Gateway-Router: FRR 경로 조회 후 root shell 명령 실행.](05-%ED%84%B0%EB%AF%B8%EB%84%90-%EA%B2%80%EC%A6%9D/Gateway-Router.png)
Alpine Firewall: 실제 프론트엔드 터미널에서 root·hostname·완료 마커 확인.

![Alpine Firewall: 실제 프론트엔드 터미널에서 root·hostname·완료 마커 확인.](05-%ED%84%B0%EB%AF%B8%EB%84%90-%EA%B2%80%EC%A6%9D/Firewall.png)

## Alpine 방화벽 배포 범위

Alpine 방화벽은 SFTP로 동일한 정적 실행 파일, DB 템플릿, Firewall 전용 설정을 배포했다. bash가 없어 터미널에 필요한 패키지를 설치했다. 장비의 패키지 저장소 DNS 조회가 실패하여 Management-Console에서 Alpine 3.24 패키지를 받은 뒤 SFTP로 전달해 로컬 설치했다. DNS·VLAN·라우팅·nft 규칙을 수정하지 않았다.

배포 전 코드 확인에서 정책 미배정 장비에도 예제 기본 명령이 전송되는 것을 발견했다. 방화벽 예제에는 drop 체인이 있어, 미배정 장비의 응답을 빈 `policies: []`로 바꿨다. 배포된 백엔드에 실제 policy-request를 보내 빈 배열을 확인한 뒤 방화벽 에이전트를 실행했다. 명시적 격리 정책은 유지되며 회귀 테스트를 통과했다.

`/etc/network/interfaces`에는 VLAN 131~133 선언이 있지만 런타임에 해당 인터페이스가 없다. 사용자 요청대로 복구하지 않았으며, 방화벽 수집값의 VLAN 0개는 현재 실행 상태와 일치한다. Switch-3의 VLAN131~133 L2 수집은 정상이다.

- [빈 정책 응답 (후속 CSO 테스트에서 external-enforcement 모드로 재확인된 스냅샷)](10-Firewall-%EB%B0%B0%ED%8F%AC/policy-before-start.json)
- [방화벽 배포 검증 결과](10-Firewall-%EB%B0%B0%ED%8F%AC/verification.json)
- [배포 전 nft·주소·경로](06-%EC%B5%9C%EC%A2%85-%EA%B2%80%EC%A6%9D/Firewall-before.txt)
- [배포 후 nft·주소·경로](10-Firewall-%EB%B0%B0%ED%8F%AC/Firewall-after.txt)
- [백엔드가 파싱한 AlpineFirewall 설정](10-Firewall-%EB%B0%B0%ED%8F%AC/Firewall-config.json)

## 자동 검사와 최종 API

| 검사 | 결과 |
| --- | --- |
| Backend 전체 테스트 | 358개 통과, 실패·오류·스킵 0 |
| Prober 통신 회귀·통합 테스트 | 3개 통과, 로컬 mock 서버 연결로 실제 실행 |
| Frontend 단위 테스트 | 3개 통과 |
| Frontend TypeScript·production build | 통과 |
| Frontend lint | 오류 0, 기존 경고 7 |
| Docker backend/frontend build·배포 | 완료 |
| 프론트엔드 실제 VLAN 편집 E2E | 저장·새로고침·미분류 복원 통과, page error 0 |
| 최종 Agent·편집·Firewall config API | HTTP 200 |
| 최종 Log API | HTTP 200, 현재 결과 0건 |
| git diff --check | 통과 |

전체 회귀 검사 중 예제 기본 정책 1개를 기대하던 기존 테스트가 실패했다. 새 동작인 미배정 장비의 빈 정책을 검증하도록 바꾼 후 전체 테스트를 다시 실행해 통과했다. 처음 로그 화면에서 보였던 HTTP 500은 최신 백엔드 배포 후 재검사에서 재현되지 않았다. 로그 조회 결과가 비어 있어 실제 로그 유입·분석까지 통과했다고 간주하지 않는다.

- [Backend 전체 테스트 결과](%EC%A6%9D%EA%B1%B0/backend-tests.json)
- [C++ 통신 테스트](%EC%A6%9D%EA%B1%B0/communication-tests.txt)
- [Frontend 테스트](%EC%A6%9D%EA%B1%B0/frontend-tests.txt)
- [Frontend lint](%EC%A6%9D%EA%B1%B0/frontend-lint.txt)
- [최종 API·브라우저 결과](10-Firewall-%EB%B0%B0%ED%8F%AC/final-readonly.json)

최신 백엔드 배포 후 로그 화면 재확인: HTTP 200, 결과 없음.

![최신 백엔드 배포 후 로그 화면 재확인: HTTP 200, 결과 없음.](10-Firewall-%EB%B0%B0%ED%8F%AC/04-%EC%B5%9C%EC%A2%85-%EB%A1%9C%EA%B7%B8.png)

## 남은 범위와 주의해서 해석할 항목

- 실제 패킷 송수신에 대한 CSO 분리·차단 정책 적용 검증은 하지 않았다. 이번 통과 범위는 배포, 수집, 화면 편집·저장, 터미널이다. 규칙 0개인 초기 테스트 프로젝트의 과거 compliant 결과는 망분리 성공 증거가 아니다.
- 기존 `test-vm`은 이전 실행 파일로 동작하며 반복 수집이 멈춘 것이 관찰됐다. 이번에 요청받은 11대와 구분한다.
- Agent 화면의 마지막 수신 표시는 등록 시각을 사용하는 기존 동작이 있어, 반복 수집 여부는 Backend `last_seen`과 수신 로그로 판정했다.
- 방화벽 `/etc/os-release`는 심볼릭 링크 대신 링크 경로가 담긴 일반 파일이다. 배포판 표시가 Unknown일 수 있으나 nftables 제품 탐지와 AlpineFirewall 파싱은 정상이다. 에이전트 배포 범위에 맞춰 이 OS 파일은 변경하지 않았다.
- 컨테이너/장비 콜드 재부팅 후 자동 실행은 아직 테스트하지 않았다.
- 자동 수집 VLAN 초안은 사람이 이름·CIDR·CSO를 확인하고 저장해야 한다. L2 VLAN 번호만으로 CIDR을 만들어 넣지 않는다.

## 증거 폴더 안내

`01`~`04`, `07`~`08`은 진행 중 또는 수정 전 화면이며, 당시의 실패도 남겨 두었다. `05`는 터미널, `06`은 실제 장비 대조, `09`는 VLAN/CSO 최종 편집 검증, `10`은 방화벽 포함 최종 상태다. `증거/telemetry-refresh.json`은 수정 전 수집 중단 증거이며 최종 결과는 `06`과 `10`을 기준으로 읽어야 한다.

참고한 문서에는 프로젝트 README, docs의 Agent·Backend·Frontend 설계와 운영 문서, PoC 랩 배포 및 Alpine 방화벽 문서가 포함된다. 주요 배포 근거는 `docs/blog/2026-09-26-poc-lab-deployment.md`, `2026-09-22-poc-network-firewall.md`, `2026-09-23-alpine-firewall-appliance.md`다.
