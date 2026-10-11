#!/usr/bin/env bash
#
# dev.sh — SonarValidator 로컬 개발 스택 실행 스크립트
#
# Frontend + Backend 를 띄우고 Agent 다운로드용 번들 자산도 준비합니다.
#
#   Backend  : Spring Boot (H2 파일 DB, local 프로필)   -> http://localhost:3000
#   Frontend : Vite dev server                          -> http://localhost:5173
#   Debug    : JDWP (suspend=n)                         -> localhost:5005 attach
#
# 사용법:
#   ./Test/dev.sh                 # 프론트 + 백엔드(디버그) 둘 다 실행
#   ./Test/dev.sh --no-debug      # 디버그 포트 없이 실행
#   ./Test/dev.sh --backend-only  # 백엔드만
#   ./Test/dev.sh --frontend-only # 프론트만
#   ./Test/dev.sh --no-install    # node_modules 없을 때 npm install 건너뛰기
#   ./Test/dev.sh --no-agent-bundle # Agent 번들 준비를 건너뛰기
#
# 환경변수로 포트 변경:
#   BACKEND_PORT=3300 FRONTEND_PORT=5174 DEBUG_PORT=5006 ./Test/dev.sh
#
# 루트의 .env 가 있으면 자동으로 로드합니다(CORS·시크릿·정책 적용 플래그 등).
# 이미 설정된 환경변수와 dev.sh 가 직접 관리하는 포트 값은 .env 로 덮어쓰지 않습니다.
#   ./Test/dev.sh --no-env        # .env 로드를 건너뛰기
#
# Ctrl+C 한 번으로 두 프로세스가 함께 종료됩니다.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$ROOT/Backend"
FRONTEND_DIR="$ROOT/Frontend"

BACKEND_PORT="${BACKEND_PORT:-3000}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
DEBUG_PORT="${DEBUG_PORT:-5005}"

RUN_BACKEND=1
RUN_FRONTEND=1
DO_INSTALL=1
DEBUG=1
PREPARE_AGENT_BUNDLE=1
LOAD_ENV=1

c_reset=$'\033[0m'; c_dim=$'\033[2m'; c_cyan=$'\033[36m'
c_green=$'\033[32m'; c_yellow=$'\033[33m'; c_red=$'\033[31m'

usage() { awk 'NR>2 { if ($0 !~ /^#/) exit; sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"; }

for arg in "$@"; do
  case "$arg" in
    --backend-only)  RUN_FRONTEND=0 ;;
    --frontend-only) RUN_BACKEND=0 ;;
    --no-install)    DO_INSTALL=0 ;;
    --no-debug)      DEBUG=0 ;;
    --no-agent-bundle) PREPARE_AGENT_BUNDLE=0 ;;
    --no-env)        LOAD_ENV=0 ;;
    -h|--help)       usage; exit 0 ;;
    *) printf '%s알 수 없는 옵션: %s%s\n' "$c_red" "$arg" "$c_reset" >&2; usage; exit 2 ;;
  esac
done

log()  { printf '%s[dev]%s %s\n' "$c_cyan" "$c_reset" "$*"; }
warn() { printf '%s[dev]%s %s\n' "$c_yellow" "$c_reset" "$*"; }
die()  { printf '%s[dev]%s %s\n' "$c_red" "$c_reset" "$*" >&2; exit 1; }

port_in_use() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

# 루트 .env 를 환경변수로 로드한다.
#   - 같은 이름의 환경변수가 이미 있으면 그대로 둔다(명령줄 override 우선).
#   - dev.sh 가 직접 관리하는 포트/디버그 값은 .env 로 덮어쓰지 않는다.
#     (.env 의 FRONTEND_PORT=80 과 BACKEND_PORT=3000 은 docker compose 용이라
#      개발 서버 포트와 충돌한다)
load_env_file() {
  local file="$1" line key value
  local loaded=()
  [ -f "$file" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in '' | \#*) continue ;; esac
    line="${line#export }"
    case "$line" in *=*) ;; *) continue ;; esac
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in [A-Za-z_]*) ;; *) continue ;; esac
    # 값 양끝의 따옴표 제거
    value="${value%\"}"; value="${value#\"}"
    value="${value%\'}"; value="${value#\'}"
    # dev.sh 가 직접 관리하는 포트/디버그 값은 .env(docker 용 80/3000)로 덮어쓰지 않는다.
    # VITE_* 도 제외한다: .env 의 VITE_API_BASE_URL= (빈 값)은 nginx 프록시용이라,
    # dev(Vite :5173)에서는 상대경로 요청이 되어 404 가 난다. 프론트는 기본값
    # (http://<host>:3000)을 쓰게 둔다.
    case "$key" in
      BACKEND_PORT | FRONTEND_PORT | DEBUG_PORT | VITE_*) continue ;;
    esac
    if [ -z "${!key+x}" ]; then
      export "$key=$value"
      loaded+=("$key")
    fi
  done < "$file"
  if [ "${#loaded[@]}" -gt 0 ]; then
    log ".env 로드: ${loaded[*]}"
  fi
}

