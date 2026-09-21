#!/usr/bin/env bash
# =============================================================================
# SonarValidator — 전체 이미지 빌드
# =============================================================================
# 순서가 중요하다.
#   1) JDK 26 베이스 이미지 (backend 가 FROM 으로 참조)
#   2) backend / frontend (베이스가 있어야 빌드 가능)
#
# 사용법:
#   ./docker/scripts/build-all.sh              # 기본 (테스트 건너뜀)
#   SKIP_TESTS=false ./docker/scripts/build-all.sh   # 테스트 포함
# =============================================================================
set -euo pipefail

# 이 스크립트가 어디에 있든 저장소 루트에서 실행되게 한다.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ROOT_DIR}"

BASE_IMAGE="${BASE_IMAGE:-sonar-validator/jdk26-base:26.0.2.1}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
SKIP_TESTS="${SKIP_TESTS:-true}"

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

# -----------------------------------------------------------------------------
# 사전 확인
# -----------------------------------------------------------------------------
command -v docker >/dev/null 2>&1 || { echo "docker 가 없습니다." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "docker 데몬에 연결할 수 없습니다." >&2; exit 1; }

log "Docker: $(docker version --format '{{.Server.Version}}')"

# -----------------------------------------------------------------------------
# 1) 베이스 이미지
# -----------------------------------------------------------------------------
log "1/3 JDK 26 베이스 이미지 빌드 → ${BASE_IMAGE}"
DOCKER_BUILDKIT=1 docker build \
  -t "${BASE_IMAGE}" \
  -f docker/base/Dockerfile \
  docker/base

# -----------------------------------------------------------------------------
# 2) 백엔드
# -----------------------------------------------------------------------------
log "2/3 백엔드 이미지 빌드 (SKIP_TESTS=${SKIP_TESTS})"
log "    첫 빌드는 Maven 의존성을 내려받느라 수 분 걸릴 수 있습니다."
DOCKER_BUILDKIT=1 docker build \
  -t "sonar-validator/backend:${IMAGE_TAG}" \
  -f docker/backend/Dockerfile \
  --build-arg "BASE_IMAGE=${BASE_IMAGE}" \
  --build-arg "SKIP_TESTS=${SKIP_TESTS}" \
  .

# -----------------------------------------------------------------------------
# 3) 프론트엔드
# -----------------------------------------------------------------------------
log "3/3 프론트엔드 이미지 빌드 (nginx)"
DOCKER_BUILDKIT=1 docker build \
  -t "sonar-validator/frontend:${IMAGE_TAG}" \
  -f docker/frontend/Dockerfile \
  .

# -----------------------------------------------------------------------------
# 결과
# -----------------------------------------------------------------------------
log "빌드 완료"
docker images --format 'table {{.Repository}}\t{{.Tag}}\t{{.Size}}' \
  | grep -E 'REPOSITORY|sonar-validator' || true

cat <<'EOF'

다음 단계:
  cp .env.example .env        # 최초 1회
  docker compose up -d
  docker compose ps
EOF
