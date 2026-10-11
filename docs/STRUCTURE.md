# 저장소 구조

```text
.
├── Prober/                 # C++23 Agent/수집기
├── Backend/                # Spring Boot API/정책 엔진
├── Frontend/               # React/Vite UI
├── docs/                   # 설계, 배포, 운영 문서와 Docusaurus 사이트
├── docker/
│   ├── D-AI-PBL-POC/      # PoC 네트워크용 GNS3 Docker appliance
│   ├── base/               # JDK/Maven 기반 이미지
│   ├── compiler/prober/    # CMake/ANTLR4 Prober 컴파일 전용 이미지
│   ├── backend/            # Backend 이미지
│   ├── frontend/           # Frontend nginx 이미지
│   ├── scripts/            # 이미지 빌드 도구
│   └── compose/
│       └── compose.yaml    # 전체 스택 정의
├── Test/
│   ├── dev.sh              # 로컬 개발 스택
│   ├── test.sh             # 통합 검사 진입점
│   ├── agent/              # Agent mock/deployment 테스트
│   └── e2e/                # PoC 및 RVI E2E 증거
├── LICENSE
└── README.md
```

실행 파일과 빌드 산출물은 각 컴포넌트의 `build-*`, `target`, `dist`,
`node_modules`에 생성되며 Git에 포함하지 않습니다. Agent 다운로드 파일은 로컬
개발 시 `/tmp/sonar_stage`, 컨테이너에서는 같은 경로에 스테이징됩니다.
