# 운영과 장애 진단

## 서버

UI는 `http://localhost:5173`, Backend 상태는 `http://localhost:3000/actuator/health`입니다.
현재 개발 앱은 `sonar-lab1-backend.service`, `sonar-lab1-preview.service` transient unit으로 실행합니다.
로그는 `journalctl -u sonar-lab1-backend -n 100 --no-pager`, 종료는 `sudo systemctl stop sonar-lab1-preview sonar-lab1-backend`입니다.
WSL 부팅 후 앱은 자동 시작하지 않습니다. [Lab 1 시작 명령](../deployment/lab1.md)을 사용합니다.
`gns3-lab-network.service`는 TAP·관리망 경로만 복구합니다.

## Cisco

1. `ssh -o BatchMode=yes -p 2222 guestshell@192.168.148.181` 또는 `.182`로 확인합니다.
2. 이번 홈 배포는 `pgrep -af sonar_validator_prober`와 `tail -50 ~/sonar/prober.log`를 확인합니다. systemd 설치 프로필과 구분합니다.
3. 서버 등록 이름과 AGENT_NAME을 비교합니다.
4. last_seen 반복 증가와 IOS 인터페이스·라우트를 확인합니다.
5. dohost의 Unexpected Error는 종료 코드 0이어도 실패입니다.
6. EEM 캐시 생성 시각을 확인합니다. Prober는 300초를 넘긴 캐시를 사용하지 않습니다.

SSH, sudo/PAM, IOS CLI 수집, WebSocket 연결은 각각 다른 단계입니다.
`guestshell enable`은 자격증명에 영향을 줄 수 있으므로 장애마다 반복하지 않습니다.
재배포는 서비스를 정상 종료하고 수행합니다. 기존 SQLite DB와 SSH 키를 삭제하지 않습니다.

## Prober 수동 제어

한 번 종료해도 EEM이 다음 주기에 다시 시작합니다. 계속 정지하려면 IOS에서 SONAR-Lab1-Collect applet을 해제한 뒤 해당 Prober 프로세스에 SIGTERM을 보냅니다.
재개는 [Lab 1 EEM](../deployment/lab1.md)을 다시 적용하고 다음 주기를 기다립니다.
수집 파일과 DB를 지우는 작업은 정지 절차에 포함하지 않습니다.
