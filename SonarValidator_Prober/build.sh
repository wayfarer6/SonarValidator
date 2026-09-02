#!/bin/sh

set -eu

cmake -S . -B build
cmake --build build
ctest --test-dir build --output-on-failure
exec ./build/sonar_validator_prober