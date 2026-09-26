#!/bin/sh
# =============================================================================
#  Arista vEOS 프로버 배포 — sftp 사용 (파이썬 불필요)
#
#  검증된 사실
#    - Arista vEOS 의 SSH 는 sftp 서브시스템을 지원한다.
#      (초기 시도에서 실패했으나 이후 정상 동작 확인 — 서버 측 sftp 활성화됨)
#    - `scp -O` 도 동작한다(레거시 SCP 프로토콜).
#      → SFTP 가 막힌 환경이면 SCP_MODE=1 로 전환하면 된다.
#    - 업로드 대상은 /mnt/flash (admin 사용자 쓰기 가능).
#
#  사용법
#    ./deploy_arista.sh [바이너리] [설정파일] [sqlite템플릿]
#
#  환경변수
#    ARISTA_HOST (기본 10.20.0.4)
#    ARISTA_USER (기본 admin)
#    REMOTE_DIR  (기본 /mnt/flash/sonar_validator)
#    SCP_MODE    (1 이면 sftp 대신 `scp -O` 사용)
# =============================================================================
set -eu

HOST="${ARISTA_HOST:-10.20.0.4}"
USER_NAME="${ARISTA_USER:-admin}"
REMOTE_DIR="${REMOTE_DIR:-/mnt/flash/sonar_validator}"
SCP_MODE="${SCP_MODE:-0}"

# 이 스크립트는 deployment/real-to-virtual/ 에 있습니다
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROBER_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../../SonarValidator_Prober" && pwd)

BIN="${1:-$PROBER_DIR/build/sonar_validator_prober}"
CONF="${2:-$PROBER_DIR/Installer/default.conf}"
TEMPLATE="${3:-$PROBER_DIR/Installer/default_template.sqlite}"

SSH_OPTS="-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o BatchMode=yes"

# ---------------------------------------------------------------------------
#  원격 명령 실행 헬퍼 (enable → bash → 명령 → exit)
#  주의: `ssh -tt ... < file` 는 멈추므로 파이프(|)로 보낸다.
# ---------------------------------------------------------------------------
remote_bash() {
    limit="${2:-120}"
    {
        sleep 1.5; printf 'enable\n'
        sleep 1.5; printf 'bash\n'
        sleep 1.5
        printf '%s\n' "$1"
        sleep 3
        printf 'exit\n'; sleep 1
        printf 'exit\n'; sleep 1
    } | timeout "$limit" ssh -tt $SSH_OPTS "${USER_NAME}@${HOST}" 2>&1 | tr -d '\r'
}

echo "==============================================================="
echo " Arista vEOS 프로버 배포"
echo "  대상 : ${USER_NAME}@${HOST}"
echo "  배치 : ${REMOTE_DIR}"
echo "  방식 : $([ "$SCP_MODE" = "1" ] && echo 'scp -O' || echo 'sftp')"
echo "==============================================================="

# ---------------------------------------------------------------------------
#  입력 확인
# ---------------------------------------------------------------------------
for f in "$BIN" "$CONF" "$TEMPLATE"; do
    if [ ! -f "$f" ]; then
        echo "파일 없음: $f" >&2
        exit 1
    fi
done
echo
echo "[1/3] 로컬 파일"
echo "      바이너리 : $BIN ($(wc -c < "$BIN" | tr -d ' ') bytes)"
echo "      설정     : $CONF"
echo "      템플릿   : $TEMPLATE"

# ---------------------------------------------------------------------------
#  전송
# ---------------------------------------------------------------------------
echo
echo "[2/3] 전송"
if [ "$SCP_MODE" = "1" ]; then
    # 레거시 SCP 프로토콜(-O)을 써서 sftp 서브시스템 없이 전송
    scp -O $SSH_OPTS "$BIN"      "${USER_NAME}@${HOST}:${REMOTE_DIR}/sonar_validator_prober"
    scp -O $SSH_OPTS "$CONF"     "${USER_NAME}@${HOST}:${REMOTE_DIR}/default.conf"
    scp -O $SSH_OPTS "$TEMPLATE" "${USER_NAME}@${HOST}:${REMOTE_DIR}/sqlite_template.sqlite"
else
    # 배치 모드 sftp: 디렉터리 생성 후 업로드
    sftp $SSH_OPTS "${USER_NAME}@${HOST}" <<SFTP_EOF > /dev/null
-mkdir ${REMOTE_DIR}
cd ${REMOTE_DIR}
put "$BIN" sonar_validator_prober
put "$CONF" default.conf
put "$TEMPLATE" sqlite_template.sqlite
chmod 755 sonar_validator_prober
SFTP_EOF
fi
echo "      전송 완료"

# ---------------------------------------------------------------------------
#  검증: 실행 권한 + sha256 일치
# ---------------------------------------------------------------------------
echo
echo "[3/3] 검증"
LOCAL_HASH=$(sha256sum "$BIN" | cut -d' ' -f1)

remote_bash "echo MARK_BEGIN; ls -la ${REMOTE_DIR}; sha256sum ${REMOTE_DIR}/sonar_validator_prober; echo MARK_END" 120 \
    | sed -n '/MARK_BEGIN/,/MARK_END/p' | sed '1d;$d'

REMOTE_HASH=$(remote_bash "sha256sum ${REMOTE_DIR}/sonar_validator_prober" 120 \
    | grep -oE '[0-9a-f]{64}' | tail -1 || true)

echo
echo "      로컬 sha256 : ${LOCAL_HASH}"
echo "      원격 sha256 : ${REMOTE_HASH:-?}"
if [ "${LOCAL_HASH}" = "${REMOTE_HASH}" ]; then
    echo "      결과        : 일치 (전송 성공)"
else
    echo "      결과        : 불일치 — 재시도 필요"
    exit 1
fi

echo
echo "배포 완료 → ${HOST}:${REMOTE_DIR}/sonar_validator_prober"
