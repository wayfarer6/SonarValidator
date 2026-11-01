# Robot Framework UI와 녹화

실행 원본은 tests/robot입니다. Browser의 Chromium으로 버튼·드롭다운·모달·터미널을 조작합니다. 설치 버전은 tests/robot/requirements.txt, 상세 환경변수는 tests/robot/README.md를 기준으로 합니다.

```bash
# 선택한 Lab이 실행 중이고 Prober 배포가 끝난 상태
SONAR_UI_PASSWORD=... tests/robot/run.sh lab2 demo features
# Lab을 순서대로 전환하며 녹화하고 마지막에 시작 상태 복원
SONAR_UI_PASSWORD=... python3 tests/labs/run-labs.py all demo
# 최초 배포와 재시작 검증도 포함하려면 --prepare --reboot 추가
# Cisco enable 암호는 SONAR_DEVICE_PASSWORD 환경변수로 전달
```

전체 흐름은 [Lab별 기능 시나리오](labs-1-4.md)에 있습니다. run.sh 단독 실행은 GNS3 노드를 켜지 않습니다. run-labs.py는 알려진 네 Lab의 노드만 제어합니다. 12GiB 환경에서 여러 Lab을 동시에 켜지 않도록 순서대로 실행합니다.

check는 빠른 회귀, demo는 사람이 읽을 수 있는 지연을 둡니다. SONAR_SLOW_MO, SONAR_STEP_PAUSE로 속도를 조절하고 SONAR_HEADLESS=false로 브라우저를 표시할 수 있습니다. 출력 경로는 ROBOT_OUTPUT_DIR로 지정합니다. 시나리오마다 별도 컨텍스트와 WebM을 남기며 주요 화면은 PNG, 통신·수집·명령 결과는 JSON으로 보관합니다.

로그인은 Fill Secret을 사용하고 .auth.json은 suite 종료 시 제거합니다. 기존 개인키·API 원문 자격증명·환경 파일·DB는 결과 묶음에 넣지 않습니다. 모의 AI 공급자는 임시 등록 후 제거하며 자동 runner가 띄운 서버는 종료합니다. 다른 프로그램이 점유한 포트를 대신 차지하지 않습니다.

BDD 모델 판정과 실제 패킷 도달성은 별도입니다. 모의 공급자의 응답도 실제 AI 판단 품질 검증과 구분합니다. 실패를 통과 결과로 표시하거나 화면을 재생성해 증거로 쓰지 않습니다.
