# Prober 빌드와 진단

Linux/WSL에서 C++23 컴파일러와 CMake 3.20 이상을 사용합니다. ANTLR 4.13.2 런타임의 공식 C++ 소스는 `SonarValidator_Prober/third_party/antlr4-runtime`에 포함되어 있습니다. CMake가 이를 정적 타깃으로 빌드하므로 `ANTLR4_HOME`, Homebrew 탐색, 전역 `-L`/include 경로는 사용하지 않습니다.

## 일반 빌드

Ubuntu/WSL의 시스템 의존성은 다음과 같습니다. 일반 빌드에는 Java·ANTLR 패키지 설치·네트워크 다운로드가 필요하지 않습니다.

```sh
sudo apt-get install build-essential cmake pkg-config libsqlite3-dev libssl-dev libboost-dev nlohmann-json3-dev
cmake -S SonarValidator_Prober -B build-prober -DCMAKE_BUILD_TYPE=Release
cmake --build build-prober -j2
ctest --test-dir build-prober --output-on-failure --no-tests=error
```

`./SonarValidator_Prober/build.sh`는 같은 configure/build/CTest를 실행합니다. 기본 병렬 작업 수는 2이며 `SONAR_BUILD_JOBS`·`SONAR_BUILD_DIR`·`SONAR_BUILD_TYPE`으로 조정합니다. 프로그램 실행은 `--run`을 명시했을 때 수행합니다.

이전 빌드 디렉터리에 외부 경로가 남아 있다면 새 디렉터리로 configure하는 것이 명확합니다. `compile_commands.json`의 ANTLR include는 프로젝트의 `third_party/antlr4-runtime/src`를 가리키며 링크 대상도 같은 빌드에서 만든 정적 라이브러리입니다.

## 배포용 정적 빌드

```sh
cmake -S SonarValidator_Prober -B build-prober-static -DCMAKE_BUILD_TYPE=Release -DSONAR_STATIC_LINK=ON -DCMAKE_EXE_LINKER_FLAGS=-static
cmake --build build-prober-static -j2
ctest --test-dir build-prober-static --output-on-failure
cmake --build build-prober-static --target installer_bundle
```

`SONAR_STATIC_LINK`는 SQLite·OpenSSL 등의 정적 의존성을 선택합니다. Linux에서 libc/libstdc++까지 정적으로 묶으려면 위처럼 `-static` 링크 옵션을 함께 사용합니다. 설치 번들에는 ANTLR 라이선스도 복사합니다. Backend 다운로드 tar에도 같은 고지가 들어갑니다.

## 문법 재생성

기존 생성 파서는 그대로 컴파일합니다. `.g4`를 수정한 경우에만 Java를 준비하고 다음을 실행합니다.

```sh
cmake --build build-prober --target fetch_antlr_tool
cmake --build build-prober --target regenerate_parser
```

첫 번째 명령만 명시적으로 네트워크를 사용합니다. 공식 ANTLR 4.13.2 JAR를 빌드 디렉터리에 받고 SHA-256을 검증합니다. 일반 빌드의 의존 타깃이 아닙니다. 기존 파일의 해시가 다르면 덮어쓰지 않고 실패합니다. `SONAR_ANTLR_TOOL_JAR`로 도구 캐시 경로를 지정할 수도 있습니다.

재생성 스크립트는 JAR 버전이 포함된 런타임의 VERSION과 다르면 거부합니다. 생성이 모두 성공한 뒤 출력 디렉터리를 교체하며 생성 주석에는 개발자별 절대 경로를 넣지 않습니다. 외부 JAR로 직접 실행하려면 `ANTLR4_JAR=/path/to/antlr-4.13.2-complete.jar sh SonarValidator_Prober/components/parser/cmake/regenerate_parser.sh`를 사용합니다.

## 버전과 진단

출처·배포 아카이브 해시는 `third_party/antlr4-runtime/UPSTREAM.json`에 있습니다. 버전을 올릴 때 런타임, 생성 도구 해시, 생성 파서와 Backend 번들용 라이선스를 함께 갱신하고 전체 CTest를 실행합니다. 서드파티 소스는 원본을 유지합니다.

실행 로그의 `SONAR_LOG_LEVEL=DEBUG` 사용법은 [작업 중심 로그](./logging.md)를 참고하세요.

VS Code Dev Container는 같은 시스템 개발 패키지를 Dockerfile에서 설치합니다. 별도의 CMake 재설치 스크립트나 vcpkg 기능을 사용하지 않습니다.
