#!/usr/bin/env bash
set -euo pipefail
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
prober="$repo/SonarValidator_Prober"
build=$(mktemp -d /tmp/sonar-prober-tests.XXXXXX)
trap 'rm -rf -- "$build"' EXIT
compiler=${CXX:-g++}
flags=(-std=c++23 -O1 -g -pthread -I "$prober")
"$compiler" "${flags[@]}" "$prober/database/database_service_test.cpp" "$prober/database/database_service.cpp" -lsqlite3 -o "$build/db-events"
"$compiler" "${flags[@]}" "$prober/database/telemetry_store_test.cpp" "$prober/database/database_service.cpp" "$prober/database/telemetry_store.cpp" "$prober/database/schema.cpp" -lsqlite3 -o "$build/telemetry-store"
"$compiler" "${flags[@]}" "$prober/components/terminal/terminal_session_test.cpp" "$prober/components/terminal/terminal_session.cpp" -o "$build/pty-lifecycle"
"$compiler" "${flags[@]}" "$prober/components/terminal/ios_cli_test.cpp" -o "$build/ios-cli"
"$compiler" "${flags[@]}" "$prober/components/terminal/ios_snapshot_test.cpp" -o "$build/ios-snapshot"
for test in db-events telemetry-store pty-lifecycle ios-cli ios-snapshot; do
  echo "Running $test"
  timeout 20s "$build/$test"
done

"$repo/tests/prober/ios-snapshot-publisher.sh"
