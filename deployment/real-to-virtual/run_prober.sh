#!/bin/sh
# =============================================================================
#  Arista vEOS 에서 프로버를 실행하는 래퍼 스크립트
#
#  왜 필요한가
#    SSH 대화형 세션에서 긴 한 줄 명령을 보내면 pty 가 줄을 접어
#    공백이 끼어들어 명령이 깨진다. 스크립트로 만들어 실행하면 이 문제가 없다.
#
#  사용법 (Arista bash 에서)
#    sh /mnt/flash/sonar_validator/run_prober.sh [초]
# =============================================================================
set -eu

ROOT=/mnt/flash/sonar_validator
DATA="$ROOT/data"
SECONDS_TO_RUN="${1:-60}"

export SONAR_DATA_DIR="$DATA"
export SONAR_TEMPLATE_PATH="$ROOT/sqlite_template.sqlite"
export SONAR_CONFIG_PATH="$ROOT/default.conf"

mkdir -p "$DATA"
cd "$ROOT"

# 이전 실행 정리
pkill -f sonar_validator_prober 2>/dev/null || true
sleep 1

# 백그라운드 실행 후 지정 시간만큼 관찰
nohup ./sonar_validator_prober > "$ROOT/run.log" 2>&1 &
PID=$!
echo "STARTED pid=$PID"
sleep "$SECONDS_TO_RUN"

echo "--- run.log (앞부분) ---"
head -30 "$ROOT/run.log"

echo "--- 프로세스 상태 ---"
if kill -0 "$PID" 2>/dev/null; then
    echo "RUNNING"
else
    echo "EXITED"
fi

echo "--- SQLite 수집 결과 ---"
python3 - <<'PY'
import sqlite3, os
db = "/mnt/flash/sonar_validator/data/prober_db.sqlite"
if not os.path.exists(db):
    print("DB 없음")
    raise SystemExit(0)
con = sqlite3.connect(db)
tables = ["nic_info", "nic_address", "route_table", "vlan_status",
          "trunk_status", "arp_table", "nic_status"]
for name in tables:
    try:
        n = con.execute("SELECT COUNT(*) FROM " + name).fetchone()[0]
        print(f"{name:14s} {n}")
    except Exception as exc:
        print(f"{name:14s} 오류: {exc}")

# 샘플 데이터
print("--- 샘플 ---")
for name, sql in [
    ("vlan_status", "SELECT vlan_id, name, status FROM vlan_status LIMIT 5"),
    ("trunk_status", "SELECT port_name, mode, access_vlan FROM trunk_status LIMIT 5"),
    ("arp_table", "SELECT address, mac, interface_name FROM arp_table LIMIT 5"),
    ("nic_info", "SELECT name, state, mac FROM nic_info LIMIT 5"),
]:
    try:
        print(f"[{name}]")
        for row in con.execute(sql):
            print("  ", row)
    except Exception as exc:
        print("  오류:", exc)
PY

# 정리
kill "$PID" 2>/dev/null || true
sleep 1
echo "STOPPED"
