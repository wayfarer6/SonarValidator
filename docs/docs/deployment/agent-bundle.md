# Prober 번들 다운로드와 SSH 배포

## 서버 설정의 소유자

프로젝트 편집의 **관리 서버 설정** 모달에서 IP 또는 호스트명과 포트를 저장합니다. 새 번들은 등록된 Agent의 소속 프로젝트에서 주소·포트·관리 대역을 읽습니다. 다운로드 화면과 생성 마법사는 저장된 값을 읽기만 합니다. 서버 설정이 없으면 localhost나 다른 Lab 주소로 대체하지 않고 다운로드를 거부합니다. 기존 Prober는 새 번들을 배포하고 재시작해야 주소가 바뀝니다.

Agent 목록의 프로젝트 열은 이름과 식별자를 표시합니다. 이름을 클릭하면 해당 프로젝트 편집으로 이동합니다. 별도의 행별 이동 버튼을 추가하지 않습니다.

## 다운로드

1. 프로젝트에서 Add Agent를 열고 장비 유형을 선택합니다.
2. 신규 Agent는 이름과 관리 IP로 등록합니다. 재배포는 기존 이름과 유형을 사용합니다.
3. Agent 다운로드로 `.tar.gz`를 받습니다. 같은 이름을 다른 프로젝트에 등록해 소속을 덮어쓸 수 없습니다.

API 미리보기는 `GET /api/v1/agents/download/info?agent_id=ID&project_id=PROJECT`이며 실제 파일은 `GET /api/v1/agents/download/ID?project_id=PROJECT`입니다. node_type은 생략하면 등록된 유형을 사용하고, 다른 유형을 요청하면 거부합니다. server_ip/server_port 다운로드 파라미터는 지원하지 않습니다. 주소 수정은 프로젝트 API PUT으로만 합니다.

번들에는 Installer/ 아래 실행 파일·default.conf·SQLite 템플릿·설치 안내와 사용 가능한 init 스크립트가 들어갑니다. 실행 파일과 템플릿이 없으면 불완전한 파일을 만들지 않습니다. default.conf 권한은 0600이고 인증 비밀값이 포함되어 있으므로 공개·녹화하지 않습니다. 화면 설정 미리보기에는 공개 연결 값만 표시합니다.

## 장비로 전송

WSL에서 기존 키를 사용합니다. ProxyJump가 필요한 Lab3는 방화벽을 경유합니다. 개인키를 장비로 복사하지 않습니다.

```bash
ssh -o BatchMode=yes -p 2222 guestshell@192.168.148.181 'umask 077; cat > sonar-agent.tar.gz' < sonar-agent-Lab1-R1.tar.gz
# 직접 SSH가 막힌 Lab3
ssh -o BatchMode=yes -J root@192.168.151.2 root@10.30.10.10 'umask 077; cat > sonar-agent.tar.gz' < sonar-agent-Lab3-Ubuntu-A.tar.gz
```

전송 전후 SHA-256을 비교합니다. 기존 Prober에 SIGTERM을 보내고 종료를 기다린 후 바이너리와 설정을 교체합니다. 기존 DB를 삭제하거나 동일 Agent를 중복 실행하지 않습니다. 장비별 영구 경로·EEM·부팅 훅은 [Lab별 검증](../testing/labs-1-4.md)의 inventory와 시작 스크립트를 따릅니다. 일반 systemd/OpenRC 신규 설치는 번들의 Installer.sh를 사용합니다.

설치 경로 안에 .next 파일을 복사하고 권한을 설정한 뒤 rename으로 교체합니다. sync 완료 후 재시작하여 VM 중지 전에 파일 저장을 마칩니다. EOS는 admin SSH에서 허용된 sudo -n으로 설치 경로와 root Prober를 관리합니다.

## 실제 다운로드·전송 녹화

Robot `agent_deployment.robot`는 Lab별 세 시나리오를 제공합니다.

- D01: 각 Prober를 실제 UI에서 다운로드, 설정·권한·파일명 검사, 원본 번들 SSH 전송과 원격 해시 비교, 기존 시작 훅으로 갱신, 온라인·새 수집·4스레드·터미널 잔여 작업 확인.
- D02: Agent 소속 링크, 프로젝트 편집 모달, 잘못된 포트 거부, 다운로드 화면의 읽기 전용 주소, API 주소 덮어쓰기 거부 확인. 실제 Lab의 주소와 포트는 원래 값으로 저장합니다.

- D03: GNS3 노드를 중지·시작한 뒤 바이너리·설정·템플릿 해시와 재부팅 이후 새 수집, 자동 실행을 확인합니다.

```bash
SONAR_UI_PASSWORD=... SONAR_HEADLESS=true python3 tests/labs/run-labs.py all demo --scenario deployment
```

SSH 단계는 Robot이 **실제 WSL SSH 명령과 결과를 보여 주는 증적 화면**으로 녹화합니다. 제품에 원격 업로드 기능을 추가한 것은 아닙니다. 원본 다운로드는 설정을 다시 쓰거나 별도 API로 대체하지 않고 그대로 전송합니다. 인증 정보가 있는 원본 tar.gz는 종료 시 지우고 공개 설정·파일 해시·검사 결과만 남깁니다.
