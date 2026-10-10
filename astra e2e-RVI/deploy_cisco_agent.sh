#!/usr/bin/env bash
# Cisco guestshell Agent 재배포 (RVI).
#
# - 백엔드 터미널 키를 SonarValidator_Backend/config/application-local.properties 에서
#   읽어 default.conf 를 생성합니다. 키 값은 화면에 출력하지 않습니다.
# - 정적 Release 바이너리(build-rvi)를 guestshell 에 올리고 Cisco 프로필로 설치합니다.
#
# 사용:
#   bash 'astra e2e-RVI/deploy_cisco_agent.sh' [--host 10.20.0.1] [--server 10.20.0.3]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="10.20.0.1"
# ⚠️ SERVER_IP 는 guestshell 이 실제로 닿을 수 있는 주소여야 합니다.
#
# guestshell 은 VirtualPortGroup0(192.168.35.0/24)에 격리되어 있고 NAT 로만
# 밖으로 나갑니다. 그런데 NAT 출구가 Gi1(192.168.122.0/24) 로 고정되어 있어
# 관리망(10.20.0.0/24)으로 가는 반환 경로가 없습니다. 그래서 10.20.0.3 을
# 넣으면 Agent 가 **조용히 무응답**이 됩니다 — 로그에 `Connection timed out`
# 만 반복되고, 화면에는 "무응답" 으로만 보입니다. (SONAR-30)
#
# 같은 서버의 NAT 쪽 주소(ens4)를 씁니다. 백엔드는 0.0.0.0:3000 을 듣고
# Agent 식별은 AGENT_NAME 으로 하므로 관리망 주소로 받는 것과 동일하게 동작합니다.
#
# ⚠️ 다만 ens4 는 DHCP 입니다. 주소가 바뀌면 이 값을 다시 넣어야 합니다.
#    (고정하려면 192.168.122.0/24 에서 정적 할당)
SERVER_IP="192.168.122.32"
PORT="2222"
BIN="$ROOT/SonarValidator_Prober/build-rvi/sonar_validator_prober"
PROPS="$ROOT/SonarValidator_Backend/config/application-local.properties"
REMOTE_USER="guestshell"
REMOTE_DIR="/home/guestshell/Installer"

while [ $# -gt 0 ]; do
    case "$1" in
        --host) HOST="$2"; shift 2 ;;
        --server) SERVER_IP="$2"; shift 2 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

[ -x "$BIN" ] || { echo "정적 바이너리가 없습니다: $BIN" >&2; exit 1; }
[ -f "$PROPS" ] || { echo "백엔드 설정이 없습니다: $PROPS" >&2; exit 1; }

# 비밀 키는 파일에서만 읽고, stdout/stderr 로 내보내지 않습니다.
SECRET="$(awk -F= '/^[[:space:]]*sonar\.terminal\.shared-secret[[:space:]]*=/{sub(/^[^=]*=/,""); gsub(/[[:space:]]/,""); print; exit}' "$PROPS")"
# Spring 플레이스홀더 ${ENV:default} 이면 default 값을 씁니다.
case "$SECRET" in
    '${'*'}')
        SECRET="${SECRET##*:}"   # 마지막 ':' 뒤(기본값)
        SECRET="${SECRET%\}}"    # 닫는 '}' 제거
        ;;
esac
[ "${#SECRET}" -ge 32 ] || { echo "터미널 키가 32자 미만입니다(길이 ${#SECRET})" >&2; exit 1; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

cp "$BIN" "$STAGE/sonar_validator_prober"
cp "$ROOT/SonarValidator_Prober/Installer/Installer.sh" "$STAGE/Installer.sh"
cp "$ROOT/SonarValidator_Prober/Installer/default_template.sqlite" "$STAGE/default_template.sqlite"
mkdir -p "$STAGE/systemd" "$STAGE/rc-service"
cp "$ROOT/SonarValidator_Prober/Installer/systemd/sonar_validator_prober-cisco.service" "$STAGE/systemd/"
cp "$ROOT/SonarValidator_Prober/Installer/systemd/sonar_validator_prober.service" "$STAGE/systemd/"
cp "$ROOT/SonarValidator_Prober/Installer/rc-service/sonar_validator_prober" "$STAGE/rc-service/" 2>/dev/null || true

cat > "$STAGE/default.conf" <<EOF
# agent 생성시 서버측에서 ip, port 인증서 등을 지정함
SERVER_IP=${SERVER_IP};
SERVER_PORT=3000;
NODE_TYPE=Router;
AGENT_NAME=Cisco-Router;
TERMINAL_SHARED_SECRET=${SECRET};
MANAGEMENT_PREFIX=10.20.0.0/24,192.168.35.0/24,192.168.122.0/24;
EOF
chmod 600 "$STAGE/default.conf"

echo "[deploy] host=${HOST}:${PORT} server=${SERVER_IP}:3000 binary=$(stat -c%s "$BIN") bytes"
scp -q -o BatchMode=yes -o StrictHostKeyChecking=no -P "$PORT" -r \
    "$STAGE/." "${REMOTE_USER}@${HOST}:${REMOTE_DIR}/"

ssh -o BatchMode=yes -o StrictHostKeyChecking=no -p "$PORT" "${REMOTE_USER}@${HOST}" \
    "cd ${REMOTE_DIR} && sudo env SONAR_INSTALL_PROFILE=cisco sh Installer.sh"

echo "[deploy] 완료. 로그:"
ssh -o BatchMode=yes -o StrictHostKeyChecking=no -p "$PORT" "${REMOTE_USER}@${HOST}" \
    'sudo journalctl -u sonar_validator_prober -n 20 --no-pager'
