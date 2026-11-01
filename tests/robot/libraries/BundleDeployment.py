from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import shlex
import sys
import tarfile
import time
import uuid
from robot.api.deco import keyword

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "labs"))
from labctl import LabControl, LABS


class BundleDeployment:
    ROBOT_LIBRARY_SCOPE = "SUITE"

    def __init__(self, lab):
        self.lab = lab
        self.site = LABS[lab]
        self.control = LabControl()
        self.files = set()
        self.pending = {}
        self.receipts = []

    @keyword
    def wait_deployment_targets_ready(self):
        self.control.wait_ready(self.lab, timeout=600)

    @keyword
    def deployment_inventory(self):
        return self.site

    @keyword
    def inspect_frontend_bundle(self, agent_id, filename):
        path = Path(filename).resolve()
        self.files.add(path)
        raw = path.read_bytes()
        agent = dict(next(a for a in self.site["agents"] if a["id"] == agent_id))
        agent["elevate"] = agent["profile"] == "eos"
        with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as archive:
            members = archive.getmembers()
            assert all(m.isfile() and m.name.startswith("Installer/") and ".." not in Path(m.name).parts for m in members)
            config_entry = archive.getmember("Installer/default.conf")
            assert config_entry.mode == 0o600
            text = archive.extractfile(config_entry).read().decode()
            values = dict(line.rstrip(";").split("=",1) for line in text.splitlines() if line and not line.startswith("#") and "=" in line)
            project = self.control.api("/projects/" + self.site["project_id"])
            assert values["SERVER_IP"] == project["management_server_ip"] == self.site["server_ip"]
            assert int(values["SERVER_PORT"]) == project["management_server_port"]
            assert values["AGENT_NAME"] == agent_id
            assert values["NODE_TYPE"] == agent["type"]
            assert values["MANAGEMENT_PREFIX"] == self.site["management_prefix"]
            binary = archive.extractfile("Installer/sonar_validator_prober").read()
            template = archive.extractfile("Installer/default_template.sqlite").read()
            assert binary.startswith(b"\x7fELF") and template.startswith(b"SQLite format 3")
        receipt = {"agent_id":agent_id, "project_id":self.site["project_id"],
            "config":{key:values[key] for key in ["AGENT_NAME","NODE_TYPE","SERVER_IP","SERVER_PORT","MANAGEMENT_PREFIX"]},
            "archive_sha256":hashlib.sha256(raw).hexdigest(), "binary_sha256":hashlib.sha256(binary).hexdigest(), "config_sha256":hashlib.sha256(text.encode()).hexdigest(), "template_sha256":hashlib.sha256(template).hexdigest(), "config_mode":"0600"}
        self.pending[agent_id] = {"agent":agent,"file":path,"receipt":receipt,
            "remote":agent["root"]+"/.frontend-deploy-"+uuid.uuid4().hex[:10]}
        return {"command":"Frontend download: "+path.name,"stdout":"Project defaults verified; config permissions 0600; binary and SQLite template present.\nSHA256 "+receipt["archive_sha256"]}

    @keyword
    def upload_frontend_bundle_by_ssh(self, agent_id):
        task=self.pending[agent_id];q=shlex.quote
        target=task["remote"]+"/download.tar.gz"
        command="set -eu; umask 077; mkdir -m 700 "+q(task["remote"])+"; cat > "+q(target)+"; sha256sum "+q(target)
        result=self.control.ssh(task["agent"],command,task["file"].read_bytes(),timeout=100)
        digest=result.stdout.decode().strip().split()[0]
        assert digest==task["receipt"]["archive_sha256"],"SSH transfer hash differs"
        task["receipt"]["ssh_archive_sha256"]=digest
        return {"command":shlex.join(self.control.ssh_command(task["agent"],command))+" < "+task["file"].name+"\n\nRemote script:\n"+command,
            "stdout":"SSH upload complete. Remote SHA256 "+digest+"\nOriginal frontend archive transferred without modifying its configuration."}

    @keyword
    def install_uploaded_bundle(self, agent_id):
        task=self.pending[agent_id];agent=task["agent"];q=shlex.quote
        staging=q(task["remote"]);root=q(agent["root"]);installed=q(agent["root"]+"/Installer")
        script="""set -eu
umask 077
stage=STAGING
root=ROOTDIR
installed=INSTALLDIR
tar xzf "$stage/download.tar.gz" -C "$stage" Installer/default.conf Installer/sonar_validator_prober Installer/default_template.sqlite
chmod 600 "$stage/Installer/default.conf"
old=$(pidof sonar_validator_prober || pgrep -x sonar_validator || true)
set -- $old
[ "$#" -le 1 ] || { echo 'Multiple Probers found; update cancelled' >&2; exit 4; }
if [ "$#" -eq 1 ]; then
    old=$1
    [ "$(readlink /proc/$old/exe)" = "$installed/sonar_validator_prober" ] || exit 5
    kill -TERM "$old"
    i=0
    while [ -e /proc/$old/exe ] && [ "$i" -lt 30 ]; do sleep 1; i=$((i+1)); done
    [ ! -e /proc/$old/exe ] || { echo 'Prober did not stop' >&2; exit 6; }
fi
mkdir -p "$installed"
cp "$stage/Installer/sonar_validator_prober" "$installed/sonar_validator_prober.next"
cp "$stage/Installer/default.conf" "$installed/default.conf.next"
cp "$stage/Installer/default_template.sqlite" "$installed/default_template.sqlite.next"
chmod 755 "$installed/sonar_validator_prober.next"
chmod 600 "$installed/default.conf.next"
chmod 644 "$installed/default_template.sqlite.next"
mv "$installed/sonar_validator_prober.next" "$installed/sonar_validator_prober"
mv "$installed/default.conf.next" "$installed/default.conf"
mv "$installed/default_template.sqlite.next" "$installed/default_template.sqlite"
sync
nohup /bin/sh "$root/start.sh" >> "$root/prober.log" 2>&1 </dev/null &
rm -f "$stage/download.tar.gz" "$stage/Installer/default.conf" "$stage/Installer/sonar_validator_prober" "$stage/Installer/default_template.sqlite"
rmdir "$stage/Installer" "$stage"
sync
echo 'SIGTERM completed; downloaded binary/config/template installed; existing startup hook reused.'
echo 'Existing telemetry database retained.'
""".replace("STAGING",staging).replace("ROOTDIR",root).replace("INSTALLDIR",installed)
        installation = self.control.ssh(agent,script,timeout=65)
        deadline=time.monotonic()+100
        while time.monotonic()<deadline:
            result=self.control.ssh(agent,"p=$(pidof sonar_validator_prober || pgrep -x sonar_validator || true); set -- $p; test \"$#\" -eq 1 || exit 1; sha256sum /proc/$1/exe; awk '/Threads:/ {print $2}' /proc/$1/status; pgrep -af '^sonar-terminal-job' || true",check=False)
            lines=result.stdout.decode().strip().splitlines()
            expected=self.control.api("/agents/expected")["agents"]
            online=any(a["agent_id"]==agent_id and a.get("connected") for a in expected)
            if result.returncode==0 and len(lines)==2 and lines[1]=="4" and online:
                assert lines[0].split()[0]==task["receipt"]["binary_sha256"]
                task["receipt"].update({"online":True,"threads":4,"terminal_jobs":0,"database_preserved":True,"startup_hook_preserved":True})
                task["completed"] = True
                self.receipts.append(task["receipt"])
                return {"command":shlex.join(self.control.ssh_command(agent,script))+"\n\nRemote script:\n"+script,
                    "stdout":installation.stdout.decode()+"\nRemote binary SHA256 "+lines[0].split()[0]+"\nProber online; one process, four threads, zero terminal jobs. Existing DB and boot hook retained."}
            time.sleep(2)
        raise AssertionError(agent_id+": Prober did not reconnect after installation")

    @keyword
    def save_deployment_receipts(self, output_file):
        path=Path(output_file);path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(self.receipts,ensure_ascii=False,indent=2))
        assert len(self.receipts)==len(self.site["agents"])

    @keyword
    def remove_private_downloads(self):
        for path in self.files:
            if path.is_file():path.unlink()

    @keyword
    def cleanup_remote_staging(self):
        for task in self.pending.values():
            q=shlex.quote
            base=task["remote"]
            assert base.startswith(task["agent"]["root"]+"/.frontend-deploy-")
            paths=[base+"/download.tar.gz"]+[base+"/Installer/"+name for name in ["default.conf","sonar_validator_prober","default_template.sqlite"]]
            command="if [ -d "+q(base)+" ]; then rm -f "+" ".join(q(p) for p in paths)+"; rmdir "+q(base+"/Installer")+" "+q(base)+"; fi"
            self.control.ssh(task["agent"],command,check=True)

    @keyword
    def reboot_deployed_lab(self):
        started=datetime.now(timezone.utc)
        for original in self.site["agents"]:
            agent=dict(original);agent["elevate"]=agent["profile"]=="eos"
            self.control.ssh(agent,"sync")
        for node in self.site["nodes"]:
            self.control.gns("/nodes/"+node+"/stop",{},"POST")
        for node in self.site["nodes"]:
            self.control.gns("/nodes/"+node+"/start",{},"POST")
        self.control.wait_ready(self.lab, timeout=600)
        for original in self.site["agents"]:
            agent=dict(original);agent["elevate"]=agent["profile"]=="eos"
            receipt=next(v for v in self.receipts if v["agent_id"]==agent["id"])
            paths=[agent["root"]+"/Installer/"+name for name in ["sonar_validator_prober","default.conf","default_template.sqlite"]]
            result=self.control.ssh(agent,"sha256sum "+" ".join(shlex.quote(path) for path in paths))
            values=[line.split()[0] for line in result.stdout.decode().splitlines()]
            assert values==[receipt[k] for k in ["binary_sha256","config_sha256","template_sha256"]],"Installed files did not persist after VM restart"
            receipt["files_survived_vm_restart"]=True
            deadline=time.monotonic()+120
            while time.monotonic()<deadline:
                value=self.control.api("/agents/"+agent["id"]+"/telemetry")
                stamp=datetime.fromisoformat(value.get("collected_at","1970-01-01T00:00:00Z").replace("Z","+00:00"))
                if stamp>=started:
                    receipt["post_reboot_collection"]=value["collected_at"]
                    break
                time.sleep(2)
            else: raise AssertionError(agent["id"]+": no new telemetry after reboot")
