# 배포 가이드

## Docker Compose

저장소 루트에서 실행합니다. 스택 정의는 Docker 전용 하위 폴더인
`docker/compose/compose.yaml`에 있습니다.

```bash
cp .env.example .env
openssl rand -hex 32  # 결과를 .env의 SONAR_TERMINAL_SHARED_SECRET에 입력(운영 필수)
docker compose -f docker/compose/compose.yaml up -d --build
docker compose -f docker/compose/compose.yaml ps
```

Prober의 CMake/ANTLR4 컴파일은 `docker/compiler/prober/`의 독립 이미지에서
수행됩니다. Backend 이미지만 빌드해도 Compose가 이 빌드 의존성을 자동으로 연결하며,
필요하면 아래처럼 compiler 이미지만 먼저 확인할 수 있습니다.

```bash
docker compose -f docker/compose/compose.yaml build prober-compiler
```

`base-image`와 `prober-compiler`는 실행 복제 수가 0이라 기본 `up`에서는 실행
컨테이너를 만들지 않습니다. 빌드 그래프에서만 사용되므로 `--wait` 상태 판정에도
영향을 주지 않습니다.

로컬/PoC에서는 비밀값이 비어 있어도 개발 기본값으로 기동하지만, 운영에서는 반드시
고유한 `SONAR_TERMINAL_SHARED_SECRET`과 `SONAR_SECRET_KEY`를 설정해야 합니다.

## Agent Bundle

Docker Backend 이미지는 Prober를 빌드하고 `/tmp/sonar_stage`에 실행 파일,
DB 템플릿, systemd/OpenRC 서비스 파일을 함께 넣습니다. 로컬 개발은 같은 계약을
`./Test/dev.sh`가 준비합니다.

```bash
ANTLR4_RUNTIME_ROOT="$HOME/tools/antlr4-install" ./Test/dev.sh --backend-only
./Test/test.sh --bundle
```

Bundle 다운로드 전 Backend의
`/api/v1/agents/bundle/info?agent_id=<이름>&node_type=<유형>`에서 스테이징된
필수 자산과 적용될 설정을 확인할 수 있습니다. 배포 환경별 네트워크와 장비 절차는 Docusaurus의
`docs/docs/PoC Network 검증/` 문서를 참고하세요.
