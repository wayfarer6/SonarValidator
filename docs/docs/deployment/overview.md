# 배포 안내

1. 관리 서버 주소와 장비 SSH 경로를 확인합니다.
2. 기존 프로젝트와 DB를 보존합니다.
3. 장비 OS와 CPU에 맞는 실행 파일을 준비합니다.
4. 배포 예정 Agent를 등록하고 같은 이름으로 서버 생성 번들을 받습니다.
5. SSH로 설치한 후 서비스, 연결, 실제 텔레메트리를 각각 확인합니다.

| 작업 | 문서 |
| --- | --- |
| Lab 1 Cisco | [SSH 배포](lab1.md) |
| 번들 API·형식 | [Agent 번들](agent-bundle.md) |
| DB | [설치](database.md) |
| 컨테이너 스택 | [Compose](docker-compose.md) |
| OPNsense API | [연결](opnsense-api.md) |

`SONAR_SECRET_KEY`는 DB의 자격증명 암호화 키입니다. `SONAR_TERMINAL_SHARED_SECRET`은 Backend와 Prober의 터미널 인증 값입니다. 두 값은 역할이 다르며 재시작 때 유지해야 합니다. 원문을 Git이나 보고서에 넣지 않습니다.

Lab 1 서버 환경은 `/home/ssh1032007/.config/sonarvalidator/lab1.env`에 저장합니다(권한 600). 개발 앱을 부팅 자동 시작으로 등록하지 않았습니다. WSL의 네트워크 복구 서비스와 앱 시작은 별개입니다.
