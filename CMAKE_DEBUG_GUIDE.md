# Prober CMake 빌드 및 디버깅 가이드

이 문서는 `SonarValidator_Prober`를 IDE나 Docker 없이 로컬에서 CMake로 구성하고,
테스트·디버깅하는 방법을 설명합니다. 예시는 Ubuntu 24.04 기준입니다.

## 1. 준비물

```bash
sudo apt-get update
sudo apt-get install -y \
  build-essential cmake gdb curl unzip \
  libsqlite3-dev libssl-dev libboost-dev nlohmann-json3-dev
```

프로젝트는 C++23과 CMake 3.20 이상이 필요합니다. ANTLR4 C++ 런타임은 아래처럼
한 번 설치합니다. 일반 빌드에는 Java나 ANTLR jar가 필요하지 않습니다.

```bash
mkdir -p "$HOME/tools/antlr4-src"
curl -fL \
  https://www.antlr.org/download/antlr4-cpp-runtime-4.13.2-source.zip \
  -o /tmp/antlr4-cpp-runtime-4.13.2-source.zip
unzip -q /tmp/antlr4-cpp-runtime-4.13.2-source.zip \
  -d "$HOME/tools/antlr4-src"

cmake -S "$HOME/tools/antlr4-src" \
  -B "$HOME/tools/antlr4-build" \
  -DCMAKE_BUILD_TYPE=Debug \
  -DCMAKE_INSTALL_PREFIX="$HOME/tools/antlr4-install" \
  -DANTLR_BUILD_STATIC=ON \
  -DANTLR_BUILD_SHARED=OFF
cmake --build "$HOME/tools/antlr4-build" --parallel
cmake --install "$HOME/tools/antlr4-build"
```

설치가 끝나면 다음 두 경로가 있어야 합니다.

```text
~/tools/antlr4-install/include/antlr4-runtime/antlr4-runtime.h
~/tools/antlr4-install/lib/libantlr4-runtime.a
```

## 2. Debug 빌드 구성 및 컴파일

저장소 루트에서 실행합니다. 전용 `build-debug` 디렉터리를 사용하므로 기존 `build`
구성이나 산출물을 덮어쓰지 않습니다.

```bash
cmake -S SonarValidator_Prober \
  -B SonarValidator_Prober/build-debug \
  -DCMAKE_BUILD_TYPE=Debug \
  -DCMAKE_EXPORT_COMPILE_COMMANDS=ON \
  -DANTLR4_RUNTIME_ROOT="$HOME/tools/antlr4-install"

cmake --build SonarValidator_Prober/build-debug \
  --parallel \
  --target sonar_validator_prober
```

전체 테스트 실행 파일도 함께 빌드하고 싶다면 타깃을 생략합니다.

```bash
cmake --build SonarValidator_Prober/build-debug --parallel
```

생성된 `compile_commands.json`은 `SonarValidator_Prober/build-debug/`에 있습니다.
VS Code의 C/C++ 확장이나 clangd에서 이 파일을 사용하면 컴파일러 옵션과 include 경로를
일치시킬 수 있습니다.

### VS Code에서 F5 디버깅

저장소 루트를 VS Code로 열고 Microsoft C/C++ (`ms-vscode.cpptools`) 확장을 설치합니다.
저장소에서 추천 확장 팝업이 나오면 CMake Tools (`ms-vscode.cmake-tools`)도 설치하세요.
`.vscode/` 설정은 Linux의 `/usr/bin/gdb`와 위 Debug 빌드 디렉터리를 사용합니다.

- **Run and Debug → `Prober: Debug safe startup (--help)`**: F5로 `main()`에 멈춥니다.
  `--help`만 실행하므로 설정 파일 생성이나 네트워크 접속은 하지 않습니다.
- **`Prober: Debug with local config (may access devices)`**: `.vscode/debug-default.conf`의
  `#DEBUG` 전용 설정(SERVER_IP `192.168.122.1`, SERVER_PORT `3000`, NODE_TYPE `VM`)과
  `build-debug/data` 데이터 디렉터리를 사용합니다. 계속 실행하면 서버에 접속을 시도하므로
  VM 네트워크에서만 사용하세요. 운영 설정 파일은 수정하지 않습니다.
- **`Prober test: Router`**: 라우터 단위 테스트를 GDB에서 실행합니다.
- VS Code의 **Terminal → Run Build Task**에서 Prober Debug 빌드나 전체 테스트 빌드를 선택합니다.

F5 전에 자동으로 CMake Debug 구성을 확인하고 Prober 실행 파일을 빌드합니다.

## 3. 테스트

모든 등록된 테스트를 실행합니다.

```bash
ctest --test-dir SonarValidator_Prober/build-debug --output-on-failure
```

특정 테스트만 실행할 수도 있습니다. 아래 예시는 라우터 모델과 CLI 파서를 검증합니다.

```bash
ctest --test-dir SonarValidator_Prober/build-debug \
  --output-on-failure \
  -R 'router_test|cli_output_parser_test'
```

## 4. GDB로 테스트 또는 Prober 디버깅

테스트 실행 파일을 GDB로 열면 실패를 재현하면서 브레이크포인트를 설정할 수 있습니다.

