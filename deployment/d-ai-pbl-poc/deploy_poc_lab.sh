#!/bin/sh
# =============================================================================
#  PoC 랩(GNS3) 노드에 프로버를 배포하고 실행하는 스크립트
#
#  이 랩의 노드는 접속 방법이 세 가지로 갈리고, 그래서 배포 방법도 나뉩니다.
#
#    A) SSH 가 되는 노드 (라우터 5대 + 방화벽 1대)
#       172.16.255.1  Gateway-Router      Alpine + FRR       NODE_TYPE=Router
#       172.16.255.2  Firewall            Alpine + nftables  NODE_TYPE=Firewall
#       172.16.255.3  DMZ-Router          Alpine + FRR       NODE_TYPE=Router
#       172.16.255.4  C4I-Network-Router  Alpine + FRR       NODE_TYPE=Router
#       172.16.255.5  Survillance-Router  Alpine + FRR       NODE_TYPE=Router
#       172.16.255.6  VDI-Router          Alpine + FRR       NODE_TYPE=Router
#       → 이 스크립트가 처리합니다.
#
#    B) 컨테이너 (docker exec)
#       스위치 5대(OpenVSwitch, L2 전용), VM 4대(TOD-Cam/UAV/VDI-1/2)
#       방화벽 컨테이너는 A 의 172.16.255.2 와 같은 노드입니다.
#       → GNS3 호스트에서 deploy_containers.sh 로 처리합니다.
#
#    C) QEMU VM (콘솔만, sshd 없음)
#       ATICS / KNCCS / AFCCS / Public-Web-Server
#       → GNS3 호스트에서 vmrun.py 로 콘솔 로그인해 처리합니다.
#
#  왜 HTTP 를 쓰는가
#    노드 안에 컴파일러가 없고 컨테이너는 패키지 저장소에 접근할 수 없습니다.
#    그래서 호스트에서 HTTP 로 바이너리를 내려주는 방식이 가장 단순합니다.
#
#  ⚠️ 반드시 SIGTERM 으로 먼저 종료하세요.
#     kill -9 는 SQLite 에 hot journal 을 남겨 다음 기동을
#     "Runtime initialization failed" 로 실패시킵니다.
#     이 스크립트는 SIGTERM 을 먼저 보내고, 그래도 남으면 강제 종료한 뒤
#     손상된 DB 파일을 제거해 복구합니다.
#
#  사용법
#    sh deploy_poc_lab.sh <HTTP_BASE>
#    sh deploy_poc_lab.sh http://172.16.255.245:8099
# =============================================================================
set -eu

HTTP_BASE="${1:?usage: deploy_poc_lab.sh <http-base>   e.g. http://172.16.255.245:8099}"
ROOT=/opt/sonar_validator

# 서버(수집 대상) 주소.
# 라우터/방화벽은 NAT 망에 있어 192.168.122.58 의 mock/백엔드에 접속합니다.
SERVER_IP="${SONAR_SERVER_IP:-192.168.122.58}"
SERVER_PORT="${SONAR_SERVER_PORT:-3000}"

SSH_OPTS="-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10"

# ---------------------------------------------------------------------------
#  노드에서 실행할 재시작 스크립트 본문
#
#  원격 한 줄 명령은 busybox ash 인용 처리가 까다로우므로, 파일로 만들어
#  전송한 뒤 실행하는 편이 훨씬 안전합니다.
# ---------------------------------------------------------------------------
remote_restart_body() {
    cat <<'EOS'
#!/bin/sh
# 프로버를 안전하게 재시작합니다. (SIGTERM 우선, 손상 DB 복구 포함)
set -u
ROOT=/opt/sonar_validator

stop_agent() {
    for p in /proc/[0-9]*; do
        e=$(readlink "$p/exe" 2>/dev/null) || continue
        case "$e" in */sonar_validator_prober*) kill -TERM "${p#/proc/}" 2>/dev/null || true ;; esac
    done
    i=0
    while [ "$i" -lt 8 ]; do
        alive=0
        for p in /proc/[0-9]*; do
            e=$(readlink "$p/exe" 2>/dev/null) || continue
            case "$e" in */sonar_validator_prober*) alive=1 ;; esac
        done
        [ "$alive" -eq 0 ] && return 0
        sleep 1
        i=$((i + 1))
    done
    for p in /proc/[0-9]*; do
        e=$(readlink "$p/exe" 2>/dev/null) || continue
        case "$e" in */sonar_validator_prober*) kill -KILL "${p#/proc/}" 2>/dev/null || true ;; esac
    done
    sleep 1
    echo "  [warn] SIGKILL 사용 -> DB 복구"
    return 1
}

recover_db() {
    rm -f "$ROOT/data/prober_db.sqlite-journal"
    if [ -f "$ROOT/data/prober_db.sqlite" ]; then
        head -c 16 "$ROOT/data/prober_db.sqlite" | grep -q "SQLite format 3" \
            || { rm -f "$ROOT/data/prober_db.sqlite"; echo "  [warn] DB 헤더 손상 -> 재생성"; }
    fi
}

