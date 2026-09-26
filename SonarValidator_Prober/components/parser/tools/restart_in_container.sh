#!/bin/sh
# =============================================================================
#  컨테이너 안에서 프로버를 안전하게 재시작합니다.
#
#  왜 별도 스크립트인가
#    호스트에서 한 줄짜리 docker exec 명령으로 종료/복구/기동을 모두 하려면
#    인용 지옥이 됩니다. 이 파일을 컨테이너로 복사해 실행하는 편이 안전하고,
#    Alpine(musl) 과 Ubuntu(glibc) 컨테이너에 같은 스크립트를 쓸 수 있습니다.
#
#  ⚠️ SIGTERM 을 먼저 보내는 이유
#     kill -9 는 SQLite 에 hot journal 을 남깁니다. 그러면 다음 기동이
#     "Runtime initialization failed" 로 실패하고, 매번 수동으로 DB 를 지워야
#     합니다. SIGTERM 을 주면 프로버가 DB 를 닫고 정상 종료합니다.
#     그래도 응답이 없을 때만 SIGKILL 로 올리고, 그 경우 손상 DB 를 제거합니다.
#
#  사용법 (컨테이너 안에서)
#    sh /opt/sonar_validator/restart.sh <SERVER_IP> [NODE_TYPE] [AGENT_NAME]
# =============================================================================
set -u

SERVER_IP="${1:?usage: restart.sh <SERVER_IP> [NODE_TYPE] [AGENT_NAME]}"
NODE_TYPE="${2:-VM}"
ROOT=/opt/sonar_validator

# AGENT_NAME 은 인자 > 기존 default.conf 순으로 결정합니다.
# 여기서 이름을 잃으면 settings.conf 재생성 때 새 이름이 발급되어,
# 관리 콘솔의 "배포 예정" 등록과 연결이 끊어집니다.
AGENT_NAME="${3:-}"
if [ -z "$AGENT_NAME" ] && [ -f "$ROOT/default.conf" ]; then
    AGENT_NAME=$(sed -n 's/^AGENT_NAME=//p' "$ROOT/default.conf" | tr -d '; \r')
fi

# --- 1) SIGTERM 으로 정상 종료 유도 -----------------------------------------
for p in /proc/[0-9]*; do
    e=$(readlink "$p/exe" 2>/dev/null) || continue
    case "$e" in
        */sonar_validator_prober*) kill -TERM "${p#/proc/}" 2>/dev/null || true ;;
    esac
done

alive=0
i=0
while [ "$i" -lt 8 ]; do
    alive=0
    for p in /proc/[0-9]*; do
        e=$(readlink "$p/exe" 2>/dev/null) || continue
        case "$e" in */sonar_validator_prober*) alive=1 ;; esac
    done
    [ "$alive" -eq 0 ] && break
    sleep 1
    i=$((i + 1))
done

# --- 2) 응답이 없으면 강제 종료 + DB 복구 -----------------------------------
if [ "$alive" -ne 0 ]; then
    for p in /proc/[0-9]*; do
        e=$(readlink "$p/exe" 2>/dev/null) || continue
        case "$e" in */sonar_validator_prober*) kill -KILL "${p#/proc/}" 2>/dev/null || true ;; esac
    done
    sleep 1
    echo "  [warn] SIGKILL 사용 -> DB 복구"

    rm -f "$ROOT/data/prober_db.sqlite-journal"
    if [ -f "$ROOT/data/prober_db.sqlite" ]; then
        # SQLite 파일은 항상 "SQLite format 3" 로 시작합니다.
        # 그렇지 않으면 열 수 없는 파일이므로 새로 시작합니다.
        head -c 16 "$ROOT/data/prober_db.sqlite" | grep -q "SQLite format 3" \
            || { rm -f "$ROOT/data/prober_db.sqlite"; echo "  [warn] DB 헤더 손상 -> 재생성"; }
    fi
fi

# --- 3) 설정을 쓰고 기동 ----------------------------------------------------
mkdir -p "$ROOT/data"
printf 'SERVER_IP=%s;\nSERVER_PORT=3000;\nNODE_TYPE=%s;\n' "$SERVER_IP" "$NODE_TYPE" \
    > "$ROOT/default.conf"
if [ -n "$AGENT_NAME" ]; then
    printf 'AGENT_NAME=%s;\n' "$AGENT_NAME" >> "$ROOT/default.conf"
fi
rm -f "$ROOT/data/settings.conf"    # 에이전트 ID 를 새로 만들게 합니다

cd "$ROOT" || exit 1
SONAR_DATA_DIR="$ROOT/data" \
SONAR_TEMPLATE_PATH="$ROOT/default_template.sqlite" \
SONAR_CONFIG_PATH="$ROOT/default.conf" \
    nohup "$ROOT/sonar_validator_prober" > "$ROOT/run.log" 2>&1 &

sleep 2
echo "  started pid=$!"
