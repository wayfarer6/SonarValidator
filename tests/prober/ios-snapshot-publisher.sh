#!/usr/bin/env bash
set -euo pipefail
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
fixture=$(mktemp -d /tmp/sonar-ios-publisher.XXXXXX)
trap 'rm -rf -- "$fixture"' EXIT
export SONAR_PROBER_HOME="$fixture/prober"
export SONAR_IOS_SOURCE_DIR="$fixture/source"
mkdir -p "$SONAR_IOS_SOURCE_DIR"
publisher="$repo/docs/static/scripts/lab1-prober-start.sh"
printf 'Interface IP-Address OK? Method Status Protocol\nGi1 192.168.148.181 YES manual up up\n' >"$SONAR_IOS_SOURCE_DIR/sonar_show_ip_interface_brief.out"
printf 'Codes: L - local, C - connected\nC 10.10.20.0/24 is directly connected, Gi2\n' >"$SONAR_IOS_SOURCE_DIR/sonar_show_ip_route.out"
printf 'Protocol Address Age Hardware Type Interface\nInternet 10.10.20.1 - aabb.ccdd.eeff ARPA Gi2\n' >"$SONAR_IOS_SOURCE_DIR/sonar_show_ip_arp.out"
bash "$publisher" --sync-only
cache="$SONAR_PROBER_HOME/data/ios_cli"
for name in show_ip_interface_brief show_ip_route show_ip_arp; do
    cmp "$SONAR_IOS_SOURCE_DIR/sonar_$name.out" "$cache/$name.out"
    [[ $(stat -c %Y "$SONAR_IOS_SOURCE_DIR/sonar_$name.out") == $(stat -c %Y "$cache/$name.out") ]]
done
cp "$cache/show_ip_route.out" "$fixture/last-good"
printf '%% Authorization failed.\n' >"$SONAR_IOS_SOURCE_DIR/sonar_show_ip_route.out"
if bash "$publisher" --sync-only 2>"$fixture/expected-error"; then echo 'Bad source was accepted' >&2; exit 1; fi
cmp "$fixture/last-good" "$cache/show_ip_route.out"
cp "$fixture/last-good" "$SONAR_IOS_SOURCE_DIR/sonar_show_ip_route.out"
pids=()
for n in 1 2 3 4 5 6; do bash "$publisher" --sync-only & pids+=("$!"); done
for pid in "${pids[@]}"; do wait "$pid"; done
[[ -z $(find "$cache" -name '.snapshot.*' -print -quit) ]]
# The sync lock must be released before the long-lived Prober is launched.
flock -n "$SONAR_PROBER_HOME/snapshot.lock" true
exec 8>"$SONAR_PROBER_HOME/snapshot.lock"
flock 8
set +e
bash "$publisher" --sync-only 2>"$fixture/expected-busy"
busy_status=$?
set -e
flock -u 8
exec 8>&-
[[ $busy_status == 75 ]]
printf '[ok] publisher preserves data/mtime, rejects errors, serializes six writers, leaves no temp files or held sync lock; reports busy instead of false success\n'
