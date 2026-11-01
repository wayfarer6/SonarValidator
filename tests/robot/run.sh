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
if [[ "$scenario" == deployment ]]; then
  suite="$here/suites/agent_deployment.robot"
  scenario=all
fi
if [[ "$scenario" == regressions ]]; then
  suite="$here/suites/bug_regressions.robot"
  scenario=all
fi
if [[ "$scenario" == features ]]; then
  suite="$here/suites/lab_features.robot"
  scenario=all
fi
if [[ "$scenario" == ai ]]; then
  suite="$here/suites/ai_advice.robot"
  scenario=all
fi
if [[ "$scenario" == shutdown ]]; then
  suite="$here/suites/prober_shutdown.robot"
  scenario=all
fi
if [[ "$scenario" == isolation ]]; then
  suite="$here/suites/isolation_topology.robot"
  scenario=Q01_Isolate_Observe_And_Release
fi
if [[ "$scenario" == isolation-ui ]]; then
  suite="$here/suites/isolation_topology.robot"
  scenario=Q02_UI_Scope_Selection_And_Apply
fi
if [[ "$scenario" == isolation-reboot ]]; then
  suite="$here/suites/isolation_topology.robot"
  scenario=Q03_Active_Isolation_Journal_Survives_VM_Restart
fi
if [[ "$scenario" == presentation ]]; then
  suite="$here/suites/presentation_demo.robot"
  scenario=all
fi
run_id=$(date -u +%Y%m%dT%H%M%SZ)
labs=("$lab")
[[ "$lab" != all ]] || labs=(lab1 lab2 lab3 lab4)
status=0
for item in "${labs[@]}"; do
  output=${ROBOT_OUTPUT_DIR:-$repo/test-results/robot/$run_id}/$item
  [[ -z ${SONAR_ISOLATION_CASE_ID:-} ]] || output="$output/$SONAR_ISOLATION_CASE_ID"
  args=(--outputdir "$output" --name "SonarValidator-$item" --variable "LAB:$item"
    --variable "HEADLESS:$headless" --variable "SLOW_MO:$slow" --variable "STEP_PAUSE:$pause")
  if [[ -n ${SONAR_ISOLATION_AGENT:-} ]]; then
    args+=(--variable "ISOLATION_AGENT:$SONAR_ISOLATION_AGENT" --variable "ISOLATION_CIDR:${SONAR_ISOLATION_CIDR:-}"
      --variable "PROBE_AGENT:$SONAR_ISOLATION_PROBE" --variable "PROBE_DESTINATION:$SONAR_ISOLATION_DESTINATION"
      --variable "INTERFACE_DOWN:${SONAR_ISOLATION_INTERFACE_DOWN:-false}")
  fi
  [[ "$scenario" == all ]] || args+=(--test "$scenario")
  "$venv/bin/robot" "${args[@]}" "$suite" || status=1
  echo "Report: $output/report.html"
  echo "Scenario videos: $output/videos/$item/"
done
exit "$status"
