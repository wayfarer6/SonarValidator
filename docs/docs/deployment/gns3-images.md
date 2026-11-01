# GNS3 Docker 이미지 빌드와 이동

저장소 루트의 `dev.sh`에서 현재 GNS3 이미지 태그로 빌드합니다. 이 모드는 이미지 빌드를 완료하면 종료합니다. Java·Node·로컬 Prober 빌드 없이 Docker 데몬만 사용합니다. GNS3 compute 서버가 사용하는 Docker 엔진에서 실행하세요.

```bash
./dev.sh --build-images
```

필요한 종류만 빌드할 수도 있습니다.

```bash
./dev.sh --build-images=ubuntu
./dev.sh --build-images=openvswitch
./dev.sh --build-images=firewall
```

## 이미지와 태그

| 이미지 | GNS3/기존 태그 | 기반 |
|---|---|---|
| Ubuntu | `sonar-validator/ubuntu-container:latest` | Ubuntu 26.04 |
| Open vSwitch | `openvswitch-modified/openvswitch:latest` | Alpine 3.22 + openvswitch 패키지 |
| Alpine 방화벽 | `firewall-alpine-linux-based/firewall:latest` | Alpine 3.24 |

기존 이름을 사용하는 템플릿을 위해 OVS에는 `openvswitch-container:latest`, 방화벽에는 `alpine-firewall:latest`와 기존 오타 이름 `firewall-alpine-linux-baed/firewall:latest`도 같은 빌드에 태그합니다. 테스트용 날짜 태그나 다른 프로그램의 이미지는 빌드 대상에 포함하지 않습니다.

Ubuntu에는 SSH, `iproute2`, `nftables`, `util-linux`, `procps`, ping/curl/traceroute, nginx가 들어갑니다. 빌드 시 생성되는 SSH 호스트 키를 제거하고 각 컨테이너의 최초 기동에서 키를 생성합니다. OVS 이미지는 Alpine 패키지에서 직접 만들고 기존 `/etc/openvswitch`, `/root` 볼륨을 유지합니다.

## 다른 컴퓨터로 이동

1. 저장소를 복사하거나 clone하고, 새 GNS3 compute 서버의 Docker에서 `./dev.sh --build-images`를 실행합니다.
2. GNS3 프로젝트와 노드별 영속 데이터를 함께 이동합니다. 현재 Ubuntu 노드는 `/etc`, OVS 노드는 `/etc/switch`를 추가 영속 경로로 사용합니다. OVS의 이미지 볼륨 `/root`, `/etc/openvswitch`도 보존합니다. 노드별 IP·부팅 스크립트·SSH 인증·Prober 설정은 이 프로젝트 데이터에 있습니다.
3. Cisco·Arista·OPNsense·FRR의 QEMU 이미지와 저장된 디스크/설정을 새 GNS3 서버에 준비합니다. 이 명령의 대상은 위 표의 Docker 이미지입니다.
4. 새 호스트의 관리망 연결과 TAP/경로를 [네트워크 fixture](../testing/network-fixtures.md)에 맞게 준비합니다. SSH 키와 서버 주소는 새 호스트 환경에 맞게 확인합니다.
5. 프로젝트를 열고 노드를 시작한 뒤 SSH와 관리 서버 수집을 확인합니다. 이미 생성된 컨테이너는 원래 이미지 ID를 계속 사용하므로, 새 이미지를 적용할 노드를 재생성할 때 프로젝트 영속 데이터를 보존합니다. 이 빌드 명령은 기존 노드를 재생성하지 않습니다.

GNS3의 볼륨은 프로젝트 폴더에 저장됩니다. 이미지와 프로젝트 데이터를 함께 준비하는 이유는 [GNS3 공식 Persistence 설명](https://docs.gns3.com/docs/emulators/docker-support-in-gns3#persistence)을 참고하세요.

## Backend/Frontend 이미지

애플리케이션 이미지는 기존 스택 빌드 명령으로 준비합니다.

```bash
./dev.sh --build-images=stack
```

Backend Docker 빌드도 프로젝트에 포함한 ANTLR 런타임을 사용합니다. `pkg-config`와 정적 빌드 의존성을 설치하고 기본 2개 작업으로 Prober를 빌드하며, Installer 라이선스를 함께 스테이징합니다. Compose 실행과 환경 파일은 [Docker Compose 배포](./docker-compose.md)를 참고하세요.

이미지 소스는 `docker/gns3/{ubuntu,openvswitch,firewall}/Dockerfile`에 있고, 영속 경로 심볼릭 링크 처리는 `docker/gns3/common/fix-links.sh`에서 공유합니다. 패키지 설치는 각 Dockerfile에 직접 정의합니다. GNS3와 앱 이미지 빌드의 진입점은 모두 `dev.sh --build-images[=대상]`입니다.
