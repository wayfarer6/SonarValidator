#!/usr/bin/env python3
import argparse
import base64
import configparser
import http.cookiejar
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shlex
import socket
import subprocess
import tarfile
import time
import urllib.parse
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
INVENTORY = json.loads((Path(__file__).parent / "inventory.json").read_text())
LABS = INVENTORY["labs"]
for lab_name, site in LABS.items():
    site["project_id"] = os.environ.get("SONAR_" + lab_name.upper() + "_PROJECT_ID", site["project_id"])


def cache_ubuntu_tools():
    cache = Path(os.environ.get("SONAR_UBUNTU_PACKAGE_CACHE", str(Path.home() / ".cache/sonarvalidator/ubuntu-packages"))).resolve()
    cache.mkdir(parents=True, exist_ok=True)
    subprocess.run(["docker", "run", "--rm", "--entrypoint", "/bin/sh", "--mount",
        "type=bind,source=" + str(cache) + ",target=/opt/packages", "ubuntu:26.04", "-ec",
        "mkdir -p /opt/packages/partial; apt-get -o Acquire::ForceIPv4=true update -qq; "
        "apt-get -y --download-only -o Dir::Cache::archives=/opt/packages install iproute2 nftables util-linux iputils-ping"], check=True)
    print("Ubuntu package cache: " + str(cache))


