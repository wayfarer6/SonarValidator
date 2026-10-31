# Robot Framework UI 시나리오와 녹화

테스트 원본은 저장소 `tests/robot/`입니다. Robot Framework 7.5와 Browser 20.6.0을 사용하며, Browser 내부의 Chromium으로 버튼·드롭다운·모달·터미널을 조작합니다. 프레임워크 설치·실행 상세는 `tests/robot/README.md`를 기준으로 합니다.

```bash
export SONAR_UI_PASSWORD  # 셸에서 별도로 입력한 값
./tests/robot/run.sh lab1 check
./tests/robot/run.sh lab1 demo 'S02*'
SONAR_HEADLESS=true ./tests/robot/run.sh lab1 demo
./tests/robot/run.sh all check
```

check는 빠른 회귀, demo는 동작 지연 400ms·단계 대기 2초의 발표용 모드입니다. 각 시나리오는 별도 브라우저 컨텍스트에서 실행해 WebM 영상·화면·Robot HTML 보고서를 `test-results/robot/`에 남깁니다. `SONAR_SLOW_MO`, `SONAR_STEP_PAUSE`로 속도를 조절합니다. 비밀번호 입력은 Fill Secret을 사용하고 인증 상태 임시 파일은 종료 때 제거합니다.

| 시나리오 | 실제 동작 |
| --- | --- |
| S01 | 기존 프로젝트 연결 상태·배포 주소 조회 |
| S02 | 노드 모달·역할·등급·허용 대상 선택 후 취소 |
| S03 | 각 Prober PTY에 echo와 id -un 실행 |
| S04 | connected route 기반 대역·자동 미분류 확인 |
| S05 | 임시 프로젝트에서 저장·새로고침·재조회, 종료 시 삭제 |

실제 Lab 정책 푸시·격리·라우터 구성 변경은 수행하지 않습니다. Lab2~4는 리소스 파일과 프로젝트 ID·예상 Agent를 설정하기 전에는 **SKIP**입니다. 네트워크 준비와 프로그램 검증 통과를 구분합니다. 실패한 시험을 숨기거나 재생성한 화면을 증거로 사용하지 않습니다.

설치 기준: [Browser 공식 설치 문서](https://robotframework-browser.org/docs/start/installation/), [컨텍스트와 녹화](https://robotframework-browser.org/docs/concepts/browser-context-page/).
