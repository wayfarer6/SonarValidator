#!/bin/bash
set -eu
export HOME=/home/guestshell
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
base=${SONAR_PROBER_HOME:-$HOME/sonar}
source_dir=${SONAR_IOS_SOURCE_DIR:-/bootflash/guest-share}
export SONAR_CONFIG_PATH="$base/Installer/default.conf"
export SONAR_TEMPLATE_PATH="$base/Installer/default_template.sqlite"
export SONAR_DATA_DIR="$base/data"
case ${1:-} in ""|--sync-only) ;; *) echo "Usage: $0 [--sync-only]" >&2; exit 2 ;; esac
mkdir -p "$SONAR_DATA_DIR/ios_cli"
temporary_file=
trap 'if [ -n "$temporary_file" ]; then rm -f -- "$temporary_file"; fi' EXIT

sync_snapshot() {
    local name=$1 pattern=$2 source_file before after size
    source_file="$source_dir/sonar_${name}.out"
    [ -f "$source_file" ] && [ -s "$source_file" ] || return 1
    size=$(stat -c %s "$source_file") || return 1
    [ "$size" -le 4194304 ] || return 1
    before=$(stat -c '%d:%i:%s:%Y:%Z' "$source_file") || return 1
    temporary_file=$(mktemp "$SONAR_DATA_DIR/ios_cli/.snapshot.XXXXXX") || return 1
    cp -p -- "$source_file" "$temporary_file" || return 1
    after=$(stat -c '%d:%i:%s:%Y:%Z' "$source_file") || return 1
    [ "$before" = "$after" ] && cmp -s -- "$source_file" "$temporary_file" || return 1
    LC_ALL=C grep -Eq "$pattern" "$temporary_file" || return 1
    if LC_ALL=C grep -Eiq '^[[:space:]]*%.*(invalid|incomplete|ambiguous|error|authorization|permission)|unexpected error|iosp_session' "$temporary_file"; then return 1; fi
    [ "$(tail -c 1 -- "$temporary_file" | od -An -tu1 | tr -d '[:space:]')" = 10 ] || return 1
    mv -f -- "$temporary_file" "$SONAR_DATA_DIR/ios_cli/$name.out" || return 1
    temporary_file=
}

exec 8>"$base/snapshot.lock"
flock -w 2 8 || { echo "[IOS] snapshot publisher is busy" >&2; exit 75; }
status=0
for name in show_ip_interface_brief show_ip_route show_ip_arp; do
    case $name in
        show_ip_interface_brief) pattern='^[[:space:]]*Interface[[:space:]]+IP-Address' ;;
        show_ip_route) pattern='^[[:space:]]*Codes:' ;;
        show_ip_arp) pattern='^[[:space:]]*Protocol[[:space:]]+Address' ;;
    esac
    if ! sync_snapshot "$name" "$pattern"; then
        echo "[IOS] snapshot rejected or unavailable: $name" >&2
        if [ -n "$temporary_file" ]; then rm -f -- "$temporary_file"; temporary_file=; fi
        status=1
    fi
done
flock -u 8
exec 8>&-
if [ "${1:-}" = --sync-only ]; then exit "$status"; fi

exec 9>"$base/prober.lock"
flock -n 9 || exit 0
if [ -f "$base/prober.log" ] && [ "$(stat -c %s "$base/prober.log")" -gt 10485760 ]; then
    mv "$base/prober.log" "$base/prober.log.1"
fi
nohup setsid -f "$base/Installer/sonar_validator_prober" >>"$base/prober.log" 2>&1 </dev/null &
sleep 2
