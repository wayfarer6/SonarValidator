#!/bin/sh
# =============================================================================
#  GNS3 QEMU Ubuntu VM 에 프로버를 설치합니다.
#
#  왜 이 방식인가
#    이 VM 들에는 sshd 가 없어서 SSH/scp 를 쓸 수 없습니다.
#    대신 콘솔로 로그인한 뒤, VM 안에서 curl 로 이 스크립트를 받아 실행합니다.
#    VM 에는 curl 과 sudo 가 있으므로 이 경로가 가장 단순합니다.
#
#  주의: 서버 주소
#    VM 에서는 관리망(172.16.255.x)이 방화벽으로 막혀 있습니다.
#    반드시 NAT 망 주소(기본 192.168.122.58)를 쓰세요.
#
#  사용법 (VM 콘솔에서)
#    curl -s -o /tmp/d.sh http://192.168.122.58:8099/deploy_vm.sh
#    echo ubuntu | sudo -S sh /tmp/d.sh http://192.168.122.58:8099 VM [AGENT_NAME]
# =============================================================================
set -eu

HTTP_BASE="${1:?usage: deploy_vm.sh <HTTP_BASE> [NODE_TYPE] [AGENT_NAME]}"
NODE_TYPE="${2:-VM}"
# 관리 콘솔에서 등록한 이름과 같아야 목록에서 한 장비로 합쳐집니다.
AGENT_NAME="${3:-$(hostname)-agent}"
ROOT=/opt/sonar_validator

SERVER_IP="${SONAR_SERVER_IP:-192.168.122.58}"
SERVER_PORT="${SONAR_SERVER_PORT:-3000}"

mkdir -p "$ROOT/data"

# 바이너리와 DB 템플릿을 HTTP 로 받습니다. (VM 에 컴파일러가 없음)
curl -s -m 180 -o "$ROOT/sonar_validator_prober"  "$HTTP_BASE/sonar_validator_prober"
curl -s -m 180 -o "$ROOT/default_template.sqlite" "$HTTP_BASE/default_template.sqlite"

printf 'SERVER_IP=%s;\nSERVER_PORT=%s;\nNODE_TYPE=%s;\nAGENT_NAME=%s;\n' \
    "$SERVER_IP" "$SERVER_PORT" "$NODE_TYPE" "$AGENT_NAME" > "$ROOT/default.conf"

chmod +x "$ROOT/sonar_validator_prober"
rm -f "$ROOT/data/settings.conf"

# 재시작 스크립트를 만듭니다. SIGTERM 우선 + 손상 DB 복구.
cat > "$ROOT/run.sh" <<EOS
#!/bin/sh
set -u
ROOT=$ROOT

for p in /proc/[0-9]*; do
    e=\$(readlink "\$p/exe" 2>/dev/null) || continue
    case "\$e" in */sonar_validator_prober*) kill -TERM "\${p#/proc/}" 2>/dev/null || true ;; esac
done

alive=0
i=0
while [ "\$i" -lt 8 ]; do
    alive=0
    for p in /proc/[0-9]*; do
        e=\$(readlink "\$p/exe" 2>/dev/null) || continue
        case "\$e" in */sonar_validator_prober*) alive=1 ;; esac
    done
    [ "\$alive" -eq 0 ] && break
    sleep 1
    i=\$((i + 1))
done

if [ "\$alive" -ne 0 ]; then
    for p in /proc/[0-9]*; do
        e=\$(readlink "\$p/exe" 2>/dev/null) || continue
        case "\$e" in */sonar_validator_prober*) kill -KILL "\${p#/proc/}" 2>/dev/null || true ;; esac
    done
    sleep 1
    echo "  [warn] SIGKILL 사용 -> DB 복구"
    rm -f "\$ROOT/data/prober_db.sqlite-journal"
    if [ -f "\$ROOT/data/prober_db.sqlite" ]; then
        head -c 16 "\$ROOT/data/prober_db.sqlite" | grep -q "SQLite format 3" \\
            || { rm -f "\$ROOT/data/prober_db.sqlite"; echo "  [warn] DB 헤더 손상 -> 재생성"; }
    fi
fi

cd "\$ROOT" || exit 1
SONAR_DATA_DIR="\$ROOT/data" \\
SONAR_TEMPLATE_PATH="\$ROOT/default_template.sqlite" \\
SONAR_CONFIG_PATH="\$ROOT/default.conf" \\
    nohup "\$ROOT/sonar_validator_prober" > "\$ROOT/run.log" 2>&1 &
sleep 2
echo "  started pid=\$!"
EOS
chmod +x "$ROOT/run.sh"

echo -n "hash: "; sha256sum "$ROOT/sonar_validator_prober" | cut -c1-16
cat "$ROOT/default.conf"
