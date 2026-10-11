# Docker Compose

전체 애플리케이션 스택 정의는 이 폴더의 `compose.yaml`에 있습니다.
상대 빌드 컨텍스트가 저장소 루트를 기준으로 구성되어 있으므로 명령은 저장소 루트에서
실행합니다.

```bash
docker compose -f docker/compose/compose.yaml config
docker compose -f docker/compose/compose.yaml build prober-compiler
docker compose -f docker/compose/compose.yaml up -d --build
docker compose -f docker/compose/compose.yaml logs -f backend
docker compose -f docker/compose/compose.yaml down
```

환경 변수는 저장소 루트의 `.env.example`을 `.env`로 복사해 설정합니다.
`base-image`와 `prober-compiler`는 실행 복제 수가 0이라 기본 `up`에서는
컨테이너로 실행되지 않지만, Backend 이미지 빌드 의존성에는 자동 포함됩니다.
