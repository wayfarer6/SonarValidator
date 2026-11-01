FROM mcr.microsoft.com/devcontainers/cpp:3-debian13
RUN apt-get update \
 && apt-get install -y --no-install-recommends cmake pkg-config \
      libsqlite3-dev libssl-dev libboost-dev nlohmann-json3-dev \
 && rm -rf /var/lib/apt/lists/*
