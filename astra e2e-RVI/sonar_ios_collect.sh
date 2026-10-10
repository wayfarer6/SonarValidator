#!/bin/sh
# EEM applet 이 `guestshell run` 으로 호출하는 IOS CLI 수집기.
#
# 왜 이렇게 하는가
#   guestshell 의 IOSP 세션은 `guestshell run` 이 시작된 뒤 **단 몇 초만** 유효합니다.
#   실장비 실측(2026-10-10):
#     === start 04:54:46 IOSP_SESSION=6AC9C5102350E2F205000023 ===
#     --- immediate: dohost show clock ---   *04:54:47.686 UTC Sat Oct 10 2026   (성공)
#     --- after 30s: dohost show clock ---   Unexpected Error                     (실패)
#
#   그래서 프로버(수십 초 뒤 dohost)는 항상 실패합니다. IOS 가 세션이 살아 있는
#   동안(= 이 스크립트 실행 즉시) 조회 결과를 파일로 남기고, 프로버는 그 스냅샷을
#   읽습니다. (프로버: ReadIosCliSnapshot 참고)
#
# 파일명 규칙은 프로버와 동일합니다: 영숫자 외 문자는 '_' 로 치환.
# 예) "show ip route" -> show_ip_route.out
DATA_DIR="${SONAR_DATA_DIR:-/var/lib/sonar_validator_prober}"
OUT_DIR="$DATA_DIR/ios_cli"
LOG=/tmp/sonar_ios_collect.log

mkdir -p "$OUT_DIR" 2>/dev/null

STAMP=$(date -Is)
echo "=== collect $STAMP IOSP_SESSION=${IOSP_SESSION:-<empty>} ===" >> "$LOG" 2>&1

collect() {
    cmd="$1"
    name=$(printf '%s' "$cmd" | tr -c 'A-Za-z0-9' '_')
    target="$OUT_DIR/$name.out"
    tmp="$target.tmp"
    out=$(/usr/bin/dohost "$cmd" 2>&1)
    printf '# %s %s\n%s\n' "$STAMP" "$cmd" "$out" > "$tmp" 2>/dev/null \
        && mv "$tmp" "$target" 2>/dev/null

    if printf '%s' "$out" | grep -qiE 'unexpected error|iosp_session|app-session-info|system error'; then
        echo "  FAIL $cmd :: $(printf '%s' "$out" | head -1)" >> "$LOG" 2>&1
    else
        echo "  OK   $cmd ($(printf '%s\n' "$out" | wc -l) lines)" >> "$LOG" 2>&1
    fi
}

collect "show ip interface brief"
collect "show ip route"
collect "show ip arp"
collect "show running-config | section event manager"
collect "show ip access-lists"

# ACL 이 실제로 인터페이스에 걸렸는지(=차단이 동작하는지) 확인용입니다.
#
# ⚠️ `show ip access-lists` 는 ACL 의 내용만 보여주고 <b>어디에 걸렸는지는
#    보여주지 않습니다.</b> ACL 을 만들어 두고 인터페이스에 걸지 않으면
#    화면에는 차단이 있는데 장치에서는 아무것도 막히지 않습니다 —
#    가장 찾기 어려운 실패입니다.
collect "show running-config interface GigabitEthernet1"

# 수집이 끝나면 프로버가 남긴 설정(차단 ACL)을 반영합니다.
#
# 왜 여기서 호출하는가
#   EEM applet 을 새로 만들지 않기 위해서입니다. 이미 주기적으로 도는 이 수집
#   주기에 얹으면 <b>장치 설정(event manager)을 건드리지 않고</b> 배포할 수
#   있습니다.
#
# ⚠️ 이 호출이 가능한 것은 세션이 살아 있는 "지금" 뿐입니다. 프로버가 직접
#    dohost 하는 경로는 서비스로 돌기 때문에 항상 실패합니다.
#    (sonar_ios_apply.sh 참고)
if [ -x /usr/local/bin/sonar_ios_apply.sh ]; then
    /usr/local/bin/sonar_ios_apply.sh >> "$LOG" 2>&1
fi
