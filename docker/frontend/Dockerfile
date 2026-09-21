# syntax=docker/dockerfile:1.7
# =============================================================================
# SonarValidator Frontend (Vite + React 19) — nginx 로 서빙
# =============================================================================
#
#   1) builder : node:24-alpine 에서 npm ci + vite build
#   2) runtime : nginx:alpine 에 정적 산출물만 복사
#
# 사용자가 요청한 대로 최종 서버는 nginx 컨테이너 베이스 이미지다.
# node 는 빌드 단계에만 쓰이고 최종 이미지에는 들어가지 않는다.
#
# 빌드 (저장소 루트에서)
#   docker build -t sonar-validator/frontend:latest -f docker/frontend/Dockerfile .
# =============================================================================

# -----------------------------------------------------------------------------
# 1) builder
# -----------------------------------------------------------------------------
FROM node:24-alpine AS builder

WORKDIR /app

# package.json + lock 파일을 먼저 복사해 의존성 레이어를 캐시한다.
# npm ci 는 lock 파일 기준으로 설치하므로 재현 가능하고 npm install 보다 빠르다.
COPY SonarValidator_Frontend/package.json SonarValidator_Frontend/package-lock.json ./

RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY SonarValidator_Frontend/ ./

# ⚠️ VITE_ 접두사 변수는 빌드 시점에 코드로 "치환"된다. 런타임 환경변수로는
#    바꿀 수 없다. 그래서 nginx 프록시와 짝을 이루도록 여기서 정한다.
#
# 기본값을 빈 문자열로 두는 이유:
#   이 값을 비워 두면 프론트는 API 를 "/api/v1/..." 같은 상대 경로로 호출하고,
#   nginx(같은 출처)가 그것을 backend:3000 으로 넘긴다.
#   → 브라우저 입장에서 CORS 가 아예 발생하지 않고, 세션 쿠키(JSESSIONID)도
#     같은 출처라 그대로 오간다. 포트를 바꿔 노출해도 프론트를 다시 굽지
#     않아도 된다.
#
#   "http://api.example.com" 처럼 절대 URL 을 넣으면 그 주소가 코드에
#   박히고, 백엔드의 sonar.cors.allowed-origins 에 프론트 출처를 추가해야 한다.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build && ls -la dist

# -----------------------------------------------------------------------------
# 2) runtime (nginx)
# -----------------------------------------------------------------------------
FROM nginx:1.29-alpine AS runtime

# 공식 nginx 이미지의 기본 conf.d/default.conf 를 우리 것으로 교체한다.
# (SPA fallback + /api 프록시 + 업로드 크기 + gzip)
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=builder /app/dist /usr/share/nginx/html

# 프론트는 nginx 가 80 으로 받는다. 호스트 포트는 compose 에서 정한다.
EXPOSE 80

# nginx:alpine 기본 CMD 가 nginx 를 foreground 로 띄운다.
# 시그널 처리도 이미지 기본값이 올바르므로 그대로 둔다.
