#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

RUN_BACKEND=0
RUN_FRONTEND=0
RUN_PROBER=0
RUN_DOCKER=0
RUN_BUNDLE=0
INSTALL_DEPS=1

usage() {
  cat <<'EOF'
Usage: ./Test/test.sh [options]

Without a target option, all checks are run.

  --all             Run Backend, Frontend, Prober, Compose, and bundle checks
  --backend         Run Backend tests
  --frontend        Run Frontend type-check, lint, and build
  --prober          Build and test the Prober
  --docker          Validate the Docker Compose model
  --bundle          Build and verify Agent Bundle staging assets
  --no-install      Do not install missing Frontend dependencies
  -h, --help        Show this help
EOF
}

select_all() {
  RUN_BACKEND=1
  RUN_FRONTEND=1
  RUN_PROBER=1
  RUN_DOCKER=1
  RUN_BUNDLE=1
}

if [ "$#" -eq 0 ]; then
  select_all
fi

for arg in "$@"; do
  case "$arg" in
    --all) select_all ;;
    --backend) RUN_BACKEND=1 ;;
    --frontend) RUN_FRONTEND=1 ;;
    --prober) RUN_PROBER=1 ;;
    --docker) RUN_DOCKER=1 ;;
    --bundle) RUN_BUNDLE=1 ;;
    --no-install) INSTALL_DEPS=0 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$arg" >&2; usage >&2; exit 2 ;;
  esac
done

log() { printf '\n[test] %s\n' "$*"; }

antlr_runtime() {
  local candidate
  if [ -n "${ANTLR4_RUNTIME_ROOT:-}" ]; then
    printf '%s\n' "$ANTLR4_RUNTIME_ROOT"
    return
  fi
  for candidate in \
    "$ROOT/.cache/antlr4-install" \
    "${HOME:-}/tools/antlr4-install" \
    /opt/antlr4-install \
    /usr/local \
    /usr; do
    if [ -f "$candidate/lib/libantlr4-runtime.a" ] && \
       [ -d "$candidate/include/antlr4-runtime" ]; then
      printf '%s\n' "$candidate"
      return
    fi
  done
  printf '%s\n' 'ANTLR4 runtime not found; set ANTLR4_RUNTIME_ROOT.' >&2
  return 1
}

configure_prober() {
  local runtime_root
  runtime_root="$(antlr_runtime)"
  cmake -S "$ROOT/Prober" -B "$ROOT/Prober/build-test" \
    -DCMAKE_BUILD_TYPE=Release \
    -DSONAR_STATIC_LINK=ON \
    -DCMAKE_EXE_LINKER_FLAGS=-static \
    -DANTLR4_RUNTIME_ROOT="$runtime_root"
}

if [ "$RUN_BACKEND" = 1 ]; then
  log 'Backend: clean test'
  (cd "$ROOT/Backend" && ./mvnw -B clean test)
fi

if [ "$RUN_FRONTEND" = 1 ]; then
  log 'Frontend: type-check, lint, build'
  if [ ! -d "$ROOT/Frontend/node_modules" ]; then
    if [ "$INSTALL_DEPS" = 0 ]; then
      printf 'Frontend/node_modules is missing and --no-install was specified.\n' >&2
      exit 1
    fi
    (cd "$ROOT/Frontend" && npm ci --no-audit --no-fund)
  fi
  (cd "$ROOT/Frontend" && npx tsc -b && npm run lint && npm run build)
fi

if [ "$RUN_PROBER" = 1 ] || [ "$RUN_BUNDLE" = 1 ]; then
  log 'Prober: configure'
  configure_prober
fi

if [ "$RUN_PROBER" = 1 ]; then
  log 'Prober: build and test'
  cmake --build "$ROOT/Prober/build-test" --parallel "${BUILD_JOBS:-4}"
  ctest --test-dir "$ROOT/Prober/build-test" --output-on-failure
fi

if [ "$RUN_BUNDLE" = 1 ]; then
  log 'Agent Bundle: build and verify assets'
  cmake --build "$ROOT/Prober/build-test" \
    --parallel "${BUILD_JOBS:-4}" --target installer_bundle
  for asset in \
    Installer.sh \
    default_template.sqlite \
    sonar_validator_prober \
    systemd/sonar_validator_prober.service \
    systemd/sonar_validator_prober-cisco.service \
    rc-service/sonar_validator_prober; do
    test -s "$ROOT/Prober/Installer/$asset" || {
      printf 'Missing Agent Bundle asset: Prober/Installer/%s\n' "$asset" >&2
      exit 1
    }
  done
fi

if [ "$RUN_DOCKER" = 1 ]; then
  log 'Docker Compose: validate configuration'
  (cd "$ROOT" && docker compose -f docker/compose/compose.yaml config --quiet)
fi

log 'All selected checks passed.'
