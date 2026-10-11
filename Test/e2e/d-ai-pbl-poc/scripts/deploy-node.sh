#!/bin/sh
set -eu
stage=$1
mode=$2
dest=/etc/sonar_validator_prober
backup=/root/sonar-backup-e2e2-$(date +%Y%m%d-%H%M%S)
mkdir -p "$backup" "$dest"
[ ! -d "$dest" ] || cp -a "$dest" "$backup/config"
[ ! -f /usr/local/bin/sonar_validator_prober ] || cp -p /usr/local/bin/sonar_validator_prober "$backup/"
[ ! -f /root/start.sh ] || cp -p /root/start.sh "$backup/"
if [ "$mode" = openrc ]; then
  rc-service sonar_validator_prober stop || true
elif [ "$mode" = systemd ]; then
  systemctl stop sonar_validator_prober.service || true
fi
if [ -f /run/sonar-supervisor.pid ]; then
  pid=$(cat /run/sonar-supervisor.pid)
  if [ -r "/proc/$pid/cmdline" ] && tr '\000' ' ' < "/proc/$pid/cmdline" | grep -q '/etc/sonar_validator_prober/supervise.sh'; then kill -TERM "$pid" || true; fi
fi
for proc in /proc/[0-9]*; do
  executable=$(readlink "$proc/exe" 2>/dev/null) || continue
  case "$executable" in */sonar_validator_prober|*/sonar_validator_prober\ \(deleted\)) kill -TERM "${proc#/proc/}" || true;; esac
done
sleep 3
for proc in /proc/[0-9]*; do
  executable=$(readlink "$proc/exe" 2>/dev/null) || continue
  case "$executable" in */sonar_validator_prober|*/sonar_validator_prober\ \(deleted\)) echo 'Old prober still running; deployment stopped'; exit 1;; esac
done
cp "$stage/default.conf" "$dest/default.conf"
chmod 600 "$dest/default.conf"
cp "$stage/default_template.sqlite" "$dest/sqlite_template.sqlite"
if [ "$mode" = container ]; then
  cp "$stage/sonar_validator_prober" "$dest/sonar_validator_prober.new"
  chmod 755 "$dest/sonar_validator_prober.new"
  mv "$dest/sonar_validator_prober.new" "$dest/sonar_validator_prober"
  mkdir -p "$dest/data"
  cat > "$dest/supervise.sh" <<'SH'
#!/bin/sh
trap 'kill -TERM "$child" 2>/dev/null || true; wait "$child" 2>/dev/null || true; exit 0' TERM INT
export SONAR_CONFIG_PATH=/etc/sonar_validator_prober/default.conf
export SONAR_TEMPLATE_PATH=/etc/sonar_validator_prober/sqlite_template.sqlite
export SONAR_DATA_DIR=/etc/sonar_validator_prober/data
while true; do
  /etc/sonar_validator_prober/sonar_validator_prober &
  child=$!
  wait "$child" || true
  sleep 5
done
SH
  cat > "$dest/start.sh" <<'SH'
#!/bin/sh
if [ -f /run/sonar-supervisor.pid ]; then
  pid=$(cat /run/sonar-supervisor.pid)
  if [ -r "/proc/$pid/cmdline" ] && tr '\000' ' ' < "/proc/$pid/cmdline" | grep -q '/etc/sonar_validator_prober/supervise.sh'; then exit 0; fi
fi
mkdir -p /etc/sonar_validator_prober/data
nohup /bin/sh /etc/sonar_validator_prober/supervise.sh >>/etc/sonar_validator_prober/data/prober.log 2>&1 </dev/null &
echo $! > /run/sonar-supervisor.pid
SH
  sh "$dest/start.sh"
  sha256sum "$dest/sonar_validator_prober"
else
  mkdir -p /usr/local/bin /var/lib/sonar_validator_prober
  cp "$stage/sonar_validator_prober" /usr/local/bin/sonar_validator_prober.new
  chmod 755 /usr/local/bin/sonar_validator_prober.new
  mv /usr/local/bin/sonar_validator_prober.new /usr/local/bin/sonar_validator_prober
  if [ "$mode" = openrc ]; then
    cp "$stage/rc-service/sonar_validator_prober" /etc/init.d/sonar_validator_prober
    chmod 755 /etc/init.d/sonar_validator_prober
    rc-update add sonar_validator_prober default
    rc-service sonar_validator_prober start
  else
    cp "$stage/systemd/sonar_validator_prober.service" /etc/systemd/system/sonar_validator_prober.service
    systemctl daemon-reload
    systemctl enable --now sonar_validator_prober.service
  fi
  sha256sum /usr/local/bin/sonar_validator_prober
fi
echo "BACKUP=$backup"
