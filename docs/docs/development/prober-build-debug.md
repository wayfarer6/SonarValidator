# 현재 Prober 빌드와 배포 호환성

현재 CMakeLists.txt는 C++23, nlohmann_json 3.11 이상, OpenSSL, SQLite, Boost 헤더와 ANTLR4 C++ 런타임을 요구합니다.
ANTLR4_HOME의 include와 lib를 참조하며 Homebrew 경로를 자동 탐색합니다. 커밋된 생성 파서를 빌드합니다.

이번 WSL은 ANTLR4 4.13.2와 GCC 15를 사용했습니다. CentOS Guest Shell에 배포하려고 별도 디렉터리에서 정적으로 링크했습니다.
현재 CMake는 공통 core 라이브러리·등록 가능한 전략·실제 CTest 타깃을 사용합니다. 기존 로컬 build-debug 디렉터리는 별도로 유지합니다.

```bash
cmake -S SonarValidator_Prober -B /tmp/sonar-lab1-static-build \
  -DCMAKE_BUILD_TYPE=Release -DSONAR_STATIC_LINK=ON \
  -DANTLR4_HOME=/home/linuxbrew/.linuxbrew \
  -DSQLite3_LIBRARY=/usr/lib/x86_64-linux-gnu/libsqlite3.a \
  -DSONAR_OPENSSL_CRYPTO_LIBRARY=/usr/lib/x86_64-linux-gnu/libcrypto.a \
  -DCMAKE_EXE_LINKER_FLAGS=-static \
  '-DCMAKE_CXX_STANDARD_LIBRARIES=-l:libjitterentropy.a -lz -lzstd -ldl -pthread'
cmake --build /tmp/sonar-lab1-static-build -j1
file /tmp/sonar-lab1-static-build/sonar_validator_prober
```

이 환경의 정적 OpenSSL은 libjitterentropy3-dev와 libzstd-dev의 전이 의존성이 필요했습니다. 다른 배포판은 경로와 의존성이 다를 수 있습니다.
정적 glibc 링크에도 DNS·dlopen 관련 경고가 남습니다. 숫자 관리 IP의 실제 Guest Shell 실행·서버 연결로 이번 경로를 검증했습니다.

`SONAR_BUILD_TESTS`의 기본값은 ON입니다. `ctest --test-dir /tmp/sonar-lab1-static-build --output-on-failure --no-tests=error`로 실제 등록한 테스트를 실행합니다. `installer_bundle`은 빌드한 실행 파일을 Installer에 복사하며 해당 바이너리는 Git에서 제외합니다. 릴리스에는 같은 검증 바이너리를 자산으로 첨부합니다.

DB·PTY 수명 회귀는 `./tests/prober/run.sh`로 직접 컴파일·실행합니다. 자세한 범위는 `tests/prober/README.md`를 참고합니다. 실제 SSH 배포·웹 터미널·재기동 검사는 이 로컬 시험과 별도로 수행합니다.
