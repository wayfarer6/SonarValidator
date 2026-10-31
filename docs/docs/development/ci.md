# CI와 검증 계약

Backend는 Java 26과 Maven Wrapper의 `clean test`, Frontend는 TypeScript·ESLint·Vite 빌드를 실행합니다. Prober는 커밋된 ANTLR 4.13.2 파서와 같은 버전의 C++ 런타임을 설치하고 `ANTLR4_HOME`을 지정합니다.

CMake의 `sonar_prober_core`를 실행 파일과 테스트가 공유합니다. 기본 ON인 `SONAR_BUILD_TESTS`가 실제 `_test.cpp`와 EEM 스냅샷 게시 테스트를 등록합니다. Release에서도 테스트의 assert가 비활성화되지 않게 합니다. CI는 `ctest --no-tests=error`로 테스트가 없는 빌드를 성공으로 처리하지 않습니다.

통신 통합 테스트는 자체 루프백 WebSocket 서버를 사용합니다. 개발 서버가 없다는 이유로 성공을 반환하지 않습니다. 실제 GNS3 장비·SSH·UI 검증은 별도의 Robot 실행이며 GitHub 일반 runner에서 통과했다고 주장하지 않습니다.

- Backend: `./mvnw clean test`
- Prober: `cmake --build <build>`, `ctest --test-dir <build> --output-on-failure --no-tests=error`
- Frontend: `npm run lint`, `npm run build`
- Docs: `npm run build`
- 실제 Lab1: `./tests/robot/run.sh lab1 demo` 및 `./tests/robot/run.sh lab1 check shutdown`

로컬 결과와 원격 GitHub Actions 결과는 구분합니다. 원격 작업은 푸시/PR 후 해당 head SHA의 실행 결과로 확인합니다.