```bash
gdb --args SonarValidator_Prober/build-debug/router_test
```

GDB 프롬프트에서:

```gdb
break main
run
bt
print variable_name
next
continue
```

실제 Prober의 도움말 경로는 DB나 장비 연결을 시작하지 않아 안전한 실행 확인에 쓸 수 있습니다.

```bash
gdb --args SonarValidator_Prober/build-debug/sonar_validator_prober --help
```

Prober 초기화나 수집 경로를 디버깅할 때는 실제 장비 대신 격리된 데이터 디렉터리를
지정하고, 설정·템플릿을 확인한 뒤 실행하세요. 기본 실행은 설정에 따라 네트워크 장비에
접속할 수 있습니다.

```bash
mkdir -p /tmp/sonar-prober-debug
SONAR_DATA_DIR=/tmp/sonar-prober-debug \
SONAR_TEMPLATE_PATH="$PWD/SonarValidator_Prober/Installer/default_template.sqlite" \
  gdb --args SonarValidator_Prober/build-debug/sonar_validator_prober
```

### 터미널에서 오프라인 실행

Debug 빌드는 실행 파일을 실행한 위치나 실행 파일 경로의 상위에서 `.vscode/debug-default.conf`
를 찾아 자동 적용합니다. 파일의 첫 줄이 `#DEBUG`인지 확인한 뒤에만 사용하며,
운영 설정과 분리된 `build-debug/data` 데이터 디렉터리와 저장소의 SQLite 템플릿을 씁니다.
저장소 루트에서 아래처럼 실행하면 별도의 환경변수 없이 오프라인 수집을 시작합니다.

```bash
SonarValidator_Prober/build-debug/sonar_validator_prober --export-offline
```

이 경우 서버 주소는 `192.168.122.1:3000`, 노드 타입은 `VM`입니다. 스냅샷은 실행한
현재 디렉터리에 저장됩니다. 실행 후 `[DEBUG] #DEBUG profile` 및
`[INFO] 스냅샷을 저장했습니다` 메시지를 확인하세요. 이 모드는 계속 수집하므로 중단은
`Ctrl+C`입니다. `--export-dir <경로>` 또는 `SONAR_OFFLINE_DIR`로 저장 위치를 바꿀 수
있습니다. 기존 기본 환경변수가 필요하면 명시적으로 `SONAR_CONFIG_PATH`,
`SONAR_DATA_DIR`, `SONAR_TEMPLATE_PATH`를 설정하면 자동 기본값보다 우선합니다.

일회성 오프라인 내보내기는 서버 전송 워커를 시작하지 않지만, 초기 설정과 장비 접근
동작은 여전히 수행할 수 있습니다. 이 모드도 실제 장비 연결이 없음을 보장하지 않으므로
격리된 테스트 환경에서만 사용하세요.

## 5. CLI 파서 입력을 빠르게 확인하기

`cli_output_parser_probe`는 파서 전용 도구이며 Agent 초기화나 장비 연결 없이 입력 파일을
파싱합니다.

```bash
cmake --build SonarValidator_Prober/build-debug \
  --target cli_output_parser_probe

SonarValidator_Prober/build-debug/cli_output_parser_probe \
  route /tmp/show-route.txt cisco
```

입력 대상은 `nic`, `brief`, `route`, `interface`, `ovs`, `vlan`, `switchport`,
`ruleset`, `arp`입니다. 벤더 인자는 `ubuntu`, `frr`, `cisco`, `arista`, `ovs`,
`nftables` 중 하나를 사용할 수 있습니다.

## 6. 파서 문법을 수정한 경우

ANTLR 생성 C++ 파일은 저장소에 포함되어 있어 일반 빌드에서는 `.g4`를 다시 생성하지
않습니다. 문법 파일을 변경했다면 Java와 ANTLR 완전 jar 4.13.2를 준비하고 생성 타깃을
명시적으로 실행해야 합니다.

```bash
cd SonarValidator_Prober
ANTLR4_JAR="$HOME/tools/antlr-4.13.2-complete.jar" \
  cmake --build build-debug --target regenerate_parser
cmake --build build-debug --parallel
ctest --test-dir build-debug --output-on-failure
```

생성 결과인 `components/parser/generated/grammar/`도 변경사항에 포함해야 합니다.
버전이 다른 jar로 생성하면 런타임 API나 전체 생성 파일 diff가 달라질 수 있으니
4.13.2를 사용하세요.

## 문제 해결

| 증상 | 확인할 내용 |
| --- | --- |
| `ANTLR4 C++ 런타임을 찾지 못했습니다` | `ANTLR4_RUNTIME_ROOT`에 헤더와 `libantlr4-runtime.a`가 있는지 확인 |
| `CMake 3.20 or higher is required` | CMake를 3.20 이상으로 업데이트 |
| 오래된 설정/경로가 계속 사용됨 | 새 빌드 디렉터리(예: `build-debug-clean`)로 다시 구성 |
| 문법 변경이 실행 결과에 반영되지 않음 | `regenerate_parser` 실행 후 재빌드했는지 확인 |
| gdb에서 변수 값이 최적화됨 | `CMAKE_BUILD_TYPE=Debug`로 구성했는지 확인 |
