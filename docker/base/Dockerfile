# syntax=docker/dockerfile:1.7
# =============================================================================
# SonarValidator 공통 베이스 이미지
#   Ubuntu 24.04 (noble) + Eclipse Temurin JDK 26 + Apache Maven 3.9.16
# =============================================================================
#
# 왜 직접 굽는가
#   - 이 프로젝트는 Java 26 을 요구한다(pom.xml 의 <java.version>26</java.version>).
#     공개 배포되는 JDK 26 컨테이너 이미지는 태그가 바뀌거나 사라질 수 있어,
#     Ubuntu LTS 위에 배포판 tarball 을 직접 올려 재현 가능하게 만든다.
#   - Maven 버전을 .mvn/wrapper/maven-wrapper.properties 의 3.9.16 과
#     의도적으로 일치시킨다. 그래야 호스트(mvnw)와 이미지(mvn)의 빌드 결과가 같다.
#   - SHA256/SHA512 로 무결성을 검증한다. 다운로드가 조용히 손상되면
#     빌드 실패보다 더 나쁜 "런타임에만 터지는" 문제가 된다.
#
# 빌드
#   docker build -t sonar-validator/jdk26-base:26.0.2.1 docker/base
#
# 아키텍처
#   기본값은 x86_64(amd64) 이다. ARM(aarch64) 에서 빌드하려면 JDK_URL 과
#   JDK_SHA256 을 arm64 용으로 바꿔 넘기면 된다.
#   예) docker build --build-arg JDK_URL=<arm64 url> --build-arg JDK_SHA256=<hash> ...
# =============================================================================

FROM ubuntu:24.04

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# tzdata 가 대화형 프롬프트를 띄우지 않게 한다. (없으면 빌드가 멈춘다)
ARG DEBIAN_FRONTEND=noninteractive

# -----------------------------------------------------------------------------
# 버전 고정
# -----------------------------------------------------------------------------
# GitHub 릴리스 경로에 '+' 가 들어가므로 URL 인코딩된 %2B 를 그대로 쓴다.
ARG JDK_URL="https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.2.1%2B1/OpenJDK26U-jdk_x64_linux_hotspot_26.0.2.1_1.tar.gz"
ARG JDK_SHA256="451c12e68747bcfa2fb5a2c16b00483fedb9fa6d77bc962d30957f76ac17044d"

ARG MAVEN_VERSION="3.9.16"
ARG MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.tar.gz"
ARG MAVEN_SHA512="831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6"

# -----------------------------------------------------------------------------
# 로케일 / 시간대
# -----------------------------------------------------------------------------
# 컨테이너 기본 로케일을 UTF-8 로 고정한다. C 로케일이면 한글 로그가
# 깨져서 디버깅이 불가능해진다.
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
# 이 프로젝트는 한국에서 쓰이므로 JVM/DB 로그 시각을 KST 로 맞춘다.
ENV TZ=Asia/Seoul

# -----------------------------------------------------------------------------
# 공통 도구
# -----------------------------------------------------------------------------
# curl      : JDK/Maven 다운로드 + 컨테이너 healthcheck(Actuator 호출)
# tini      : PID 1 로 두어 SIGTERM 을 java 프로세스에 정확히 전달한다.
#             이게 없으면 `docker compose stop` 이 SIGKILL 로 끝나고,
#             진행 중인 텔레메트리 트랜잭션이 끊긴다.
# tzdata    : 위 TZ 를 실제로 적용하려면 필요하다.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      tar \
      gzip \
      tini \
      tzdata \
 && rm -rf /var/lib/apt/lists/*

# -----------------------------------------------------------------------------
# JDK 26 설치
# -----------------------------------------------------------------------------
# /opt/java 에 바로 풀어 두면 이후 JDK 버전을 올릴 때 경로 변경이 없다.
RUN set -eux; \
    mkdir -p /opt/java; \
    curl -fSL --retry 3 --retry-delay 2 -o /tmp/jdk.tar.gz "${JDK_URL}"; \
    echo "${JDK_SHA256}  /tmp/jdk.tar.gz" | sha256sum -c -; \
    tar -xzf /tmp/jdk.tar.gz -C /opt/java --strip-components=1; \
    rm -f /tmp/jdk.tar.gz; \
    /opt/java/bin/java -version

ENV JAVA_HOME=/opt/java
ENV PATH="/opt/java/bin:${PATH}"

# -----------------------------------------------------------------------------
# Maven 3.9.16 설치
# -----------------------------------------------------------------------------
RUN set -eux; \
    curl -fSL --retry 3 --retry-delay 2 -o /tmp/maven.tar.gz "${MAVEN_URL}"; \
    echo "${MAVEN_SHA512}  /tmp/maven.tar.gz" | sha512sum -c -; \
    mkdir -p /opt/maven; \
    tar -xzf /tmp/maven.tar.gz -C /opt/maven --strip-components=1; \
    rm -f /tmp/maven.tar.gz; \
    /opt/maven/bin/mvn -version

ENV MAVEN_HOME=/opt/maven
ENV PATH="/opt/maven/bin:${PATH}"

# -----------------------------------------------------------------------------
# 비root 실행 사용자
# -----------------------------------------------------------------------------
# UID 10001 을 쓴다. Ubuntu 24.04 이미지에는 UID 1000(ubuntu)이 이미 있어
# 1000 을 쓰면 홈 디렉터리 권한이 꼬인다.
RUN set -eux; \
    groupadd --gid 10001 appgroup; \
    useradd --uid 10001 --gid 10001 --create-home --shell /bin/bash appuser

# 베이스 이미지 자체는 아무 것도 실행하지 않는다. (파생 이미지가 진입점을 정한다)
CMD ["/bin/bash"]
