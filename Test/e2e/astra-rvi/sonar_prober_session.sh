#!/bin/sh
# IOS 가 띄운 guestshell 세션에서 프로버를 실행하는 래퍼.
#
# 왜 이 방식인가
#   이 장비의 guestshell 에서 `dohost`(IOS CLI 실행)는 IOSP 세션이 있어야
#   동작합니다. 그 세션은 **IOS 가 띄운 프로세스에만** 주어집니다
#   (`guestshell run ...`). systemd 로 띄운 프로세스는 그 세션을 받지 못하고,
#   IOS 가 `/cisco/cisco_cli/app-session-info` 파일도 만들지 않습니다.
#   그래서 프로버를 IOS 쪽에서 직접 띄워야 IOS CLI 수집이 됩니다.
#
# ⚠️ 이 스크립트는 systemd 유닛과 **함께 쓰면 안 됩니다.** 두 개가 동시에
#    돌면 같은 SQLite/WebSocket 을 두 프로세스가 잡습니다. 반드시 하나만
#    실행하세요. (systemd 는 disable)
export SONAR_CONFIG_PATH=/etc/sonar_validator_prober/default.conf
export SONAR_TEMPLATE_PATH=/etc/sonar_validator_prober/sqlite_template.sqlite
export SONAR_DATA_DIR=/var/lib/sonar_validator_prober

# 이미 돌고 있으면 종료합니다.
#   IOS 의 watchdog(EEM)이 주기적으로 이 스크립트를 다시 띄우는데, 그대로
#   실행하면 같은 SQLite/WebSocket 을 두 프로세스가 잡아 데이터가 섞입니다.
#   ⚠️ 세션(IOSP)은 이 프로세스가 살아 있는 동안 유지됩니다. 그래서 아래
#      exec 로 **같은 프로세스**가 계속 돌아야 IOS CLI 접근이 유지됩니다.
if pgrep -f '/usr/local/bin/sonar_validator_prober' >/dev/null 2>&1; then
    echo "already running — nothing to do"
    exit 0
fi

exec /usr/local/bin/sonar_validator_prober
