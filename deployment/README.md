# deployment — 랩 배포·검증 도구

SonarValidator Agent(Prober)를 **GNS3 랩에 배포하고 검증하는 스크립트** 모음입니다.

> 이 폴더는 저장소 루트에 있습니다.
> 이전에는 `SonarValidator_Prober/components/parser/tools/` 에 있었는데,
> **파서(parser) 계층이 배포 도구를 들고 있는 것이 성격에 맞지 않아** 분리했습니다.

---

## 1. 왜 네트워크별로 나누는가

랩이 **두 개**이고, 접속 방식이 완전히 다릅니다.

| 랩 | 관리망 | 라우터 | 스위치 | 엔드포인트 | 배포 방식 |
| --- | --- | --- | --- | --- | --- |
| **D-AI-PBL-PoC** | `172.16.255.0/24` | Alpine + FRR (QEMU) | Open vSwitch 컨테이너 | Alpine 컨테이너 / QEMU | SSH + `docker exec` + 콘솔 |
| **Real-to-Virtual (RVI)** | `10.20.0.0/24` | Cisco Catalyst8000V | Arista vEOS | Ubuntu 24 + OPNsense | SSH(비밀번호) + GNS3 콘솔 |

도구 하나가 두 랩을 모두 처리하려 하면 **주소·계정·전송 방식이 뒤섞여**
"왜 배포가 조용히 실패했는지" 를 찾기 어려워집니다.
(실측: 번들 `server_ip` 가 이전 랩 값으로 나가도 오류 없이 200 을 반환했습니다)

그래서 **네트워크마다 폴더를 두고**, 공통 도구만 `_shared/` 에 둡니다.

---

## 2. 구조

```
deployment/
├── README.md                  ← 이 문서
├── _shared/                   네트워크 무관 (GNS3 공통)
│   ├── gns3_console.py            GNS3 콘솔(telnet) 접속 헬퍼
│   └── ws_collector.py            WebSocket 수신 검증기 (표준 라이브러리만)
├── d-ai-pbl-poc/              D-AI-PBL-PoC 랩 (172.16.255.0/24)
│   ├── PoC_Lab_Deployment.md      배포 가이드 (여기서 시작)
│   ├── deploy_poc_lab.sh          라우터 5대 (SSH) 배포 + 재시작
│   ├── deploy_containers.sh       컨테이너 스위치·VM·방화벽 배포
│   ├── deploy_vm.sh               QEMU VM 용 (콘솔에서 실행)
│   ├── restart_in_container.sh    컨테이너 프로버 재시작 (SIGTERM 우선)
│   ├── frr_shell.py               FRR 콘솔에서 vtysh 탈출 후 셸 명령
│   ├── vmrun.py                   QEMU VM 콘솔 로그인 + 명령 실행
│   └── dump_gns3_nodes.py         GNS3 프로젝트에서 노드→콘솔 포트 추출
└── real-to-virtual/           RVI 랩 (10.20.0.0/24)
    ├── deploy_cisco.py            Cisco 8000v (guestshell, dohost 경유)
    ├── deploy_arista.sh           Arista vEOS (sftp, 키 인증)
    ├── deploy_arista_rvi.py       Arista vEOS (비밀번호 인증, base64 전송)
    ├── node_probe.py              노드 조회 명령 실행 + 파서 검증
    ├── run_prober.sh              Arista 용 실행 래퍼 (원격 배치)
    └── run_prober_linux.sh        Ubuntu VM 용 실행 래퍼 (원격 배치)
```

---

## 3. ⚠️ 자격증명 취급

이 저장소는 **public** 입니다. 스크립트는 비밀번호를 **담지 않고**
환경변수로 받습니다.

```bash
export SONAR_CISCO_PW='...'       # Cisco 8000v
export SONAR_OPNSENSE_PW='...'    # OPNsense root
export ARISTA_PASSWORD='...'      # Arista admin (deploy_arista_rvi.py)
export SONAR_UBUNTU_PW='...'      # Ubuntu VM
```

랩 접속 정보(주소·계정·포트)는
`Poc용 네트워크 Real-to-Virtual/OPNSense_Credential.md` 를 참고하세요.
(그 문서도 평문 비밀번호는 담지 않습니다)

---

## 4. 새 네트워크를 추가할 때

1. `deployment/<네트워크-이름>/` 폴더를 만든다.
2. 그 안에 배포 스크립트와 **배포 가이드 md** 를 둔다.
3. 주소·계정을 다른 네트워크에서 복사하지 않는다.
   (하드코딩된 기본값은 랩이 바뀌면 **조용히 잘못된 값**을 배포합니다)
4. GNS3 콘솔·WebSocket 검증처럼 재사용할 도구는 `_shared/` 에 둔다.

> 스크립트가 저장소 안의 빌드 산출물(`SonarValidator_Prober/build_static/…`)을
> 참조한다면, **이 폴더 기준 상대경로**로 계산하세요.
> 예: `deployment/real-to-virtual/` → `../../SonarValidator_Prober`

---

## 5. 관련 문서

| 문서 | 내용 |
| --- | --- |
| [`d-ai-pbl-poc/PoC_Lab_Deployment.md`](d-ai-pbl-poc/PoC_Lab_Deployment.md) | D-AI-PBL 랩 배포 절차 (18대 실검증) |
| [RVI 통합 테스트 계획](https://shseo2023.atlassian.net/wiki/spaces/SONAR/pages/1867833) | RVI 랩 검증 계획 |
| [RVI 통합 테스트 결과](https://shseo2023.atlassian.net/wiki/spaces/SONAR/pages/1867859) | RVI 랩 실측 결과 |
| [`docs/Agent/Appendix_SIGTERM_Hang_Analysis.md`](../docs/Agent/Appendix_SIGTERM_Hang_Analysis.md) | SIGTERM 미종료 결함 분석 |