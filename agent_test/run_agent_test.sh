#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

if [ ! -x "$SCRIPT_DIR/sonar_validator_prober" ]; then
    echo "Agent test binary is missing; run agent_test/build_agent.sh first." >&2
    exit 1
fi

export SONAR_DATA_DIR="${SONAR_DATA_DIR:-$SCRIPT_DIR/data}"
export SONAR_CONFIG_PATH="${SONAR_CONFIG_PATH:-$SCRIPT_DIR/default.conf}"
export SONAR_TEMPLATE_PATH="${SONAR_TEMPLATE_PATH:-$ROOT_DIR/SonarValidator_Prober/Installer/default_template.sqlite}"
export LD_LIBRARY_PATH="$SCRIPT_DIR/lib:${LD_LIBRARY_PATH:-}"

exec "$SCRIPT_DIR/sonar_validator_prober" "$@"
