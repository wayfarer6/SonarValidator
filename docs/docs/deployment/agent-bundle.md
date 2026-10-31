# Agent 번들

기준: Backend `AgentBundleController`, `AgentBundleService`, Prober `Installer/Installer.sh`.

1. POST `/api/v1/agents/expected`: agent_id, project_id, device_type, node_type, expected_ip를 등록합니다.
2. GET `/api/v1/agents/download/info`: agent_id, node_type, server_ip, server_port로 자산과 주소를 확인합니다.
3. GET `/api/v1/agents/download/{agentId}`: 설정이 채워진 tar.gz를 받습니다.

expected_ip는 장비 주소입니다. server_ip는 Prober가 연결할 관리 서버 주소입니다. 둘을 같은 입력으로 취급하지 않습니다.
번들은 Installer/ 디렉터리 아래 실행 파일, default.conf, SQLite 템플릿, 설치기와 서비스 자산을 담습니다.
생성에는 32자 이상 터미널 공유 시크릿과 스테이징 실행 파일이 필요합니다.

default.conf에는 프로젝트 관리 대역을 MANAGEMENT_PREFIX로 포함합니다. 생성된 주소·포트·대역이 사이트 설정과 일치하는지 확인합니다.
실행 파일이 현재 소스인지, 대상 OS에서 실행되는지 별도로 확인합니다. 오래된 Installer 바이너리를 무조건 재사용하지 않습니다.

표준 설치기는 root로 실행하며 Cisco 프로필은 guestshell 계정의 systemd 서비스를 설치합니다.
이번 Lab 1은 R1 sudo/PAM 문제를 발견해 홈 경로와 EEM 실행 유지 방식을 사용합니다. [실제 배포 절차](lab1.md)를 따릅니다.
