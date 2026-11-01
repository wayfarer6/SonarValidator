#!/usr/bin/env python3
import argparse
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import urllib.request

from labctl import LabControl, LABS, ROOT


def mock_is_running():
    try:
        with urllib.request.urlopen("http://127.0.0.1:3049/v1/models", timeout=2) as response:
            value = json.load(response)
        return any(model.get("id") == "mock-advice-v1" for model in value.get("data", []))
    except Exception:
        return False


def restore(control, original):
    known = {node for site in LABS.values() for node in site["nodes"]}
    current = {node["node_id"]: node for node in control.gns("/nodes")}
    for node in known:
        if current[node]["status"] == "started" and original[node] != "started":
            control.gns("/nodes/" + node + "/stop", {}, "POST")
    for node in known:
        if original[node] == "started" and current[node]["status"] != "started":
            control.gns("/nodes/" + node + "/start", {}, "POST")


def main():
    parser = argparse.ArgumentParser(description="Record feature scenarios sequentially, then restore the initial GNS3 node state.")
    parser.add_argument("lab", choices=["all", *LABS])
    parser.add_argument("mode", choices=["check", "demo"], nargs="?", default="demo")
    parser.add_argument("--scenario", choices=["features", "deployment", "isolation", "presentation"], default="features")
    parser.add_argument("--prepare", action="store_true", help="Register/deploy current bundles before testing. Lab2 requires SONAR_DEVICE_PASSWORD.")
    parser.add_argument("--reboot", action="store_true", help="Stop/start the selected Lab after preparation.")
    parser.add_argument("--keep-active", action="store_true", help="Keep the final Lab running instead of restoring the initial state.")
    args = parser.parse_args()
    if not os.environ.get("SONAR_UI_PASSWORD"):
        parser.error("Set SONAR_UI_PASSWORD without putting it in source or reports.")
    state_dir = Path.home() / ".local/share/sonarvalidator/labs"
    state_dir.mkdir(parents=True, exist_ok=True)
    with (state_dir / "runner.lock").open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            parser.error("Another Lab runner is active.")
        control = LabControl()
        original = {node["node_id"]: node["status"] for node in control.gns("/nodes")}
        selected = list(LABS) if args.lab == "all" else [args.lab]
        output = Path(os.environ.get("ROBOT_OUTPUT_DIR", str(ROOT / "test-results/labs" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"))))
        output.mkdir(parents=True, exist_ok=True)
        (output / "initial-node-state.json").write_text(json.dumps(original, indent=2))
        env = os.environ.copy()
        node_dir = env.get("SONAR_NODE_BIN", str(Path.home() / ".nvm/versions/node/v26.13.0/bin"))
        env["PATH"] = node_dir + ":" + env["PATH"]
        env["ROBOT_OUTPUT_DIR"] = str(output)
        env.setdefault("SONAR_HEADLESS", "true")
        mock = None
        try:
            if args.scenario == "features" and not mock_is_running():
                with socket.socket() as probe:
                    probe.settimeout(1)
                    if probe.connect_ex(("127.0.0.1", 3049)) == 0:
                        raise RuntimeError("Port 3049 belongs to another service; it will not be replaced.")
                with (output / "mock-provider.log").open("w") as log:
                    mock = subprocess.Popen([str(Path(node_dir) / "node"), str(ROOT / "tests/robot/fixtures/ai-provider.mjs")],
                        cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
                import time
                for attempt in range(30):
                    if mock_is_running():
                        break
                    time.sleep(0.2)
                if args.scenario == "features" and not mock_is_running():
                    raise RuntimeError("Mock provider did not start.")
            for lab in selected:
                print("Recording " + lab, flush=True)
                control.activate(lab)
                control.wait_ready(lab, timeout=600)
                if args.prepare:
                    control.bootstrap(lab)
                if args.reboot:
                    for node in LABS[lab]["nodes"]:
                        control.gns("/nodes/" + node + "/stop", {}, "POST")
                    for node in LABS[lab]["nodes"]:
                        control.gns("/nodes/" + node + "/start", {}, "POST")
                    control.wait_ready(lab, timeout=600)
                cases = LABS[lab]["isolation_cases"] if args.scenario == "isolation" else [None]
                for case in cases:
                    case_env = env.copy()
                    if case:
                        for key, value in {"CASE_ID": case["id"], "AGENT": case["agent"], "CIDR": case["cidr"],
                                "PROBE": case["probe"], "DESTINATION": case["destination"],
                                "INTERFACE_DOWN": str(case["interface_down"]).lower()}.items():
                            case_env["SONAR_ISOLATION_" + key] = value
                    result = subprocess.run(["bash", "tests/robot/run.sh", lab, args.mode, args.scenario], cwd=ROOT, env=case_env)
                    if result.returncode:
                        raise RuntimeError(lab + " scenario failed; inspect " + str(output / lab))
        finally:
            if mock is not None:
                mock.terminate()
                mock.wait(timeout=10)
            if not args.keep_active:
                restore(control, original)
                print("Restored initial GNS3 start/stop state.", flush=True)
        print("Reports and recordings: " + str(output), flush=True)


if __name__ == "__main__":
    main()
