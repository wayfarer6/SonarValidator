# syntax=docker/dockerfile:1.7
# =============================================================================
# SonarValidator Prober compiler
# =============================================================================
# C++23/CMake/ANTLR4 툴체인을 애플리케이션 런타임과 분리한다.
# 이 이미지가 만든 /tmp/sonar_stage 만 Backend 이미지로 전달된다.

FROM ubuntu:24.04 AS toolchain

ARG DEBIAN_FRONTEND=noninteractive
ARG ANTLR4_VERSION=4.13.2

RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates \
      build-essential \
      g++ \
      cmake \
      curl \
      unzip \
      libsqlite3-dev \
      libssl-dev \
      libboost-dev \
      nlohmann-json3-dev \
    && rm -rf /var/lib/apt/lists/*

# 생성된 parser와 같은 버전의 ANTLR4 C++ runtime을 고정해 설치한다.
RUN mkdir -p /opt/antlr4-install && \
    curl -fsSL -o /tmp/antlr4-cxx.zip \
      "https://www.antlr.org/download/antlr4-cpp-runtime-${ANTLR4_VERSION}-source.zip" && \
    unzip -q /tmp/antlr4-cxx.zip -d /tmp/antlr4-src && \
    cd /tmp/antlr4-src && \
    cmake -B build \
      -DCMAKE_BUILD_TYPE=Release \
      -DCMAKE_INSTALL_PREFIX=/opt/antlr4-install && \
    cmake --build build --parallel "$(nproc)" && \
    cmake --install build && \
    rm -rf /tmp/antlr4-cxx.zip /tmp/antlr4-src

WORKDIR /build

FROM toolchain AS compile

COPY Prober/ ./

RUN cmake -S . -B build_static \
      -DCMAKE_BUILD_TYPE=Release \
      -DSONAR_STATIC_LINK=ON \
      -DCMAKE_EXE_LINKER_FLAGS=-static \
      -DANTLR4_RUNTIME_ROOT=/opt/antlr4-install && \
    cmake --build build_static --target installer_bundle --parallel "$(nproc)" && \
    mkdir -p /tmp/sonar_stage && \
    cp -R \
      Installer/Installer.sh \
      Installer/default_template.sqlite \
      Installer/sonar_validator_prober \
      Installer/systemd \
      Installer/rc-service \
      /tmp/sonar_stage/ && \
    test -x /tmp/sonar_stage/sonar_validator_prober && \
    test -s /tmp/sonar_stage/default_template.sqlite

# Compose와 Backend 사이에는 빌드 결과만 전달한다. gcc/cmake/ANTLR 소스는
# 이 최종 산출 이미지에 남지 않는다.
FROM ubuntu:24.04 AS bundle

COPY --from=compile /tmp/sonar_stage/ /tmp/sonar_stage/

CMD ["/bin/true"]
