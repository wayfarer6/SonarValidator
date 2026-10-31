#!/usr/bin/env bash
set -uo pipefail
here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo=$(cd "$here/../.." && pwd)
lab=${1:-lab1}
mode=${2:-check}
scenario=${3:-all}
case "$lab" in lab1|lab2|lab3|lab4|all) ;; *) echo "Lab: lab1|lab2|lab3|lab4|all" >&2; exit 2 ;; esac
case "$mode" in check|demo) ;; *) echo "Mode: check|demo" >&2; exit 2 ;; esac
venv=${SONAR_ROBOT_VENV:-$HOME/.local/share/sonarvalidator/robot-venv}
if [[ ! -x "$venv/bin/robot" ]]; then
  echo "Robot runtime missing. Install tests/robot/requirements.txt in $venv and run rfbrowser init chromium." >&2
  exit 2
fi
if [[ -z ${SONAR_UI_PASSWORD:-} ]]; then
  if [[ -t 0 ]]; then
    read -r -s -p "SonarValidator UI password: " SONAR_UI_PASSWORD
    echo
    export SONAR_UI_PASSWORD
  else
    echo "Set SONAR_UI_PASSWORD in the environment." >&2
    exit 2
  fi
fi
export PATH="${SONAR_NODE_BIN:-$HOME/.nvm/versions/node/v26.13.0/bin}:$venv/bin:$PATH"
if [[ "$mode" == demo ]]; then
  slow=${SONAR_SLOW_MO:-400ms}
  pause=${SONAR_STEP_PAUSE:-2s}
  headless=${SONAR_HEADLESS:-false}
else
  slow=${SONAR_SLOW_MO:-0ms}
  pause=${SONAR_STEP_PAUSE:-0s}
  headless=${SONAR_HEADLESS:-true}
fi
suite="$here/suites/lab_ui.robot"
if [[ "$scenario" == shutdown ]]; then
  suite="$here/suites/prober_shutdown.robot"
  scenario=all
fi
run_id=$(date -u +%Y%m%dT%H%M%SZ)
labs=("$lab")
[[ "$lab" != all ]] || labs=(lab1 lab2 lab3 lab4)
status=0
for item in "${labs[@]}"; do
  output=${ROBOT_OUTPUT_DIR:-$repo/test-results/robot/$run_id}/$item
  args=(--outputdir "$output" --name "SonarValidator-$item" --variable "LAB:$item"
    --variable "HEADLESS:$headless" --variable "SLOW_MO:$slow" --variable "STEP_PAUSE:$pause")
  [[ "$scenario" == all ]] || args+=(--test "$scenario")
  "$venv/bin/robot" "${args[@]}" "$suite" || status=1
  echo "Report: $output/report.html"
  echo "Scenario videos: $output/videos/$item/"
done
exit "$status"
