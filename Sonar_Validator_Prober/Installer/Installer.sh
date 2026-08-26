#!/bin/sh

set -eu

if [ "$(id -u)" -ne 0 ]; then
	echo "Please run this installer as root." >&2
	exit 1
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
SOURCE_BINARY="$PROJECT_DIR/build/sonar_validator_prober"
TARGET_BINARY="/usr/local/bin/sonar_validator_prober"
TARGET_CONFIG="/etc/sonar_validator_prober/default.conf"
TARGET_DATA_DIR="/var/lib/sonar_validator_prober"

if [ ! -x "$SOURCE_BINARY" ]; then
	echo "Binary not found: $SOURCE_BINARY" >&2
	echo "Build the project first with: cmake --build build" >&2
	exit 1
fi

if [ -d /run/systemd/system ] && command -v systemctl >/dev/null 2>&1; then
	INIT_SYSTEM="systemd"
elif command -v rc-update >/dev/null 2>&1 && command -v rc-service >/dev/null 2>&1; then
	INIT_SYSTEM="openrc"
else
	echo "Neither systemd nor OpenRC was detected." >&2
	exit 1
fi

install -Dm755 "$SOURCE_BINARY" "$TARGET_BINARY"
install -Dm644 "$PROJECT_DIR/Installer/default.conf" "$TARGET_CONFIG"
install -d -m750 "$TARGET_DATA_DIR"

if [ "$INIT_SYSTEM" = "systemd" ]; then
	install -Dm644 "$PROJECT_DIR/systemd/prober.service" \
		/etc/systemd/system/sonar_validator_prober.service
	systemctl daemon-reload
	systemctl enable --now sonar_validator_prober.service
	echo "Installed and started with systemd."
else
	install -Dm755 "$PROJECT_DIR/rc-service/sonar_validator_prober" \
		/etc/init.d/sonar_validator_prober
	rc-update add sonar_validator_prober default
	rc-service sonar_validator_prober start
	echo "Installed and started with OpenRC."
fi


