# SonarValidator Prober

C++23 장비 수집기입니다. components는 파서·장비·정책·터미널 부품, module은 초기화·관리·수집·오프라인 내보내기, database는 SQLite 계층을 담습니다.

- [현재 빌드 계약과 제약](../docs/docs/development/prober-build-debug.md)
- [통신 envelope](../docs/docs/protocol/overview.md)
- [장비별 수집·적용](../docs/docs/policy/device-contracts.md)
- [Lab 1 배포](../docs/docs/deployment/lab1.md)
- [오프라인 수집](../docs/docs/features/offline-export.md)

ANTLR 4.13.2 C++ 런타임은 `third_party/antlr4-runtime`에 포함되어 있습니다. Homebrew·시스템 ANTLR 설치·Java·네트워크 다운로드 없이 커밋된 생성 파서와 함께 빌드합니다. SQLite/OpenSSL/Boost/nlohmann-json과 C++23 컴파일러·CMake는 시스템 빌드 의존성입니다.

저장소 루트에서 실행합니다. `./SonarValidator_Prober/build.sh`로 같은 빌드/검사를 실행할 수도 있으며, 실행까지 하려면 `--run`을 명시합니다.

```sh
cmake -S SonarValidator_Prober -B build-prober -DCMAKE_BUILD_TYPE=Release
cmake --build build-prober -j2
ctest --test-dir build-prober --output-on-failure
```

문법을 바꿀 때만 Java가 필요합니다. `fetch_antlr_tool`로 해시가 고정된 도구를 받고 `regenerate_parser`로 재생성합니다. 전체 정적 링크와 업데이트 절차는 빌드 문서를 참고하세요.

[현재 런타임 설계](../docs/docs/architecture/prober-runtime.md): 수집·관리·터미널 작업 스레드와 호출 스레드에서 처리하는 DB 이벤트를 사용합니다. DB 전용 스레드는 없습니다. 전체 회귀 검사는 위 CTest 또는 `build.sh`로 실행합니다. [테스트 선택 실행](../tests/prober/README.md)도 같은 CMake 테스트 목록을 사용합니다.
