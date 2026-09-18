#!/bin/bash
# =============================================================================
#  GNS3 docker 컨테이너에 프로버를 배포합니다. (GNS3 호스트에서 실행)
#
#  이 랩에서 docker 컨테이너로 도는 노드
#    Firewall                 alpine-firewall:latest        NODE_TYPE=Firewall
#    Switch-0 ~ Switch-4      openvswitch-container:latest  NODE_TYPE=Switch
#    TOD-Cam/UAV/VDI-1/VDI-2  gns3/ubuntu:resolute          NODE_TYPE=VM
#
#  왜 docker cp 를 쓰는가
#    컨테이너 안에는 컴파일러가 없고 패키지 저장소에도 접근할 수 없습니다.
#    GNS3 호스트에는 이미 바이너리가 있으므로 docker cp 가 가장 확실합니다.
#
#  왜 서버 주소가 두 가지인가
#    - 스위치는 L2 전용이라 관리망(172.16.255.0/24)에만 붙습니다.
#      → 관리망에 있는 호스트 주소로 접속해야 합니다.
#    - VM 컨테이너는 데이터망(NAT)에 있어 192.168.122.58 로 접속합니다.
#    같은 주소를 쓰면 한쪽은 도달할 수 없습니다.
#
#  주의: 이 스크립트는 배포만 합니다. 재시작은 컨테이너 안에서
#        restart_in_container.sh 로 하세요. (SIGTERM 우선 = DB 보호)
#
#  사용법
#    bash deploy_containers.sh <binary> <template.sqlite> [mgmt_host] [nat_host]
# =============================================================================
set -eu

BIN="${1:?usage: deploy_containers.sh <binary> <template.sqlite> [mgmt_host] [nat_host]}"
TEMPLATE="${2:?usage: deploy_containers.sh <binary> <template.sqlite> [mgmt_host] [nat_host]}"
MGMT_HOST="${3:-172.16.255.245}"
NAT_HOST="${4:-192.168.122.58}"

for full in $(docker ps --format '{{.Names}}' | sort); do
    short=$(echo "$full" | sed 's/GNS3\.//;s/\.5a790fe8.*//')

    case "$short" in
        Firewall)  NODE_TYPE="Firewall"; SERVER_IP="$NAT_HOST"  ;;
        Switch-*)  NODE_TYPE="Switch";   SERVER_IP="$MGMT_HOST" ;;
        *)         NODE_TYPE="VM";       SERVER_IP="$NAT_HOST"  ;;
    esac

    echo "=== $short (NODE_TYPE=$NODE_TYPE, SERVER_IP=$SERVER_IP) ==="

    docker exec "$full" mkdir -p /opt/sonar_validator/data

    # 실행 중인 바이너리는 교체할 수 없으므로 먼저 멈춥니다.
    docker exec "$full" sh -c '
        for p in /proc/[0-9]*; do
            e=$(readlink "$p/exe" 2>/dev/null) || continue
            case "$e" in */sonar_validator_prober*) kill -TERM "${p#/proc/}" 2>/dev/null || true ;; esac
        done
        sleep 3' 2>/dev/null || true

    docker cp "$BIN"      "$full:/opt/sonar_validator/sonar_validator_prober"
    docker cp "$TEMPLATE" "$full:/opt/sonar_validator/default_template.sqlite"

    # 노드별 설정을 만들어 넣습니다. settings.conf 는 지워서 새로 생성되게 합니다.
    printf 'SERVER_IP=%s;\nSERVER_PORT=3000;\nNODE_TYPE=%s;\n' "$SERVER_IP" "$NODE_TYPE" \
        > /tmp/sonar_default.conf
    docker cp /tmp/sonar_default.conf "$full:/opt/sonar_validator/default.conf"
    docker exec "$full" sh -c 'rm -f /opt/sonar_validator/data/settings.conf' 2>/dev/null || true

    docker exec "$full" chmod +x /opt/sonar_validator/sonar_validator_prober
    printf '  hash: '
    docker exec "$full" sh -c 'sha256sum /opt/sonar_validator/sonar_validator_prober | cut -c1-16'
done

echo
echo "배포 완료. 재시작은 각 컨테이너 안에서:"
echo "  docker cp restart_in_container.sh <name>:/opt/sonar_validator/restart.sh"
echo "  docker exec <name> sh /opt/sonar_validator/restart.sh <SERVER_IP>"
