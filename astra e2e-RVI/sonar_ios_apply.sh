#!/bin/sh
# 프로버가 남긴 IOS 설정 명령을 <b>IOS CLI 로 실제 적용</b>하는 스크립트.
#
# 누가 실행하는가
#   IOS 의 EEM applet(`SONAR-COLLECT`)이 주기적으로
#   `guestshell run /usr/local/bin/sonar_ios_collect.sh` 를 실행하고,
#   그 수집 스크립트가 마지막에 이 스크립트를 호출합니다.
#
#   EEM applet 을 새로 만들지 않은 이유: 장치 설정(event manager)을 건드리면
#   롤백·재현이 어려워집니다. 이미 도는 수집 주기에 얹으면 장치 설정 변경 없이
#   배포할 수 있습니다.
#
# 왜 파일을 경유하는가 (게스트셸의 근본 제약)
#   `dohost` 는 IOS 가 만든 세션(IOSP)이 있을 때만 동작합니다. 그 세션은
#   `guestshell run` 으로 띄운 프로세스에서만 살아 있으므로, 서비스로 도는
#   프로버는 dohost 를 항상 실패합니다(실장비 실측: `Unexpected Error`).
#   그래서 프로버는 "지금 남아 있어야 하는 설정" 을 파일로 남기고,
#   세션이 살아 있는 이 스크립트가 그 순간에 적용합니다.
#
# 왜 선언적인가 (지우고 다시 쓰기)
#   IOS 확장 ACL 은 규칙 하나만 지우는 문법이 없습니다.
#   `no ip access-list extended SONAR-CSO` 는 ACL 을 통째로 지웁니다.
#   그래서 프로버는 남아 있어야 하는 명령 전체를 매번 보내고, 이 스크립트는
#   내용이 바뀌었을 때만 적용합니다. 운영자가 금지 연결을 지운 경우가
#   바로 "명령 목록이 짧아진" 경우이고, 그때 장치에서도 그 규칙이 사라집니다.
#
# ⚠️ dohost 는 명령을 세미콜론으로 이어 붙여 <b>한 번에</b> 보내야 합니다.
#    호출을 나누면 `configure terminal` 컨텍스트가 이어지지 않아 두 번째
#    호출이 exec 모드에서 실행되어 실패합니다.
DATA_DIR="${SONAR_DATA_DIR:-/var/lib/sonar_validator_prober}"
APPLY_DIR="$DATA_DIR/ios_apply"
DESIRED="$APPLY_DIR/acl_desired.cmd"
STATE="$APPLY_DIR/acl_applied.state"
RESULT="$APPLY_DIR/acl_result.txt"
LOG=/tmp/sonar_ios_apply.log

[ -f "$DESIRED" ] || exit 0

# 첫 줄은 `# fingerprint=<값>` 헤더입니다. 본문만 적용합니다.
DESIRED_FP=$(sed -n '1s/^# fingerprint=//p' "$DESIRED" 2>/dev/null)
[ -n "$DESIRED_FP" ] || exit 0

# 이미 반영한 내용이면 아무것도 하지 않습니다.
#
# ⚠️ 이 비교가 없으면 매 주기(초 단위)마다 ACL 을 지웠다 다시 씁니다.
#    그 순간마다 차단이 잠깐 풀리고(보안 구멍), 장치 로그도 의미 없이 넘칩니다.
APPLIED_FP=$(sed -n 's/^applied=//p' "$STATE" 2>/dev/null)
if [ "$APPLIED_FP" = "$DESIRED_FP" ]; then
    exit 0
fi

# 본문(헤더 제외)을 세미콜론으로 이어 붙여 하나의 dohost 명령을 만듭니다.
# 빈 줄과 주석은 제외합니다.
PAYLOAD=$(sed -e '1d' -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "$DESIRED" \
    | awk 'NR>1{printf "; "} {printf "%s", $0} END{print ""}')

if [ -z "$PAYLOAD" ]; then
    # 명령이 하나도 없으면 "차단 없음" 이라는 뜻입니다. 상태만 갱신합니다 —
    # 남은 ACL 이 없다는 사실이 장치에 반영되어야 합니다.
    printf 'when=%s\napplied=%s\ncommands=0\nresult=empty\n' \
        "$(date -Is)" "$DESIRED_FP" > "$STATE" 2>/dev/null
    echo "=== ${DESIRED_FP}: empty desired set (no ACL to apply) ===" >> "$LOG" 2>&1
    exit 0
fi

STAMP=$(date -Is)
OUT=$(/usr/bin/dohost "$PAYLOAD" 2>&1)
STATUS=$?

# 결과를 파일로 남깁니다. 프로버가 이 파일로 "장치에 정말 적용됐는지" 를
# 확인할 수 있습니다 — 큐에 넣은 것과 적용된 것은 다릅니다.
{
    printf '# %s\n' "$STAMP"
    printf '%s\n' "$OUT"
} > "$RESULT.tmp" 2>/dev/null && mv "$RESULT.tmp" "$RESULT" 2>/dev/null

# IOS 는 명령을 거부해도 종료코드 0 을 돌려줍니다. 출력의 오류 표시로 판정합니다.
#
# ⚠️ `cli syntax error or execution failure` 를 반드시 포함해야 합니다.
#    dohost 는 IOS 가 설정 줄을 거부했을 때 이 문구를 돌려줍니다. 빠뜨리면
#    <b>실패를 성공으로 기록</b>하고 다시 시도하지 않아, 차단이 안 걸린 채
#    조용히 끝납니다. (RVI 실장비: 장치에 없는 인터페이스를 넣었을 때 실측)
#    프로버의 ios_cli::CheckOutput 과 같은 목록을 유지합니다.
if printf '%s' "$OUT" | grep -qiE 'unexpected error|iosp_session|app-session-info|system error|% (invalid|incomplete|ambiguous|error)|cli syntax error|execution failure|error:|no setupinprogress'; then
    echo "=== ${STAMP} FAIL fp=${DESIRED_FP} status=${STATUS} ===" >> "$LOG" 2>&1
    printf '%s\n' "$OUT" | head -5 >> "$LOG" 2>&1
    # ⚠️ 실패했는데 applied 로 기록하면 다음 주기에 재시도하지 않습니다.
    #    (차단이 안 걸린 채 영원히 조용해집니다)
    printf 'when=%s\npending=%s\napplied=\nresult=failed\n' "$STAMP" "$DESIRED_FP" > "$STATE" 2>/dev/null
    exit 0
fi

printf 'when=%s\npending=%s\napplied=%s\nresult=ok\n' \
    "$STAMP" "$DESIRED_FP" "$DESIRED_FP" > "$STATE" 2>/dev/null
echo "=== ${STAMP} OK fp=${DESIRED_FP} ===" >> "$LOG" 2>&1

# ⚠️ 스크립트는 항상 종료코드 0 으로 끝냅니다.
#    dohost 실패가 수집 주기 전체(ARP/라우팅 스냅샷)를 중단시키면,
#    차단 하나가 안 걸린 것 때문에 <b>텔레메트리 전체가 멈춥니다.</b>
exit 0
