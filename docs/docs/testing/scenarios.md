# 회귀 검증 계층

| 계층 | 실행 기준 | 확인 범위 |
| --- | --- | --- |
| Backend | SonarValidator_Backend의 ./mvnw test | 정책 모델, 프로젝트, 권한, AI 실행 이력, API와 번들 |
| Prober | CMake 빌드 후 CTest | 파서·명령 실행기·장비 전략·DB·종료·중복 요청 |
| Frontend | SonarValidator_Frontend의 npm test, npm run build, npm run lint | API 타임아웃·터미널 수명·데이터 변환·정적 검사 |
| 실제 Lab 기능 | tests/robot/run.sh labN demo features | 실제 수집·경로·모달·터미널·BDD/출력·AI 조회 |
| 추가 UI 회귀 | tests/robot/run.sh lab1 check | 저장·재조회·잘못된 입력·주기 변경·UML |

[Lab1~4의 F01~F06](labs-1-4.md)을 주요 기능 검증과 발표 녹화의 기준으로 사용합니다. 모든 Lab의 프로젝트 ID와 Agent 목록은 리소스 파일에 지정되어 있습니다. 설정 전 SKIP하던 초기 계획은 현재 실행 기준에서 제거했습니다.

Cisco 셸 종료 스트레스와 SIGTERM 복구는 tests/robot/suites/prober_shutdown.robot, Arista CLI 반복·긴 출력·임시 VLAN 제어는 arista_cli.robot에서 별도로 검증합니다. 실제 정책 푸시·격리는 명시적인 별도 실험으로 구분합니다.

## B04 오류 요약과 상세 모달

기존 버그 회귀 suite의 B04는 긴 HTTP 500 응답을 UI에서만 모의합니다. 목록에 요약이 표시되고 재시도 후 한 건만 기록되는지, 요청 오류 탭의 상세 모달에서 원문을 확인할 수 있는지 검사합니다. 비밀 값 가림과 HTML 실행 방지도 확인합니다. 이어 모의 장비 로그의 요약과 상세 모달을 검사하고 네 장면을 캡처합니다. 실제 장비 설정과 서버 로그 데이터는 수정하지 않습니다.

`tests/robot/run.sh lab1 demo regressions`로 기존 회귀들과 함께 실행합니다. `test-results/error-details/lab1`에는 이번 B04 검증의 스크린샷과 시나리오 동영상이 있습니다.
