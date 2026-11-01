#!/bin/sh
set -eu
base=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export SONAR_CONFIG_PATH="$base/Installer/default.conf"
export SONAR_TEMPLATE_PATH="$base/Installer/default_template.sqlite"
export SONAR_DATA_DIR="$base/data"
mkdir -p "$SONAR_DATA_DIR"
exec 9>"$base/prober.lock"
flock -n 9 || exit 0
exec "$base/Installer/sonar_validator_prober"
