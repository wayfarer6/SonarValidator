#!/usr/bin/env bash
# dev.sh 의 .env 로더만 독립 실행해 동작을 검증한다.
set -euo pipefail
ROOT="/home/osboxes/SonarValidator"
log() { printf '[dev] %s\n' "$*"; }

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
    value="${value%\"}"; value="${value#\"}"
    value="${value%\'}"; value="${value#\'}"
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

# dev.sh 처럼 포트는 이미 설정된 상태를 가정
FRONTEND_PORT=5173
BACKEND_PORT=3000
SONAR_POLICY_AUTOMATIC_ENFORCEMENT_ENABLED=false   # 명령줄 override

load_env_file "$ROOT/.env"

echo "FRONTEND_PORT=$FRONTEND_PORT   (기대 5173: .env 의 80 무시)"
echo "BACKEND_PORT=$BACKEND_PORT    (기대 3000)"
echo "POLICY=$SONAR_POLICY_AUTOMATIC_ENFORCEMENT_ENABLED   (기대 false: 명령줄 우선)"
echo "CORS=$SONAR_CORS_ALLOWED_ORIGINS"
echo "ADMIN_PW_SET=$([ -n "${SONAR_ADMIN_PASSWORD:-}" ] && echo yes || echo no)"
echo "SECRET_SET=$([ -n "${SONAR_SECRET_KEY:-}" ] && echo yes || echo no)"
