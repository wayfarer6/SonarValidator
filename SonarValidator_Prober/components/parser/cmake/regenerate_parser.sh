#!/bin/sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
parser_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
grammar_dir="$parser_dir/grammar"
output="$parser_dir/generated/grammar"
expected=$(cat "$parser_dir/../../third_party/antlr4-runtime/VERSION")
if [ ! -f "${ANTLR4_JAR:-}" ]; then
    echo "ANTLR4_JAR is missing. Run: cmake --build <build-dir> --target fetch_antlr_tool" >&2
    exit 1
fi
if ! command -v java >/dev/null 2>&1; then
    echo "Java is required only to regenerate grammars." >&2
    exit 1
fi
actual=$(java -jar "$ANTLR4_JAR" 2>&1 | sed -n 's/.*Version \([0-9][0-9.]*\).*/\1/p' | head -1)
if [ "$actual" != "$expected" ]; then
    echo "ANTLR generator/runtime version mismatch: tool=$actual runtime=$expected" >&2
    exit 1
fi
mkdir -p "$parser_dir/generated"
staging=$(mktemp -d "$parser_dir/generated/.antlr.XXXXXX")
trap 'rm -rf -- "$staging"' EXIT HUP INT TERM
mkdir "$staging/new"
cd "$grammar_dir"
for grammar in ./*.g4; do
    java -jar "$ANTLR4_JAR" -Dlanguage=Cpp -visitor -no-listener \
        -lib "$grammar_dir" -Xexact-output-dir -o "$staging/new" "$(basename "$grammar")"
done
if [ -e "$output" ]; then mv "$output" "$staging/previous"; fi
if ! mv "$staging/new" "$output"; then
    if [ -d "$staging/previous" ]; then mv "$staging/previous" "$output"; fi
    exit 1
fi
echo "Regenerated parsers with ANTLR $expected: $output"
