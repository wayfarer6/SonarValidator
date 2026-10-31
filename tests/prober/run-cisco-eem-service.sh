#!/usr/bin/env bash
set -euo pipefail
build=${1:-/tmp/sonar-lab1-static-build}
cmake --build "$build" --target sonar_test_ios_eem_service_test -j1
ctest --test-dir "$build" --output-on-failure -R '^ios_eem_service_test$' --no-tests=error
