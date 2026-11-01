# Lab별 Robot Framework 테스트와 녹화

Browser 라이브러리(내부 Playwright)를 사용합니다. 각 테스트는 독립 브라우저 컨텍스트를 만들고, 종료할 때 시나리오별 WebM 영상을 확정합니다.

## 설치

```bash
python3 -m venv "$HOME/.local/share/sonarvalidator/robot-venv"
source "$HOME/.local/share/sonarvalidator/robot-venv/bin/activate"
pip install -r tests/robot/requirements.txt
export PATH="$HOME/.nvm/versions/node/v26.13.0/bin:$PATH"
rfbrowser init chromium
```

## 실행

```bash
./tests/robot/run.sh lab1 check
./tests/robot/run.sh lab1 demo
./tests/robot/run.sh lab1 demo 'S02*'
SONAR_HEADLESS=true ./tests/robot/run.sh lab1 demo
./tests/robot/run.sh all check
```

비밀번호는 실행 중 마스킹 입력하거나 `SONAR_UI_PASSWORD` 환경변수로 전달합니다. 테스트 코드·명령 인자·영상 자막에는 넣지 않습니다.
`demo`는 기본적으로 창을 표시하고 버튼 사이 2초, 브라우저 동작마다 400ms를 둡니다. `SONAR_STEP_PAUSE`, `SONAR_SLOW_MO`, `SONAR_HEADLESS`로 조절합니다.
`check`는 빠르게 실행합니다. 화면이 준비됐는지 확인하는 대기는 Browser 자동 대기이며, 발표용 멈춤과 별개입니다.

| 시나리오 | 확인 내용 | 변경 |
| --- | --- | --- |
| S01 | 프로젝트·장비 온라인·배포 서버 주소 | 조회 |
| S02 | 노드 연결 관리 모달·역할·CSO·허용 대상 드롭다운 | 실제 프로젝트에서는 취소 |
| S03 | 장비별 웹 터미널의 실제 echo 응답 | 상태를 바꾸지 않는 셸 명령 |
| S04 | 실제 대역과 자동 수집 CSO 미분류 | 조회 |
| S05 | 역할·허용 대상 저장과 새로고침 유지 | 임시 프로젝트·임시 Agent만 생성하고 삭제 |
| S06 | 활성 셸 SIGTERM·EEM 자동 복원 | 별도 shutdown suite; Prober 재시작 |
| S07 | BDD 입력 거부와 검증 범위 | 임시 프로젝트만 변경 후 삭제 |
| S08 | JSON 파일 다운로드 | 조회·로컬 다운로드 |
| S09 | 장비 확장 UML 3개 렌더링 | 문서 조회·SVG/PNG 캡처 |
| S10 | 동시성 소유권·종료 순서 다이어그램 | 문서 조회·SVG/PNG 캡처 |
| S11 | 노드별 주기 변경·실제 수집·적용 ACK | Lab1 수집 주기만 변경 후 원상복원 |
| S12 | 오프라인 저장·입력 유효성·재조회·인증 | 임시 프로젝트·노드 생성 후 삭제 |
| S13 | 수집 설정 저장·전달·확인 시퀀스 | 문서 조회·SVG/PNG 캡처 |

정책 푸시·격리·실제 장비 네트워크 변경은 실행하지 않습니다. S11은 Lab1 수집 주기를 저장하되 FINALLY에서 원래 값으로 복원합니다. 연결 정책의 저장·삭제 검사는 임시 프로젝트에서 수행합니다.

## Lab 설정과 결과

`labs/lab1.resource`는 현재 Lab1 프로젝트를 사용합니다. Lab2~4는 `SONAR_LAB2_PROJECT_ID` 등으로 프로그램 프로젝트를 먼저 지정해야 합니다.
프로젝트가 없는 Lab은 **SKIP**으로 보고하며 PASS로 세지 않습니다. 장비 이름과 기대 대역은 해당 resource의 목록을 채웁니다.

결과는 `test-results/robot/<실행ID>/<Lab>/`에 저장하며 Git에서 제외합니다.
`report.html`은 시험 요약, `log.html`은 단계별 로그, `screenshots/`는 캡처, `videos/<Lab>/<시나리오>/`는 개별 영상입니다.
`ROBOT_OUTPUT_DIR`로 출력 위치를 바꿀 수 있습니다. 인증 상태 파일은 종료 때 제거합니다.

