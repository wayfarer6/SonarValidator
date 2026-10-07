#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
SOURCE_DIR="$ROOT_DIR/SonarValidator_Prober"
BUILD_DIR="$SCRIPT_DIR/build"

cmake -S "$SOURCE_DIR" -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DANTLR4_RUNTIME_ROOT="${ANTLR4_RUNTIME_ROOT:-/usr}"
cmake --build "$BUILD_DIR" --parallel "${BUILD_JOBS:-2}"
ctest --test-dir "$BUILD_DIR" --output-on-failure

install -m 755 "$BUILD_DIR/sonar_validator_prober" \
    "$SCRIPT_DIR/sonar_validator_prober"

ANTLR4_LIBRARY=$(ldd "$SCRIPT_DIR/sonar_validator_prober" |
    sed -n 's/.*libantlr4-runtime[^=]*=> \([^ ]*\).*/\1/p')
if [ -n "$ANTLR4_LIBRARY" ] && [ -f "$ANTLR4_LIBRARY" ]; then
    install -D -m 755 "$ANTLR4_LIBRARY" \
        "$SCRIPT_DIR/lib/$(basename "$ANTLR4_LIBRARY")"
fi

printf '[OK] Agent test binary: %s\n' "$SCRIPT_DIR/sonar_validator_prober"
