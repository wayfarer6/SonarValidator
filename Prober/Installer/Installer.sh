#!/bin/sh

# 스크립트에서 발생하는 오류나 에러가 발생하면 즉시 스크립트를 종료하는 옵션입니다. 
set -eu

if [ "$(id -u)" -ne 0 ]; then
	echo "Please run this installer as root." >&2
	exit 1
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SOURCE_BINARY="$SCRIPT_DIR/sonar_validator_prober"
TARGET_BINARY="/usr/local/bin/sonar_validator_prober"
TARGET_CONFIG="/etc/sonar_validator_prober/default.conf"
TARGET_TEMPLATE="/etc/sonar_validator_prober/sqlite_template.sqlite"
TARGET_DATA_DIR="/var/lib/sonar_validator_prober"

if [ ! -x "$SOURCE_BINARY" ]; then
	echo "Binary not found in Installer bundle: $SOURCE_BINARY" >&2
	echo "Build first, then stage it with: cmake --build build --target installer_bundle" >&2
	exit 1
fi

if [ -d /run/systemd/system ] && command -v systemctl >/dev/null 2>&1; then
	INIT_SYSTEM="systemd"
elif command -v rc-update >/dev/null 2>&1 && command -v rc-service >/dev/null 2>&1; then
	INIT_SYSTEM="openrc"
else
	INIT_SYSTEM="none"
	echo "Neither systemd nor OpenRC was detected; install files only." >&2
fi


# Auto-detect Cisco guestshell; an explicit profile also works with sudo's PATH.
PROFILE=${SONAR_INSTALL_PROFILE:-auto}
if [ "$PROFILE" = auto ]; then
    PROFILE=generic
    if id guestshell >/dev/null 2>&1 && { command -v dohost >/dev/null 2>&1 || [ -x /usr/bin/dohost ]; }; then
        PROFILE=cisco
    fi
fi
case "$PROFILE" in
    cisco)
        id guestshell >/dev/null 2>&1 || { echo "Cisco profile requires guestshell user" >&2; exit 1; }
        SERVICE_SOURCE="$SCRIPT_DIR/systemd/sonar_validator_prober-cisco.service"
        SERVICE_USER=guestshell
        ;;
    generic)
        SERVICE_SOURCE="$SCRIPT_DIR/systemd/sonar_validator_prober.service"
        SERVICE_USER=root
        ;;
    *) echo "Unknown SONAR_INSTALL_PROFILE: $PROFILE (auto|generic|cisco)" >&2; exit 1 ;;
esac
SERVICE_GROUP=$(id -gn "$SERVICE_USER")
for asset in "$SCRIPT_DIR/default.conf" "$SCRIPT_DIR/default_template.sqlite"; do
    [ -f "$asset" ] || { echo "Missing bundle asset: $asset" >&2; exit 1; }
done
if [ "$INIT_SYSTEM" = systemd ]; then
    [ -f "$SERVICE_SOURCE" ] || { echo "Missing service: $SERVICE_SOURCE" >&2; exit 1; }
    if systemctl is-active --quiet sonar_validator_prober.service; then
        systemctl stop sonar_validator_prober.service
    fi
elif [ "$INIT_SYSTEM" = openrc ]; then
    [ -f "$SCRIPT_DIR/rc-service/sonar_validator_prober" ] || {
        echo "Missing OpenRC service; stage a complete installer bundle" >&2; exit 1;
    }
    if rc-service sonar_validator_prober status >/dev/null 2>&1; then
        rc-service sonar_validator_prober stop
    fi
fi

install -o root -g root -Dm755 "$SOURCE_BINARY" "$TARGET_BINARY"
install -o "$SERVICE_USER" -g "$SERVICE_GROUP" -d -m750 /etc/sonar_validator_prober
install -o "$SERVICE_USER" -g "$SERVICE_GROUP" -Dm600 "$SCRIPT_DIR/default.conf" "$TARGET_CONFIG"
install -o root -g root -Dm644 "$SCRIPT_DIR/default_template.sqlite" "$TARGET_TEMPLATE"
install -o "$SERVICE_USER" -g "$SERVICE_GROUP" -d -m750 "$TARGET_DATA_DIR"
# Existing data was created by the previous service account (often root).
chown -R "$SERVICE_USER:$SERVICE_GROUP" "$TARGET_DATA_DIR"

if [ "$INIT_SYSTEM" = "systemd" ]; then
	install -Dm644 "$SERVICE_SOURCE" \
		/etc/systemd/system/sonar_validator_prober.service
	systemctl daemon-reload
	systemctl enable sonar_validator_prober.service
	systemctl restart sonar_validator_prober.service
	echo "Installed and started with systemd (profile=$PROFILE, user=$SERVICE_USER)."
elif [ "$INIT_SYSTEM" = "openrc" ]; then
	install -Dm755 "$SCRIPT_DIR/rc-service/sonar_validator_prober" \
		/etc/init.d/sonar_validator_prober
	rc-update add sonar_validator_prober default
	rc-service sonar_validator_prober start
	echo "Installed and started with OpenRC."
else
	echo "Installed files. Start sonar_validator_prober manually."
fi