공식 기준: [Browser 설치](https://robotframework-browser.org/docs/start/installation/), [컨텍스트·녹화](https://robotframework-browser.org/docs/concepts/browser-context-page/).

## Prober 종료 회귀

새 실행 파일 배포 후 `SONAR_TERMINAL_CYCLES=10 SONAR_TERMINAL_STRESS=true ./tests/robot/run.sh lab1 check 'S03*'`로 장비별 10회 연결·종료를 반복합니다. 스트레스 모드는 HUP/TERM을 무시하는 `sonar-terminal-job` 임시 작업을 셸에서 실행한 뒤 모달을 닫습니다. 실행 후 SSH로 해당 작업이 남지 않았는지, Prober FD·스레드 수가 유지되는지 확인합니다. 일반 S03은 배경 작업을 둔 상태의 `exit`와 셸 종료 상태도 확인합니다. 셸만 먼저 종료되고 PTY가 열려 있는 조건의 회귀입니다.

`./tests/robot/run.sh lab1 check shutdown`은 실제 터미널에서 임시 작업을 실행하고 Prober에 SIGTERM을 보냅니다. 작업이 남지 않고 10초 안에 종료되는지, 다음 EEM 주기에 자동 복원되는지 확인합니다. Lab1의 두 Prober가 잠시 재연결되므로 일반 조회 시험과 구분해 실행합니다.

## BDD 검증 범위 시나리오

`./tests/robot/run.sh lab1 demo "S07*"`는 임시 프로젝트에서 잘못된 CIDR 거부·강제 푸시 버튼 비활성화와 정상 모델의 검증 범위 표시를 확인합니다. 실제 장비 정책을 전송하지 않으며 임시 프로젝트는 종료 시 삭제합니다. 두 화면의 스크린샷과 시나리오 영상이 각각 저장됩니다.

S08은 Export 화면의 단일 파일 출력 버튼을 눌러 실제 JSON 다운로드를 읽고 프로젝트·검증 범위·서브넷 수가 보존되는지 검사합니다. `./tests/robot/run.sh lab1 demo "S08*"`로 개별 녹화할 수 있습니다.

S09는 현재 장비 확장 UML 문서의 Mermaid 3개가 실제로 렌더링되는지 확인하고 SVG·화면·영상을 저장합니다. 문서 배포 빌드를 로컬 3001 포트에 제공하거나 `SONAR_DOCS_URL`을 지정합니다.

S10은 `prober-runtime`의 worker 소유권·중지 요청·join·DB 종료 다이어그램과 Backend 전송 잠금 설명을 확인합니다. `./tests/robot/run.sh lab1 demo "S10*"`으로 개별 녹화합니다.

## Arista 직접 CLI 검증

`arista_cli.robot`은 UI 로그인 없이 SSH와 오프라인 Prober만 사용합니다. Lab2의 `/mnt/flash/sonar-cli-check`에 검증할 prober, default.conf, template.sqlite를 임시 준비한 뒤 실행합니다. 이 경로는 상시 설치 경로가 아니며 검증 뒤 제거합니다.

```bash
robot --outputdir test-results/robot/arista tests/robot/suites/arista_cli.robot
```

`SONAR_ARISTA_SSH`, `SONAR_ARISTA_TEST_DIR`, `SONAR_ARISTA_VERSION`, `SONAR_ARISTA_EXPORT_RUNS`로 대상을 지정합니다. 버전은 장비 이름이 아닌 show version 기준입니다. 기본값은 Lab2의 실제 EOS 4.30.2F입니다. A01은 명령 오류 코드, A02는 기본 20회/100개 수집 명령, A03은 약 120KB 출력의 마지막 포트와 페이징, A04는 사용 중이지 않은 VLAN3999의 생성·조회·삭제를 검사합니다. A04는 running config를 잠시 변경하므로 테스트 VLAN이 없는지 먼저 검사하며 write memory는 실행하지 않습니다. 상시 Agent 등록·정책 배포·이벤트 핸들러 설치는 수행하지 않습니다. 실행 로그를 기록하는 CLI 검사이며 Browser 영상 녹화 대상은 아닙니다.

## 노드별 수집 설정 시나리오

S11은 Lab1 R1/R2를 UI에서 각각 2초·4초로 바꾸고 Backend의 적용 상태와 실제 텔레메트리를 확인한 뒤 원래 주기로 복원합니다. S12는 임시 오프라인 노드의 1분 설정 저장·새로고침 유지·잘못된 소수/0 입력·인증을 검사하고 fixture를 삭제합니다. 노드 카드의 `노드 설정` 모달에서 수집 설정과 연결 관리를 전환합니다.
S13은 현재 수집 설정 프로토콜의 Mermaid 시퀀스를 렌더링하고 SVG·PNG·시나리오 영상을 저장합니다. `./tests/robot/run.sh lab1 demo "S13*"`으로 개별 실행합니다.

## AI 모의 공급자 검증

`node tests/robot/fixtures/ai-provider.mjs`를 켠 뒤 `tests/robot/run.sh lab1 demo ai`를 실행합니다. S14 실제 Prober 경로 조회, S15 변경 미리보기, S16 미지원 명령 차단을 검증합니다. 임시 공급자는 teardown에서 제거합니다. 설정 변경은 실제 장비에서 실행하지 않습니다. API 클라이언트 회귀 검사는 `node --test tests/frontend/api-client.test.mjs`입니다.

## 네 Lab의 주요 기능과 발표 녹화

`tests/robot/run.sh lab2 demo features`처럼 Lab을 지정합니다. F01~F06은 실제 수집, 네트워크/API, 노드 설정/주기, 터미널, BDD/JSON 출력, 모의 AI 조회를 검증합니다. [전체 시나리오](../../docs/docs/testing/labs-1-4.md)와 `tests/labs/inventory.json`을 참고하세요. Lab을 켜고 Prober를 배포하는 작업은 `tests/labs/labctl.py`가 담당합니다.

## UI 다운로드부터 SSH 배포까지

`tests/robot/run.sh lab1 demo deployment` 또는 `python3 tests/labs/run-labs.py all demo --scenario deployment`로 실행합니다. D01은 모든 등록 Prober를 실제 UI에서 내려받고 원본 번들을 키 기반 SSH로 전송·갱신합니다. D02는 Agent 소속 링크와 프로젝트에서만 서버 주소를 편집하는 동작을 검사합니다. D03는 배포 후 VM 재시작을 거쳐 세 파일의 해시와 새 텔레메트리·자동 실행을 확인합니다. SSH 증적 화면은 실제 명령 결과를 표시하는 테스트 전용 화면이며 제품 기능이 아닙니다. 민감한 tar.gz는 suite 종료 시 제거하고 해시·공개 설정·결과만 저장합니다.

## 버그 수정 회귀 시나리오

`SONAR_UI_PASSWORD`를 환경에 설정한 뒤 `bash tests/robot/run.sh lab1 check regressions`로 실행합니다. `demo` 모드는 사람에게 보이는 속도로 녹화합니다. CLI 능력 정보에 예시 결과가 섞이지 않는지, 제거된 데모 경로가 404인지, 연결 설정 저장 중 입력·닫기를 잠그는지, 다운로드 화면에서 소속 프로젝트로 돌아가는지를 확인합니다. 저장 테스트는 teardown에서 삭제하는 임시 프로젝트만 사용합니다.

## 교수님 발표용 침해 징후 가정 시연

`python3 tests/labs/run-labs.py lab1 demo --scenario presentation --keep-active`는 실제 격리·해제 UI와 패킷·관리 SSH·토폴로지를 확인하는 P01을 녹화합니다. lab1~lab4에 각각 적용할 수 있습니다. [Lab별 상황, 선택 범위, 발표 멘트와 복구 순서](../../docs/docs/testing/professor-demo.md)를 참고하세요. 기존 조회용 S 시나리오와 달리 실제 네트워크를 변경하고 teardown에서 복구합니다. 침해 탐지는 가정이며 공격 코드는 실행하지 않습니다.

## 발표용 핵심 UML 발췌

`bash tests/robot/run.sh lab1 demo "S18*"`은 격리 클래스/시퀀스, Prober 동기 이벤트, 단일 파일 export 진입점의 UML 4개를 실제 문서 페이지에서 렌더링하고 SVG/PNG와 영상을 저장합니다. 전체 클래스 중 일부만 표시한 설계도입니다.
