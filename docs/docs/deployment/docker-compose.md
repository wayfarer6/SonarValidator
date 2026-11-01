# Docker Compose 배포

현재 서비스·볼륨·환경변수의 기준은 저장소 루트 docker-compose.yml과 .env.example입니다.
개발 서버 실행과 Compose 스택을 같은 포트로 동시에 띄우지 않습니다.

```bash
docker compose config --services
docker compose up --build
docker compose ps
```

볼륨 삭제를 배포·재시작 절차에 포함하지 않습니다. 기존 데이터·DB 프로필·서버 포트·공유 시크릿·암호화 키를 먼저 확인합니다.
이번 Lab 1은 Compose를 실행하지 않고 기존 H2 DB로 개발 Backend·Frontend를 실행했습니다.
별도의 deeptutor·Whisper·TTS·WebGoat 컨테이너는 이 스택이 아닙니다.

GNS3 실험용 Ubuntu/OVS/방화벽 이미지는 `./dev.sh --build-images`로 준비합니다. 태그와 다른 컴퓨터로 옮기는 절차는 [GNS3 Docker 이미지](./gns3-images.md)를 참고하세요.
