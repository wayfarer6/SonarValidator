# JDK 26 베이스 이미지

Ubuntu 24.04 + Eclipse Temurin JDK 26 + Apache Maven 3.9.16.

## 빌드

```bash
docker build -t sonar-validator/jdk26-base:26.0.2.1 docker/base
```

또는 저장소 루트에서:

```bash
docker compose --profile bootstrap build base-image
```

## 포함된 것

| 항목 | 값 |
| --- | --- |
| 베이스 | `ubuntu:24.04` (noble) |
| JDK | Eclipse Temurin 26.0.2.1+1 (`/opt/java`) |
| Maven | 3.9.16 (`/opt/maven`) |
| 실행 사용자 | `appuser` (UID/GID 10001) |
| 시간대 | `Asia/Seoul` |
| 로케일 | `C.UTF-8` |
| PID 1 | `tini` (파생 이미지에서 사용) |

## 무결성

JDK 는 SHA256, Maven 은 SHA512 로 빌드 시점에 검증한다.
체크섬이 다르면 빌드가 실패한다.

## ARM(aarch64) 에서 빌드

기본값은 x86_64 용 URL/해시다. ARM 에서는 다음처럼 덮어쓴다.

```bash
docker build -t sonar-validator/jdk26-base:26.0.2.1 docker/base \
  --build-arg JDK_URL="<aarch64 tarball URL>" \
  --build-arg JDK_SHA256="<aarch64 sha256>"
```