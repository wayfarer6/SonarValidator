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

정책 푸시·격리·실제 장비 네트워크 변경은 실행하지 않습니다. 실제 프로젝트의 저장·삭제 버튼도 누르지 않습니다.

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
