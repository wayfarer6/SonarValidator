# syntax=docker/dockerfile:1.7
# =============================================================================
# SonarValidator Backend (Spring Boot 4.1.1 / Java 26)
# =============================================================================
#
# 3단계로 나눈다.
#   1) base    : docker/base 에서 직접 구운 JDK 26 + Maven 이미지
#   2) builder : 소스를 컴파일해 실행 가능한 fat jar 생성
#   3) runtime : JDK 만 담은 최소 Ubuntu + 비root 사용자
#
# 런타임에 Maven 은 필요 없다. base 이미지에서 /opt/java 만 복사해 오므로
# 최종 이미지에는 Maven 과 apt 캐시가 들어가지 않는다.
#
# 빌드 (저장소 루트에서)
#   docker build -t sonar-validator/backend:latest -f docker/backend/Dockerfile .
#
# 선행 조건: 베이스 이미지가 먼저 구워져 있어야 한다.
#   docker build -t sonar-validator/jdk26-base:26.0.2.1 docker/base
# =============================================================================

ARG BASE_IMAGE=sonar-validator/jdk26-base:26.0.2.1

# -----------------------------------------------------------------------------
# 1) builder
# -----------------------------------------------------------------------------
FROM ${BASE_IMAGE} AS builder

WORKDIR /build

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

# -----------------------------------------------------------------------------
# 2) runtime
# -----------------------------------------------------------------------------
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