if [ "$LOAD_ENV" = 1 ]; then
  load_env_file "$ROOT/.env"
fi

resolve_antlr_runtime() {
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
  return 1
}

stage_agent_bundle() {
  local runtime_root
  runtime_root="$(resolve_antlr_runtime)" || die \
    "ANTLR4 C++ 런타임을 찾을 수 없습니다. ANTLR4_RUNTIME_ROOT를 지정하거나 --no-agent-bundle을 사용하세요."

  log "Agent Bundle 빌드 및 스테이징..."
  cmake -S "$PROBER_DIR" -B "$PROBER_BUILD_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DSONAR_STATIC_LINK=ON \
    -DCMAKE_EXE_LINKER_FLAGS=-static \
    -DANTLR4_RUNTIME_ROOT="$runtime_root"
  cmake --build "$PROBER_BUILD_DIR" \
    --parallel "${BUILD_JOBS:-4}" --target installer_bundle

  install -d "$STAGE_DIR/systemd" "$STAGE_DIR/rc-service"
  install -m 755 "$PROBER_DIR/Installer/sonar_validator_prober" \
    "$STAGE_DIR/sonar_validator_prober"
  install -m 755 "$PROBER_DIR/Installer/Installer.sh" "$STAGE_DIR/Installer.sh"
  install -m 644 "$PROBER_DIR/Installer/default_template.sqlite" \
    "$STAGE_DIR/default_template.sqlite"
  install -m 644 "$PROBER_DIR/Installer/systemd/sonar_validator_prober.service" \
    "$STAGE_DIR/systemd/sonar_validator_prober.service"
  install -m 644 "$PROBER_DIR/Installer/systemd/sonar_validator_prober-cisco.service" \
    "$STAGE_DIR/systemd/sonar_validator_prober-cisco.service"
  install -m 755 "$PROBER_DIR/Installer/rc-service/sonar_validator_prober" \
    "$STAGE_DIR/rc-service/sonar_validator_prober"
}

configure_terminal_secret() {
  local secret_file="$ROOT/.runtime/terminal-secret"
  local configured="${SONAR_TERMINAL_SHARED_SECRET:-}"

  if [ -z "$configured" ] && [ -f "$ROOT/.env" ]; then
    configured="$(sed -n 's/^SONAR_TERMINAL_SHARED_SECRET=//p' "$ROOT/.env" | tail -n 1)"
  fi
  if [ -z "$configured" ] && [ -f "$secret_file" ]; then
    configured="$(cat "$secret_file")"
  fi
  if [ "${#configured}" -lt 32 ]; then
    command -v openssl >/dev/null 2>&1 || die \
      "Agent Bundle용 비밀값이 필요합니다. SONAR_TERMINAL_SHARED_SECRET에 32자 이상을 지정하세요."
    install -d -m 700 "$ROOT/.runtime"
    configured="$(openssl rand -hex 32)"
    printf '%s\n' "$configured" > "$secret_file"
    chmod 600 "$secret_file"
    log "개발용 Agent 터미널 비밀값을 .runtime/terminal-secret에 생성했습니다."
  fi
  export SONAR_TERMINAL_SHARED_SECRET="$configured"
}

# --- 사전 점검 --------------------------------------------------------------
if [ "$RUN_BACKEND" = 1 ]; then
  [ -x "$BACKEND_DIR/mvnw" ] || die "Maven wrapper 를 찾을 수 없습니다: $BACKEND_DIR/mvnw"
  command -v java >/dev/null 2>&1 || die "java 를 찾을 수 없습니다. JDK 26 을 설치하세요."
  java_major="$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)"
  if [ "${java_major:-0}" -lt 26 ] 2>/dev/null; then
    warn "현재 Java ${java_major} 입니다. 이 프로젝트는 JDK 26 을 사용합니다."
  fi
  if port_in_use "$BACKEND_PORT"; then
    die "포트 $BACKEND_PORT 이(가) 이미 사용 중입니다. (docker compose -f docker/compose/compose.yaml 로 띄운 백엔드일 수 있습니다)
     BACKEND_PORT=3300 ./Test/dev.sh 처럼 다른 포트를 지정하거나 기존 프로세스를 종료하세요."
  fi

  if [ "$PREPARE_AGENT_BUNDLE" = 1 ]; then
    PROBER_DIR="$ROOT/Prober"
    PROBER_BUILD_DIR="${PROBER_BUILD_DIR:-$PROBER_DIR/build-dev}"
    STAGE_DIR="${SONAR_DEPLOY_STAGE_DIR:-/tmp/sonar_stage}"
    [ -f "$PROBER_DIR/CMakeLists.txt" ] || die "Prober 소스 디렉터리를 찾을 수 없습니다: $PROBER_DIR"
    command -v cmake >/dev/null 2>&1 || die "cmake 가 없어 Agent Bundle을 준비할 수 없습니다."
    stage_agent_bundle
    configure_terminal_secret
    export SONAR_DEPLOY_STAGE_DIR="$STAGE_DIR"
  fi
