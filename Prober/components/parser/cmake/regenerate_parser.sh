#!/bin/sh
# =============================================================================
#  regenerate_parser.sh — ANTLR4 문법에서 C++ 파서를 다시 생성한다.
#
#  언제 쓰나
#    components/parser/grammar/*.g4 를 수정했을 때만 실행한다.
#    생성 결과는 components/parser/generated/grammar 에 들어가고,
#    그 파일들이 저장소에 커밋되므로 평상시 빌드에는 Java 가 필요 없다.
#
#  준비물
#    - Java (java 실행 파일)
#    - ANTLR4 완전 jar (antlr-4.13.2-complete.jar 등)
#        ANTLR4_JAR 환경변수로 지정하거나 아래 기본 경로 중 하나에 둔다.
#           ~/tools/antlr.jar
#           ~/antlr/antlr-4.13.2-complete.jar
#           /usr/local/lib/antlr-4.13.2-complete.jar
#           /usr/share/java/antlr4.jar
#
#  ⚠️ jar 버전
#    저장소에 커밋된 생성 소스는 ANTLR 4.13.2 로 만들어진다.
#    다른 버전으로 재생성하면 커밋 diff 가 통째로 뒤집힌다.
#    스크립트가 버전을 확인하므로, 정말 다른 버전을 쓸 때만
#      ANTLR4_EXPECTED_VERSION=<버전> sh ...regenerate_parser.sh
#    로 실행한다.
#
#  사용법
#    sh components/parser/cmake/regenerate_parser.sh
#    ANTLR4_JAR=/path/to/antlr.jar sh components/parser/cmake/regenerate_parser.sh
# =============================================================================

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PARSER_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)     # components/parser
GRAMMAR_DIR="$PARSER_DIR/grammar"
GENERATED_DIR="$PARSER_DIR/generated"

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

if [ -z "${ANTLR4_JAR:-}" ] || [ ! -f "$ANTLR4_JAR" ]; then
    echo "[ERROR] ANTLR jar 를 찾지 못했습니다. ANTLR4_JAR 로 지정하세요." >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] java 실행 파일이 필요합니다." >&2
    exit 1
fi

# jar 버전을 확인한다. 저장소에 커밋된 생성 소스는 4.13.2 로 만들어졌으므로,
# 다른 버전으로 재생성하면 커밋 diff 가 통째로 뒤집힌다(런타임 API 가 달라진다).
# 참고: ~/tools/antlr.jar 라는 이름의 antlr4ng 4.13.2-SNAPSHOT 포크가
#       "Version 4.13.1" 로 보고되는 사례가 있어 이름만 믿으면 안 된다.
ANTLR_VERSION=$(java -jar "$ANTLR4_JAR" 2>&1 | sed -n 's/.*Version \([0-9][0-9.]*\).*/\1/p' | head -1)
EXPECTED_VERSION=${ANTLR4_EXPECTED_VERSION:-4.13.2}

echo "[INFO] jar      : $ANTLR4_JAR"
echo "[INFO] version  : ${ANTLR_VERSION:-unknown} (기대: $EXPECTED_VERSION)"
echo "[INFO] grammars : $GRAMMAR_DIR"
echo "[INFO] output   : $GENERATED_DIR"

if [ -z "$ANTLR_VERSION" ] || [ "$ANTLR_VERSION" != "$EXPECTED_VERSION" ]; then
    echo "[WARN] ANTLR 버전이 $EXPECTED_VERSION 이 아닙니다." >&2
    echo "       저장소의 생성 소스는 $EXPECTED_VERSION 기준이므로 diff 가 크게 뒤집힙니다." >&2
    echo "       계속하려면 ANTLR4_EXPECTED_VERSION=$ANTLR_VERSION 로 실행하세요." >&2
    exit 1
fi

# 생성 디렉터리는 항상 비우고 다시 만든다.
# (이전 버전에서 만들어진 파일이 남아 링크 오류를 내는 것을 막는다)
rm -rf "$GENERATED_DIR/grammar"
mkdir -p "$GENERATED_DIR/grammar"

# 문법마다 Lexer/Parser/Visitor/BaseVisitor 를 생성한다.
# -Dlanguage=Cpp -visitor -no-listener (프로버는 visitor 만 쓴다)
for grammar in "$GRAMMAR_DIR"/*.g4; do
    name=$(basename "$grammar" .g4)
    echo "[INFO] generating $name"

    java -jar "$ANTLR4_JAR" \
        -Dlanguage=Cpp \
        -visitor \
        -no-listener \
        -lib "$GRAMMAR_DIR" \
        -o "$GENERATED_DIR" \
        -Xexact-output-dir \
        "$grammar"

    # ANTLR 은 -o 로 준 디렉터리 바로 아래에 파일을 만든다.
    # 우리는 generated/grammar 에 모으고 싶으므로 생성된 것만 옮긴다.
    for generated in \
        "$GENERATED_DIR/${name}Lexer.cpp" \
        "$GENERATED_DIR/${name}Lexer.h" \
        "$GENERATED_DIR/${name}Parser.cpp" \
        "$GENERATED_DIR/${name}Parser.h" \
        "$GENERATED_DIR/${name}Visitor.cpp" \
        "$GENERATED_DIR/${name}Visitor.h" \
        "$GENERATED_DIR/${name}BaseVisitor.cpp" \
        "$GENERATED_DIR/${name}BaseVisitor.h" \
        "$GENERATED_DIR/${name}.interp" \
        "$GENERATED_DIR/${name}.tokens" \
        "$GENERATED_DIR/${name}Lexer.interp" \
        "$GENERATED_DIR/${name}Lexer.tokens"
    do
        if [ -f "$generated" ]; then
            mv "$generated" "$GENERATED_DIR/grammar/"
        fi
    done
done

echo "[INFO] 완료 — $GENERATED_DIR/grammar"