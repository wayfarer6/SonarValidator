#!/bin/sh
# =============================================================================
#  build.sh — SonarValidator Prober 빌드/테스트/실행
#
#  1) ANTLR4 툴체인(jar + C++ 런타임) 위치를 찾는다.
#  2) CMake 로 구성/빌드한다. (생성 파서는 저장소에 커밋된 소스를 그대로 쓴다)
#  3) 단위 테스트를 돌린다.
#  4) 프로버를 실행한다.
#
#  툴체인 경로를 바꾸고 싶으면 환경변수로 넘기면 된다.
#    ANTLR4_JAR=... ANTLR4_RUNTIME_ROOT=... ./build.sh
# =============================================================================

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

# ---------------------------------------------------------------- ANTLR jar --
if [ -z "${ANTLR4_JAR:-}" ]; then
    for candidate in \
        "$HOME/tools/antlr.jar" \
        "$HOME/antlr/antlr-4.13.2-complete.jar" \
        "/usr/local/lib/antlr-4.13.2-complete.jar" \
        "/usr/share/java/antlr4.jar"
    do
        if [ -f "$candidate" ]; then
            ANTLR4_JAR="$candidate"
            break
        fi
    done
fi

# ------------------------------------------------------------ ANTLR runtime --
if [ -z "${ANTLR4_RUNTIME_ROOT:-}" ]; then
    for candidate in \
        "$HOME/tools/antlr4-install" \
        "$HOME/.local" \
        "/usr/local" \
        "/usr"
    do
        if [ -f "$candidate/include/antlr4-runtime/antlr4-runtime.h" ]; then
            ANTLR4_RUNTIME_ROOT="$candidate"
            break
        fi
    done
fi

if [ -z "${ANTLR4_JAR:-}" ] || [ -z "${ANTLR4_RUNTIME_ROOT:-}" ]; then
    echo "[ERROR] ANTLR4 툴체인을 찾지 못했습니다." >&2
    echo "        ANTLR4_JAR / ANTLR4_RUNTIME_ROOT 환경변수로 지정하세요." >&2
    echo "        (jar=${ANTLR4_JAR:-<none>} root=${ANTLR4_RUNTIME_ROOT:-<none>})" >&2
    exit 1
fi

export ANTLR4_JAR
export ANTLR4_RUNTIME_ROOT

echo "[INFO] ANTLR4 jar     : $ANTLR4_JAR"
echo "[INFO] ANTLR4 runtime : $ANTLR4_RUNTIME_ROOT"

cmake -S . -B build \
    -DANTLR4_JAR="$ANTLR4_JAR" \
    -DANTLR4_RUNTIME_ROOT="$ANTLR4_RUNTIME_ROOT"
cmake --build build --parallel

ctest --test-dir build --output-on-failure

exec ./build/sonar_validator_prober