fi

if [ "$RUN_FRONTEND" = 1 ]; then
  command -v npm >/dev/null 2>&1 || die "npm 을 찾을 수 없습니다. Node 20+ 를 설치하세요."
  port_in_use "$FRONTEND_PORT" && \
    warn "포트 $FRONTEND_PORT 이(가) 이미 사용 중입니다. Vite 가 다른 포트를 자동 선택합니다."
fi

# --- 종료 처리 (백그라운드 job = 자체 프로세스 그룹 → 자식까지 함께 종료) ----
set -m
BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  trap - INT TERM EXIT
  printf '\n'
  log "종료 중..."
  for pid in "$FRONTEND_PID" "$BACKEND_PID"; do
    [ -n "$pid" ] || continue
    kill -TERM "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
  log "정지 완료."
}
trap cleanup INT TERM EXIT

# --- Frontend ---------------------------------------------------------------
if [ "$RUN_FRONTEND" = 1 ]; then
  if [ "$DO_INSTALL" = 1 ] && [ ! -d "$FRONTEND_DIR/node_modules" ]; then
    log "${c_green}Frontend${c_reset} 의존성 설치 (npm install)..."
    ( cd "$FRONTEND_DIR" && npm install --no-audit --no-fund )
  fi
  log "${c_green}Frontend${c_reset} 시작 → http://localhost:$FRONTEND_PORT"
  ( cd "$FRONTEND_DIR" && exec npm run dev -- --host --port "$FRONTEND_PORT" --strictPort ) &
  FRONTEND_PID=$!
fi

# --- Backend ----------------------------------------------------------------
jvm_args=""
if [ "$RUN_BACKEND" = 1 ]; then
  if [ "$DEBUG" = 1 ]; then
    if port_in_use "$DEBUG_PORT"; then
      warn "디버그 포트 $DEBUG_PORT 이(가) 이미 사용 중입니다. 디버거 없이 실행합니다."
    else
      jvm_args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$DEBUG_PORT"
      log "${c_green}Backend${c_reset} 디버그 포트 열림 → localhost:$DEBUG_PORT (suspend=n)"
    fi
  fi

  log "${c_green}Backend${c_reset} 시작 → http://localhost:$BACKEND_PORT (H2 · local 프로필)"
  ( cd "$BACKEND_DIR" && exec ./mvnw -q spring-boot:run \
      -Dspring-boot.run.profiles=local \
      -Dspring-boot.run.jvmArguments="$jvm_args" \
      -Dspring-boot.run.arguments="--server.port=$BACKEND_PORT" ) &
  BACKEND_PID=$!

  # 기동 대기 (최초 실행은 Maven 의존성 다운로드로 오래 걸림)
  log "백엔드 기동 대기 중... (${c_dim}Ctrl+C 로 중단 가능, 최초 실행은 수 분${c_reset})"
  ready=0
  for _ in $(seq 1 180); do
    if curl -fsS -o /dev/null "http://localhost:$BACKEND_PORT/actuator/health"; then
      ready=1; break
    fi
    kill -0 "$BACKEND_PID" 2>/dev/null || break
    sleep 1
  done
  if [ "$ready" = 1 ]; then
    log "${c_green}백엔드 준비 완료${c_reset} — H2 콘솔: http://localhost:$BACKEND_PORT/h2-console"
  else
    warn "백엔드가 180초 안에 응답하지 않았습니다. 위 로그를 확인하세요."
  fi
fi

# --- 요약 -------------------------------------------------------------------
printf '\n'
log "실행 중인 서비스:"
[ "$RUN_BACKEND" = 1 ]  && printf '       Backend  : %shttp://localhost:%s%s\n' "$c_dim" "$BACKEND_PORT" "$c_reset"
[ "$RUN_FRONTEND" = 1 ] && printf '       Frontend : %shttp://localhost:%s%s\n' "$c_dim" "$FRONTEND_PORT" "$c_reset"
if [ "$RUN_BACKEND" = 1 ] && [ "$RUN_FRONTEND" = 1 ] && [ -n "$jvm_args" ]; then
  printf '       Debugger : %slocalhost:%s (suspend=n)%s\n' "$c_dim" "$DEBUG_PORT" "$c_reset"
elif [ "$RUN_BACKEND" = 1 ] && [ -n "$jvm_args" ]; then
  printf '       Debugger : %slocalhost:%s (suspend=n)%s\n' "$c_dim" "$DEBUG_PORT" "$c_reset"
fi
printf '       %s종료: Ctrl+C%s\n\n' "$c_dim" "$c_reset"

wait
