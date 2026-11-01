#!/bin/sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
build_dir=${SONAR_BUILD_DIR:-"$script_dir/build"}
jobs=${SONAR_BUILD_JOBS:-2}
run=false
case ${1:-} in
    --run) run=true; shift ;;
    --help)
        echo "Usage: $0 [--run] [CMake configure arguments]"
        echo "SONAR_BUILD_DIR, SONAR_BUILD_JOBS (default 2), SONAR_BUILD_TYPE (default Release)"
        exit 0 ;;
esac
case $jobs in ''|*[!0-9]*) echo "SONAR_BUILD_JOBS must be a positive integer" >&2; exit 2 ;; esac
if [ "$jobs" -le 0 ]; then echo "SONAR_BUILD_JOBS must be positive" >&2; exit 2; fi
cmake -S "$script_dir" -B "$build_dir" -DCMAKE_BUILD_TYPE="${SONAR_BUILD_TYPE:-Release}" "$@"
cmake --build "$build_dir" --parallel "$jobs"
ctest --test-dir "$build_dir" --output-on-failure --no-tests=error
if [ "$run" = true ]; then exec "$build_dir/sonar_validator_prober"; fi