class LabControl:
    def __init__(self):
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.logged_in = False
        self.api_base = os.environ.get("SONAR_API_URL", "http://127.0.0.1:3000") + "/api/v1"
        self.gns_base = os.environ.get("SONAR_GNS3_URL", "http://127.0.0.1:3080") + "/v2/projects/" + INVENTORY["gns3_project_id"]

    def api(self, path, body=None, method=None, binary=False):
        if not self.logged_in and path != "/auth/login":
            password = os.environ.get("SONAR_UI_PASSWORD")
            if not password:
                raise RuntimeError("Set SONAR_UI_PASSWORD; credentials are never stored in test sources.")
            self.api("/auth/login", {"username": os.environ.get("SONAR_UI_USERNAME", "admin"), "password": password})
            self.logged_in = True
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.api_base + path, data=data, method=method,
                                         headers={"Content-Type": "application/json"})
        with self.opener.open(request, timeout=45) as response:
            return response.read() if binary else json.load(response)

    def gns(self, path, body=None, method=None):
        config = configparser.ConfigParser()
        config.read(Path.home() / ".config/GNS3/2.2/gns3_server.conf")
        auth = base64.b64encode((config["Server"]["user"] + ":" + config["Server"]["password"]).encode()).decode()
        request = urllib.request.Request(self.gns_base + path,
            data=None if body is None else json.dumps(body).encode(), method=method,
            headers={"Authorization": "Basic " + auth, "Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=35) as response:
            return json.load(response) if response.status != 204 else None

    def activate(self, lab):
        selected = set(LABS[lab]["nodes"])
        known = {node for value in LABS.values() for node in value["nodes"]}
        nodes = {node["node_id"]: node for node in self.gns("/nodes")}
        for node_id in known - selected:
            if nodes[node_id]["status"] == "started":
                self.gns("/nodes/" + node_id + "/stop", {}, "POST")
                print("Stopped " + nodes[node_id]["name"], flush=True)
        for node_id in LABS[lab]["nodes"]:
            if nodes[node_id]["status"] != "started":
                self.gns("/nodes/" + node_id + "/start", {}, "POST")
                print("Started " + nodes[node_id]["name"], flush=True)

    def ssh_command(self, agent, script):
        command = ["ssh", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes",
                   "-o", "LogLevel=ERROR", "-o", "ConnectTimeout=5",
                   "-o", "ServerAliveInterval=10", "-o", "ServerAliveCountMax=2",
                   "-p", str(agent.get("port", 22))]
        if agent.get("jump"):
            command += ["-J", agent["jump"]]
        if agent.get("profile") == "eos":
            payload = base64.b64encode(script.encode()).decode()
            invocation = 'exec ' + ('sudo -n ' if agent.get('elevate') else '') + '/bin/bash -c "$(printf %s ' + payload + ' | base64 -d)"'
            script = "enable\nbash /bin/bash -c " + shlex.quote(invocation)
        command += [agent.get("user", "root") + "@" + agent["host"], script]
        return command

    def ssh(self, agent, script, data=None, timeout=45, check=True):
        command = self.ssh_command(agent, script)
        result = subprocess.run(command, input=data, capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(agent.get("id", agent["host"]) + ": SSH command failed: " +
                               result.stderr.decode(errors="replace")[-400:])
        return result

    def wait_ready(self, lab, timeout=420):
        pending = list(LABS[lab]["agents"])
        firewall = LABS[lab].get("firewall")
        if firewall:
            pending.insert(0, {**firewall, "user": "root"})
        deadline = time.monotonic() + timeout
        while pending and time.monotonic() < deadline:
            for agent in pending[:]:
                try:
                    result = self.ssh(agent, "echo SONAR_READY", timeout=12, check=False)
                    if result.returncode == 0 and b"SONAR_READY" in result.stdout:
                        pending.remove(agent)
                        print("SSH ready " + agent["id"], flush=True)
                except subprocess.TimeoutExpired:
                    pass
            if pending:
                time.sleep(3)
        if pending:
            raise RuntimeError("SSH readiness timeout: " + ", ".join(a["id"] for a in pending))

    def ensure_project(self, lab):
        site = LABS[lab]
        try:
            project = self.api("/projects/" + site["project_id"])
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
            project = self.api("/projects", {"project_id": site["project_id"],
                "name": lab.capitalize(), "category": "Network validation",
                "description": site["title"], "status": "Planning",
                "management_prefix": site["management_prefix"]})
            self.api("/projects/" + site["project_id"], {
                "management_server_ip": site["server_ip"], "management_server_port": 3000}, "PUT")
        if project.get("management_prefix") != site["management_prefix"]:
            raise RuntimeError("Project management prefix differs from inventory; review it before deployment.")

    def register(self, lab):
        self.ensure_project(lab)
        site = LABS[lab]
        existing = {a["agent_id"]: a for a in self.api("/agents/expected")["agents"]}
        for agent in site["agents"]:
            previous = existing.get(agent["id"])
            if previous and previous["project_id"] != site["project_id"]:
                raise RuntimeError("Agent already belongs to another project: " + agent["id"])
            self.api("/agents/expected", {
                "agent_id": agent["id"], "project_id": site["project_id"],
                "device_type": "VM" if agent["type"] == "VM" else agent["type"].upper(),
                "node_type": agent["type"], "expected_ip": agent["host"],
                "note": previous.get("note") if previous else ("Lab inventory; SSH jump required" if agent.get("jump") else "Lab inventory")})
            print("Registered " + agent["id"], flush=True)

    def configure_firewall(self, lab):
        site = LABS[lab]
        firewall = site.get("firewall")
        if not firewall:
            return None
        env_path = Path.home() / ".config/sonarvalidator/labs" / (lab + "-opnsense-api.env")
        values = {}
        for line in env_path.read_text().splitlines():
            match = re.match(r"(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)", line.strip())
            if match:
                values[match[1]] = shlex.split(match[2])[0]
        key = next(v for k, v in values.items() if k.upper().endswith("KEY"))
        secret = next(v for k, v in values.items() if k.upper().endswith("SECRET"))
        result = self.api("/opnsense/credentials/" + firewall["id"], {
            "display_name": firewall["id"], "base_url": "https://" + firewall["host"],
            "api_key": key, "api_secret": secret, "allow_insecure_tls": True,
            "verify_now": True, "project_key": site["project_id"]}, "PUT")
        if result["status"] != "OK":
            raise RuntimeError("OPNsense verify failed: " + str(result.get("last_error")))
        print("OPNsense API verified " + firewall["id"], flush=True)
        return {"agent_id": firewall["id"], "status": result["status"], "version": result["detected_version"]}

    def _bundle(self, site, agent):
        query = urllib.parse.urlencode({
            "node_type": agent["type"], "project_id": site["project_id"], "data_directory": agent["root"] + "/data"})
        raw = self.api("/agents/download/" + agent["id"] + "?" + query, binary=True)
        needs_packages = agent["profile"] == "ubuntu" and self.ssh(agent,
            "command -v ip >/dev/null && command -v nft >/dev/null && command -v flock >/dev/null && command -v ping >/dev/null",
            check=False).returncode != 0
        output = io.BytesIO()
        with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as original, tarfile.open(fileobj=output, mode="w:gz") as target:
            for item in original.getmembers():
                if not item.isfile() or not item.name.startswith("Installer/") or ".." in Path(item.name).parts:
                    raise RuntimeError("Unsafe bundle member")
                content = original.extractfile(item).read()
                if item.name == "Installer/default.conf":
                    text = content.decode()
                    text = re.sub(r"^MANAGEMENT_PREFIX=.*$", "MANAGEMENT_PREFIX=" + site["management_prefix"] + ";", text, flags=re.M)
                    content = text.encode()
                    item.mode = 0o600
                if item.name == "Installer/sonar_validator_prober":
                    expected = os.environ.get("SONAR_EXPECTED_PROBER_SHA256")
                    if expected and hashlib.sha256(content).hexdigest() != expected.lower():
                        raise RuntimeError("Downloaded Prober does not match SONAR_EXPECTED_PROBER_SHA256; update the backend stage directory.")
                    item.name += ".next"
                item.size = len(content)
                target.addfile(item, io.BytesIO(content))
            if needs_packages:
                cache = Path(os.environ.get("SONAR_UBUNTU_PACKAGE_CACHE", str(Path.home() / ".cache/sonarvalidator/ubuntu-packages")))
                packages = sorted(cache.glob("*.deb"))
                if not packages:
                    raise RuntimeError("Run python3 tests/labs/labctl.py cache-tools for this legacy Ubuntu image.")
                for package in packages:
                    content = package.read_bytes()
                    item = tarfile.TarInfo("packages/" + package.name)
                    item.size, item.mode = len(content), 0o644
                    target.addfile(item, io.BytesIO(content))
            launcher = ROOT / ("docs/static/scripts/lab1-prober-start.sh" if agent["profile"] == "ios" else "tests/labs/start-prober.sh")
            content = launcher.read_bytes()
            item = tarfile.TarInfo("start.sh")
            item.size, item.mode = len(content), 0o755
            target.addfile(item, io.BytesIO(content))
        return output.getvalue()

    def _upload_text(self, agent, path, text, mode="755"):
        command = "umask 077; cat > " + shlex.quote(path) + "; chmod " + mode + " " + shlex.quote(path)
        self.ssh(agent, command, text.encode())

    def _console(self, port, commands):
        with socket.create_connection(("127.0.0.1", port), timeout=10) as channel:
            channel.settimeout(0.5)
            def receive(limit=12):
                data = b""
                password_sent = False
                deadline = time.monotonic() + limit
                while time.monotonic() < deadline:
                    try:
                        part = channel.recv(65536)
                        if not part:
                            break
                        data += part
                        text = data.decode(errors="replace")
                        if re.search(r"Password:\s*$", text):
                            password = os.environ.get("SONAR_DEVICE_PASSWORD")
                            if not password or password_sent:
                                raise RuntimeError("Cisco enable password is required or was rejected; set SONAR_DEVICE_PASSWORD.")
                            channel.sendall(password.encode() + b"\r")
                            password_sent = True
                            data = b""
                            continue
                        if re.search(r"[A-Za-z0-9_.()/-]+[>#]\s*$", text):
                            return text
                        if "[confirm]" in text:
                            channel.sendall(b"\r")
                    except socket.timeout:
                        pass
                raise RuntimeError("Cisco console prompt timeout")
            channel.sendall(b"\r")
            receive()
            output = []
            for command in commands:
                channel.sendall(command.encode() + b"\r")
                try:
                    result = receive(limit=60 if command.strip() == "write memory" else 15)
                except RuntimeError as error:
                    raise RuntimeError("Cisco console failed after: " + command) from error
                if re.search(r"% (?:Invalid input|Incomplete command|Ambiguous command|Error)", result):
                    raise RuntimeError("Cisco rejected command: " + command)
                output.append(result)
            return "\n".join(output)

    def _persist_start(self, agent):
        root = agent["root"]
        launch = "nohup /bin/sh " + shlex.quote(root + "/start.sh") + " >>" + shlex.quote(root + "/prober.log") + " 2>&1 </dev/null &"
        if agent["profile"] == "ios":
            name = "SONAR-" + agent["id"].split("-")[0] + "-Collect"
            commands = [
                "enable", "configure terminal",
                "event manager session cli username guestshell privilege 15",
                "no event manager applet " + name,
                "event manager applet " + name + " authorization bypass",
                "event timer watchdog time 60 maxrun 55",
                'action 010 cli command "enable"',
                'action 015 cli command "terminal length 0"']
            for number, operation in [(20, "show ip interface brief"), (30, "show ip route"), (40, "show ip arp")]:
                file = operation.replace(" ", "_")
                commands += [
                    'action %03d cli command "%s | redirect bootflash:guest-share/sonar_%s.out" pattern "confirm|#"' % (number, operation, file),
                    'action %03d cli command ""' % (number + 1)]
            commands += ['action 050 cli command "guestshell run /bin/bash /home/guestshell/sonar/start.sh"', "end", "write memory"]
            console = self.gns("/nodes/" + agent["gns3_node"])["console"]
            directory = "/bootflash/guest-share/sonar-cli"
            self.ssh(agent, "mkdir -p " + directory + " && chmod 770 " + directory)
            self._upload_text(agent, directory + "/sonar_cli_bridge.tcl",
                (ROOT / "SonarValidator_Prober/Installer/sonar_cli_bridge.tcl").read_text())
            current = self._console(console, ["enable", "show running-config | include event manager directory user policy"])
            configured = re.findall(r"^event manager directory user policy (.+)$", current, re.MULTILINE)
            if any(value.strip().strip('"').rstrip("/").replace("bootflash:guest-share/", "bootflash:/guest-share/") != "bootflash:/guest-share/sonar-cli" for value in configured):
                raise RuntimeError("An existing EEM user policy directory must be preserved; configure the bridge there.")
            commands += ["configure terminal", "event manager directory user policy bootflash:/guest-share/sonar-cli",
                "no event manager policy sonar_cli_bridge.tcl", "event manager policy sonar_cli_bridge.tcl type user authorization bypass",
                "end", "write memory"]
            self._console(console, commands)
            self.ssh(agent, "/bin/bash " + shlex.quote(root + "/start.sh"))
        elif agent["profile"] == "eos":
            script = "\n".join(["configure terminal", "event-handler SONAR-Prober-Start",
                "action bash /bin/bash " + root + "/boot.sh", "trigger onboot", "delay 30", "exit", "end", "write memory"])
            self._upload_text(agent, root + "/boot.sh", "#!/bin/bash\n" + launch + "\n")
            self.ssh(agent, "FastCli -p 15 -M -c " + shlex.quote(script))
            self.ssh(agent, launch)
        elif agent["profile"] == "ubuntu":
            boot = ("#!/bin/sh\nset -eu\n"
                + "if ! command -v ip >/dev/null || ! command -v nft >/dev/null; then\n"
                + "  dpkg -i " + root + "/packages/*.deb >" + root + "/packages-install.log 2>&1 || echo Prober_tools_restore_failed >>" + root + "/packages-install.log\nfi\n"
                + launch + "\nexec /root/start.sh\n")
            self._upload_text(agent, root + "/boot.sh", boot)
            node = self.gns("/nodes/" + agent["gns3_node"])
            wanted = "/bin/sh " + root + "/boot.sh"
            if node["properties"].get("start_command") != wanted:
                self.gns("/nodes/" + agent["gns3_node"], {"properties": {"start_command": wanted}}, "PUT")
                for attempt in range(30):
                    try:
                        if self.ssh(agent, "echo READY", check=False, timeout=10).returncode == 0:
                            break
                    except subprocess.TimeoutExpired:
                        pass
                    time.sleep(1)
            self.ssh(agent, launch)
        elif agent["profile"] == "ovs":
            hook = "# SonarValidator launcher"
            script = ("set -eu; p=/etc/switch/lab4-start.sh; if ! grep -q " + shlex.quote(hook) +
                ' "$p"; then awk -v start=' + shlex.quote(launch) +
                " " + shlex.quote(r'(/\/usr\/sbin\/sshd -t/ || /^exec .*sshd/) && !done { print "# SonarValidator launcher"; print start; done=1 } {print} END {if (!done) exit 2}') +
                ' "$p" >"$p.next"; chmod 755 "$p.next"; mv "$p.next" "$p"; fi')
            self.ssh(agent, script)
            self.ssh(agent, launch)
        elif agent["profile"] == "frr":
            self._upload_text(agent, "/etc/local.d/sonar-prober.start", "#!/bin/sh\n" + launch + "\n")
            self.ssh(agent, "rc-update add local default >/dev/null 2>&1; " + launch)

    def deploy_agent(self, lab, agent):
        if agent["profile"] == "eos":
            agent = dict(agent, elevate=True)
        site = LABS[lab]
        root = agent["root"]
        command = ("umask 077; mkdir -p " + shlex.quote(root) + "; tar xzf - -C " +
            shlex.quote(root) + "; chmod 600 " + shlex.quote(root + "/Installer/default.conf") +
            "; mv -f " + shlex.quote(root + "/Installer/sonar_validator_prober.next") + " " +
            shlex.quote(root + "/Installer/sonar_validator_prober") + "; sync")
        bundle = self._bundle(site, agent)
        with tarfile.open(fileobj=io.BytesIO(bundle), mode="r:gz") as archive:
            digest = hashlib.sha256(archive.extractfile("Installer/sonar_validator_prober.next").read()).hexdigest()
        self.ssh(agent, command, data=bundle, timeout=90)
        installed = self.ssh(agent, "sha256sum " + shlex.quote(root + "/Installer/sonar_validator_prober")).stdout.decode().split()[0]
        if installed != digest:
            raise RuntimeError("Installed Prober checksum mismatch: " + agent["id"])
        print("Verified Prober " + agent["id"] + " sha256=" + digest, flush=True)
        pattern = "^" + re.escape(root + "/Installer/sonar_validator_prober") + "$"
        self.ssh(agent, "pkill -TERM -f " + shlex.quote(pattern) + " || true")
        time.sleep(1)
        self._persist_start(agent)

    def bootstrap(self, lab):
        site = LABS[lab]
        self.register(lab)
        self.configure_firewall(lab)
        for agent in site["agents"]:
            self.deploy_agent(lab, agent)
            if agent["profile"] == "ubuntu":
                self.ssh(agent, "command -v ip >/dev/null && command -v nft >/dev/null || dpkg -i " +
                    shlex.quote(agent["root"]) + "/packages/*.deb", timeout=120)
            print("Deployed " + agent["id"], flush=True)

    def telemetry(self, lab):
        site = LABS[lab]
        result = {}
        for agent in site["agents"]:
            try:
                result[agent["id"]] = self.api("/agents/" + agent["id"] + "/telemetry")
            except urllib.error.HTTPError as error:
                result[agent["id"]] = {"http_status": error.code}
        return result


def main():
    parser = argparse.ArgumentParser(description="Start one known GNS3 Lab and deploy or inspect its Probers.")
    commands = parser.add_subparsers(dest="command", required=True)
    for command in ["activate", "ready", "bootstrap", "telemetry", "status"]:
        commands.add_parser(command).add_argument("lab", choices=LABS)
    commands.add_parser("cache-tools", help="Cache offline tools for older Ubuntu images")
    args = parser.parse_args()
    if args.command == "cache-tools":
        cache_ubuntu_tools()
        return
    control = LabControl()
    if args.command == "activate":
        control.activate(args.lab)
    elif args.command == "ready":
        control.wait_ready(args.lab)
    elif args.command == "bootstrap":
        control.wait_ready(args.lab)
        control.bootstrap(args.lab)
    elif args.command == "telemetry":
        print(json.dumps(control.telemetry(args.lab), ensure_ascii=False, indent=2))
    else:
        print(json.dumps([{k: n[k] for k in ["node_id", "name", "status"]} for n in control.gns("/nodes") if n["node_id"] in LABS[args.lab]["nodes"]], indent=2))


if __name__ == "__main__":
    main()
