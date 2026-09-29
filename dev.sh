#!/usr/bin/env bash
#
# dev.sh — SonarValidator 로컬 개발 스택 실행 스크립트
#
# Agent(Prober) 없이 Frontend + Backend 만 띄워서 개발/테스트합니다.
#
#   Backend  : Spring Boot (H2 파일 DB, local 프로필)   -> http://localhost:3000
#   Frontend : Vite dev server                          -> http://localhost:5173
#   Debug    : JDWP (suspend=n)                         -> localhost:5005 attach
#
# 사용법:
#   ./dev.sh                 # 프론트 + 백엔드(디버그) 둘 다 실행
#   ./dev.sh --no-debug      # 디버그 포트 없이 실행
#   ./dev.sh --backend-only  # 백엔드만
#   ./dev.sh --frontend-only # 프론트만
#   ./dev.sh --no-install    # node_modules 없을 때 npm install 건너뛰기
#
# 환경변수로 포트 변경:
#   BACKEND_PORT=3300 FRONTEND_PORT=5174 DEBUG_PORT=5006 ./dev.sh
#
# Ctrl+C 한 번으로 두 프로세스가 함께 종료됩니다.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT/SonarValidator_Backend"
FRONTEND_DIR="$ROOT/SonarValidator_Frontend"

BACKEND_PORT="${BACKEND_PORT:-3000}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
DEBUG_PORT="${DEBUG_PORT:-5005}"

RUN_BACKEND=1
RUN_FRONTEND=1
DO_INSTALL=1
DEBUG=1

c_reset=$'\033[0m'; c_dim=$'\033[2m'; c_cyan=$'\033[36m'
c_green=$'\033[32m'; c_yellow=$'\033[33m'; c_red=$'\033[31m'

usage() { awk 'NR>2 { if ($0 !~ /^#/) exit; sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"; }

for arg in "$@"; do
  case "$arg" in
    --backend-only)  RUN_FRONTEND=0 ;;
    --frontend-only) RUN_BACKEND=0 ;;
    --no-install)    DO_INSTALL=0 ;;
    --no-debug)      DEBUG=0 ;;
    -h|--help)       usage; exit 0 ;;
    *) printf '%s알 수 없는 옵션: %s%s\n' "$c_red" "$arg" "$c_reset" >&2; usage; exit 2 ;;
  esac
done

log()  { printf '%s[dev]%s %s\n' "$c_cyan" "$c_reset" "$*"; }
warn() { printf '%s[dev]%s %s\n' "$c_yellow" "$c_reset" "$*"; }
die()  { printf '%s[dev]%s %s\n' "$c_red" "$c_reset" "$*" >&2; exit 1; }

port_in_use() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

# --- 사전 점검 --------------------------------------------------------------
if [ "$RUN_BACKEND" = 1 ]; then
  [ -x "$BACKEND_DIR/mvnw" ] || die "Maven wrapper 를 찾을 수 없습니다: $BACKEND_DIR/mvnw"
  command -v java >/dev/null 2>&1 || die "java 를 찾을 수 없습니다. JDK 26 을 설치하세요."
  java_major="$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)"
  if [ "${java_major:-0}" -lt 26 ] 2>/dev/null; then
    warn "현재 Java ${java_major} 입니다. 이 프로젝트는 JDK 26 을 사용합니다."
  fi
  if port_in_use "$BACKEND_PORT"; then
    die "포트 $BACKEND_PORT 이(가) 이미 사용 중입니다. (docker compose 로 띄운 백엔드일 수 있습니다)
     BACKEND_PORT=3300 ./dev.sh 처럼 다른 포트를 지정하거나 기존 프로세스를 종료하세요."
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