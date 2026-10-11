# Test

개발 실행 스크립트, 통합 검사 진입점, Agent/PoC E2E 자료를 모아 둔 디렉터리입니다.

```bash
./Test/dev.sh                   # Frontend + Backend + Agent Bundle 준비
./Test/test.sh                  # 전체 검사
./Test/test.sh --backend        # Backend만
./Test/test.sh --frontend       # Frontend만
./Test/test.sh --prober         # Prober만
./Test/test.sh --docker         # Compose 설정 검증
./Test/test.sh --bundle         # Agent Bundle 산출물 검증
```

- `agent/`: Mock API 및 장비 배포 테스트 자료
- `e2e/astra-results/`: Astra 결과/증거
- `e2e/astra-rvi/`: Real-to-Virtual 검증
- `e2e/d-ai-pbl-poc/`: D-AI-PBL PoC E2E 시나리오와 결과

Prober 검사에는 ANTLR4 C++ 런타임이 필요합니다. 기본 탐색 경로에 없다면
`ANTLR4_RUNTIME_ROOT`를 지정하세요.
