#!/bin/sh
# =============================================================================
#  Ubuntu VM 용 프로버 실행 래퍼
#
#  사용법 (VM 에서)
#    sh ~/sonar_validator/run_prober.sh [관찰시간(초)]
#
#  비root 로 실행하므로 SONAR_* 환경변수 3종을 반드시 지정한다.
# =============================================================================
set -eu

ROOT="$HOME/sonar_validator"
DATA="$ROOT/data"
SECONDS_TO_RUN="${1:-60}"

export SONAR_DATA_DIR="$DATA"
export SONAR_TEMPLATE_PATH="$ROOT/default_template.sqlite"
export SONAR_CONFIG_PATH="$ROOT/default.conf"

mkdir -p "$DATA"
cd "$ROOT"

echo "=== 바이너리 ==="
ls -la "$ROOT/sonar_validator_prober"
file "$ROOT/sonar_validator_prober" 2>/dev/null | cut -c1-120 || true

echo
echo "=== 설정 ==="
cat "$ROOT/default.conf"

pkill -f sonar_validator_prober 2>/dev/null || true
sleep 1

echo
echo "=== 기동 ==="
nohup ./sonar_validator_prober > "$ROOT/run.log" 2>&1 &
PID=$!
echo "STARTED pid=$PID"
sleep "$SECONDS_TO_RUN"

echo
echo "=== run.log ==="
head -40 "$ROOT/run.log"

echo
echo "=== 프로세스 ==="
if kill -0 "$PID" 2>/dev/null; then
    echo "RUNNING"
else
    echo "EXITED"
fi

echo
echo "=== DB 파일 ==="
ls -la "$DATA" 2>/dev/null || echo "data 디렉터리 없음"

echo
echo "=== SQLite 수집 결과 ==="
python3 - <<'PY'
import os, sqlite3
base = os.path.expanduser("~/sonar_validator/data")
cands = [os.path.join(base, f) for f in os.listdir(base)] if os.path.isdir(base) else []
cands = [c for c in cands if c.endswith(".sqlite") or c.endswith(".db")]
if not cands:
    print("DB 없음")
    raise SystemExit(0)
db = sorted(cands, key=os.path.getmtime)[-1]
print("DB:", db)
con = sqlite3.connect(db)
tables = ["nic_info", "nic_address", "route_table", "vlan_status",
          "trunk_status", "arp_table", "nic_status", "Agent_info"]
for name in tables:
    try:
        n = con.execute("SELECT COUNT(*) FROM " + name).fetchone()[0]
        print(f"  {name:14s} {n}")
    except Exception as exc:
        print(f"  {name:14s} - ({exc})")
PY

echo
echo "=== 샘플 데이터 ==="
python3 - <<'PY'
import os, sqlite3
base = os.path.expanduser("~/sonar_validator/data")
cands = [os.path.join(base, f) for f in os.listdir(base)] if os.path.isdir(base) else []
cands = [c for c in cands if c.endswith(".sqlite") or c.endswith(".db")]
if not cands:
    raise SystemExit(0)
db = sorted(cands, key=os.path.getmtime)[-1]
con = sqlite3.connect(db)
for t in ("nic_info", "route_table", "arp_table", "nic_address"):
    try:
        cols = [r[1] for r in con.execute(f"PRAGMA table_info({t})")]
        if not cols:
            continue
        rows = con.execute(f"SELECT * FROM {t} LIMIT 3").fetchall()
        print(f"--- {t} ({', '.join(cols)}) ---")
        for r in rows:
            print("   ", r)
    except Exception as exc:
        print(f"--- {t} 오류: {exc}")
PY
