# syntax=docker/dockerfile:1.7
# =============================================================================
# SonarValidator Backend (Spring Boot 4.1.1 / Java 26)
# =============================================================================
#
# 3개 빌드 단계로 나눈다 (builder 는 사전에 만든 BASE_IMAGE 를 사용).
#   1) prober-builder : C++23 Prober(Agent) 정적 바이너리 빌드
#   2) builder        : 소스를 컴파일해 실행 가능한 fat jar 생성 (바이너리 스테이징 포함)
#   3) runtime        : JDK 와 번들 생성에 필요한 프로버 바이너리를 담은 Ubuntu
#
# 런타임에 Maven 은 필요 없다. base 이미지에서 /opt/java 만 복사해 오므로
# 최종 이미지에는 Maven 과 apt 캐시가 들어가지 않는다.
#
# Prober 바이너리 스테이징:
# 정적 링크 바이너리(`build_static/sonar_validator_prober`)는 빌드 산출물로
# 저장소에 커밋되지 않지만, 배포 시 번들에 포함시켜야 하므로 여기서
# 구성 단계에 구성한다. 그래서 최종 jar 는 `/tmp/sonar_stage` 의
# 바이너리를 함께 포함한 설치 번들을 만들 수 있다. 런타임에서도 번들 요청 때
# 파일을 읽어야 하므로 최종 이미지의 동일 경로로 복사한다.
#
# 빌드 (저장소 루트에서)
#   docker build -t sonar-validator/backend:latest -f docker/backend/Dockerfile .
#
# 선행 조건: 베이스 이미지가 먼저 구워져 있어야 한다.
#   docker build -t sonar-validator/jdk26-base:26.0.2.1 docker/base
# =============================================================================

# Prober 정적 바이너리 빌드용 베이스 이미지
# 개발 환경에 맞춘 gcc/cmake 등을 포함합니다.
ARG BUILDER_IMAGE=ubuntu:24.04
ARG BASE_IMAGE=sonar-validator/jdk26-base:26.0.2.1

# Prober 빌드 스테이지
FROM ${BUILDER_IMAGE} AS prober-builder

RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates build-essential g++ cmake \
      curl unzip \
      libsqlite3-dev libssl-dev \
      libboost-dev nlohmann-json3-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# ANTLR4 C++ 런타임을 받아 빌드합니다.
# 버전은 프로버의 생성 소스와 반드시 같아야 합니다.
ARG ANTLR4_VERSION=4.13.2
RUN mkdir -p /opt/antlr4-install && \
    curl -fsSL -o /tmp/antlr4-cxx.zip \
      "https://www.antlr.org/download/antlr4-cpp-runtime-${ANTLR4_VERSION}-source.zip" && \
    unzip -q /tmp/antlr4-cxx.zip -d /tmp/antlr4-src && \
    cd /tmp/antlr4-src && \
    cmake -B build \
      -DCMAKE_BUILD_TYPE=Release \
      -DCMAKE_INSTALL_PREFIX=/opt/antlr4-install && \
    cmake --build build --parallel $(nproc) && \
    cmake --install build

# Prober 소스를 복사하고 정적 바이너리를 빌드합니다.
COPY SonarValidator_Prober/ ./

RUN cmake -S . -B build_static \
      -DCMAKE_BUILD_TYPE=Release \
      -DSONAR_STATIC_LINK=ON \
  -DCMAKE_EXE_LINKER_FLAGS=-static \
      -DANTLR4_RUNTIME_ROOT=/opt/antlr4-install && \
    cmake --build build_static --target installer_bundle --parallel $(nproc) && \
    mkdir -p /tmp/sonar_stage && \
    cp -R Installer/Installer.sh Installer/default_template.sqlite Installer/sonar_validator_prober Installer/systemd Installer/rc-service /tmp/sonar_stage/ && \
    ls -la /tmp/sonar_stage/

# Backend builder 스테이지
FROM ${BASE_IMAGE} AS builder

WORKDIR /build

# 스테이징 디렉터리를 준비합니다 (Prober 에서 복사해올 준비).
RUN mkdir -p /tmp/sonar_stage

# Prober 바이너리를 이전 단계에서 복사합니다.
COPY --from=prober-builder /tmp/sonar_stage/ /tmp/sonar_stage/

# 의존성만 먼저 받아 캐시를 만든다. 소스만 바뀌었을 때 Maven 이
# 전체를 다시 내려받지 않도록 pom.xml 을 먼저 복사한다.
#
# dependency:go-offline 은 일부 플러그인을 완벽히 받지 못할 수 있어 실패를
# 허용한다. 어차피 다음 단계의 package 가 필요한 것을 채운다.
COPY SonarValidator_Backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline || true

# ANTLR 문법이 src/main/antlr4 가 아닌 src/main/java/.../ANTLR4 에 있고
# .antlr/ 생성물은 손으로 만든 파서가 쓰지 않으므로, antlr4-maven-plugin 은
# "No ANTLR 4 grammars to compile" 으로 넘어간다. (정상)
COPY SonarValidator_Backend/ ./

ARG SKIP_TESTS=true
RUN --mount=type=cache,target=/root/.m2 \
    if [ "$SKIP_TESTS" = "true" ]; then \
      mvn -B clean package -DskipTests; \
    else \
      mvn -B clean package; \
    fi \
 && ls -l target/*.jar

# Backend runtime 스테이지
# ubuntu:24.04 는 base 이미지와 같은 배포판이라 glibc 호환이 보장된다.
FROM ubuntu:24.04 AS runtime

ARG DEBIAN_FRONTEND=noninteractive

# curl  : 컨테이너 healthcheck (Actuator /actuator/health 호출)
# tini  : PID 1 로 두어 compose stop 의 SIGTERM 을 java 까지 전달한다
# tzdata: TZ=Asia/Seoul 적용
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      tini \
      tzdata \
 && rm -rf /var/lib/apt/lists/*

# JDK 만 가져온다. Maven 은 복사하지 않는다.
COPY --from=builder /opt/java /opt/java

# 번들 생성은 런타임 요청 시 실행되므로 빌드 스테이지에만 두지 않고
# 서비스의 기본 스테이징 경로에도 프로버 바이너리를 복사한다.
COPY --from=builder /tmp/sonar_stage/ /tmp/sonar_stage/

ENV JAVA_HOME=/opt/java
ENV PATH="/opt/java/bin:${PATH}"
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV TZ=Asia/Seoul

# UID/GID 10001 — ubuntu:24.04 에 이미 있는 1000(ubuntu) 과 충돌하지 않게 한다.
RUN groupadd --gid 10001 appgroup \
 && useradd --uid 10001 --gid 10001 --create-home --shell /bin/bash appuser

WORKDIR /app

# jar 소유자를 실행 사용자로 맞춘다. root 소유로 두면 appuser 가 읽지 못한다.
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar /app/app.jar

USER appuser

# C++ Prober 와 프론트(nginx) 가 접속하는 포트.
# application.properties 의 server.port=3000 과 일치한다.
EXPOSE 3000

# JVM 을 컨테이너 메모리에 맞춘다. MaxRAMPercentage 를 쓰면 힙을 이미지에
# 하드코딩하지 않아도 되고, 컨테이너 limit 을 바꿔도 그대로 따라간다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul"

# sh -c 로 감싸야 JAVA_OPTS/SPRING_ARGS 환경변수가 확장된다.
# exec 를 붙여 java 가 sh 를 대체하도록 해 시그널이 tini 로부터 바로 전달되게 한다.
ENTRYPOINT ["/usr/bin/tini", "--", "/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar $SPRING_ARGS"]
