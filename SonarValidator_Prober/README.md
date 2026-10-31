# SonarValidator Prober

C++23 장비 수집기입니다. components는 파서·장비·정책·터미널 부품, module은 초기화·관리·수집·오프라인 내보내기, database는 SQLite 계층을 담습니다.

- [현재 빌드 계약과 제약](../docs/docs/development/prober-build-debug.md)
- [통신 envelope](../docs/docs/protocol/overview.md)
- [장비별 수집·적용](../docs/docs/policy/device-contracts.md)
- [Lab 1 배포](../docs/docs/deployment/lab1.md)
- [오프라인 수집](../docs/docs/features/offline-export.md)

현재 CMake는 ANTLR4_HOME과 커밋된 생성 파서를 사용합니다. 예전 ANTLR4_RUNTIME_ROOT·installer_bundle·ctest 안내를 현재 작업 트리와 혼용하지 않습니다.

[현재 런타임 설계](../docs/docs/architecture/prober-runtime.md): 수집·관리·터미널 작업 스레드와 호출 스레드에서 처리하는 DB 이벤트를 사용합니다. DB 전용 스레드는 없습니다. 회귀 검사는 `./tests/prober/run.sh`로 실행합니다.
