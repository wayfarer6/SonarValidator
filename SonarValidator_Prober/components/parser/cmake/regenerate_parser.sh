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

echo "[INFO] jar      : $ANTLR4_JAR"
echo "[INFO] grammars : $GRAMMAR_DIR"
echo "[INFO] output   : $GENERATED_DIR"

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