stop_agent >/dev/null 2>&1 || true
recover_db
rm -f "$ROOT/data/settings.conf"

cd "$ROOT" || exit 1
SONAR_DATA_DIR="$ROOT/data" \
SONAR_TEMPLATE_PATH="$ROOT/default_template.sqlite" \
SONAR_CONFIG_PATH="$ROOT/default.conf" \
    nohup "$ROOT/sonar_validator_prober" > "$ROOT/run.log" 2>&1 &
sleep 2
echo "  started pid=$!"
EOS
}

deploy_ssh() {
    host="$1"
    node_type="$2"
    # 관리 콘솔에서 "배포 예정" 으로 등록한 이름과 반드시 같아야 합니다.
    # 다르면 등록한 장치는 영원히 무응답으로 남고, 같은 장비가 두 줄로 보입니다.
    agent_name="${3:-$(basename "$host")-agent}"
    echo "=== $host  (NODE_TYPE=$node_type, AGENT_NAME=$agent_name) ==="

    # 1) 실행 중이면 먼저 정상 종료시킵니다. (실행 중인 파일은 교체할 수 없음)
    ssh $SSH_OPTS "root@$host" "
        mkdir -p $ROOT/data
        for p in /proc/[0-9]*; do
            e=\$(readlink \"\$p/exe\" 2>/dev/null) || continue
            case \"\$e\" in */sonar_validator_prober*) kill -TERM \"\${p#/proc/}\" 2>/dev/null || true ;; esac
        done
        sleep 3
        rm -f $ROOT/sonar_validator_prober $ROOT/data/settings.conf" >/dev/null 2>&1

    # 2) HTTP 로 바이너리/템플릿을 내려받고 설정을 씁니다.
    #
    # ⚠️ curl 이 아니라 wget 을 쓰는 이유:
    #   이 랩의 FRR 라우터(Alpine)에는 curl 이 설치되어 있지 않습니다.
    #   (실측: wget/nc/busybox/base64 는 있음, curl 은 없음)
    #   curl 을 그대로 쓰면 배포가 조용히 0바이트 파일을 남깁니다.
    #
    # ⚠️ -O 대신 -q -O 를 쓰고, 실패 시 파일을 지웁니다.
    #   wget 은 404 에서도 빈 파일을 만들 수 있어, 그대로 두면
    #   다음 기동이 "not executable format" 으로 실패합니다.
    ssh $SSH_OPTS "root@$host" "
        cd $ROOT
        rm -f sonar_validator_prober default_template.sqlite
        wget -q -O sonar_validator_prober  $HTTP_BASE/sonar_validator_prober  || exit 1
        wget -q -O default_template.sqlite $HTTP_BASE/default_template.sqlite || exit 1
        # 크기 검증: 다운로드가 잘렸는지 확인합니다.
        sz=\$(wc -c < sonar_validator_prober)
        [ \"\$sz\" -gt 1000000 ] || { echo '  [FAIL] prober download truncated ('\$sz' bytes)'; rm -f sonar_validator_prober; exit 1; }
        printf 'SERVER_IP=$SERVER_IP;\nSERVER_PORT=$SERVER_PORT;\nNODE_TYPE=$node_type;\nAGENT_NAME=$agent_name;\n' > default.conf
        chmod +x sonar_validator_prober
        echo -n '  hash: '; sha256sum sonar_validator_prober | cut -c1-16"

    # 3) 재시작 스크립트를 만들어 실행합니다.
    remote_restart_body | ssh $SSH_OPTS "root@$host" "cat > $ROOT/restart.sh"
    ssh $SSH_OPTS "root@$host" "sh $ROOT/restart.sh"
}

deploy_all() {
    deploy_ssh 172.16.255.1 Router   Gateway-Router       # Gateway-Router
    deploy_ssh 172.16.255.3 Router   DMZ-Router           # DMZ-Router
    deploy_ssh 172.16.255.4 Router   C4I-Network-Router   # C4I-Network-Router
    deploy_ssh 172.16.255.5 Router   Survillance-Network-Router # Survillance-Network-Router
    deploy_ssh 172.16.255.6 Router   VDI-Router           # VDI-Router
}

deploy_all

echo
echo "완료. 나머지 노드는 GNS3 호스트에서 배포하세요."
echo "  방화벽(172.16.255.2) : SSH 키 미등록 → 호스트에서 docker exec GNS3.Firewall.* (deploy_containers.sh)"
echo "  컨테이너(스위치/VM)  : bash deploy_containers.sh <binary> <template>"
echo "  QEMU VM(서버)        : python3 vmrun.py <console-port> ubuntu ubuntu ..."
echo
echo "⚠️ 방화벽을 이 스크립트에 넣지 않는 이유:"
echo "  172.16.255.2 는 SSH 키가 등록되어 있지 않아 비밀번호를 물어봅니다."
echo "  대화형 프롬프트가 뜨면 자동 배포가 그 자리에서 멈추므로,",
echo "  방화벽은 <b>같은 노드의 컨테이너</b>(GNS3.Firewall.*)에 docker exec 으로 넣습니다."
