#!/usr/bin/env python3
"""Write boot-persistent network config to /etc and apply it at runtime.

Why: runtime-only `ip addr/ip link` commands vanish on reboot. Every device
type here has a boot hook that reads files under /etc:

  * Router VMs (Alpine + OpenRC): `networking` service runs `ifup -a` on
    /etc/network/interfaces (ifupdown-ng); `sysctl` service reads
    /etc/sysctl.conf; `frr` service reads /etc/frr/frr.conf (write memory).
  * Docker switches (openvswitch-container): OVS db lives in
    /etc/openvswitch (inside the persistent /etc volume) so br0/ports
    survive; /etc/network/interfaces brings br0 UP at boot (busybox ifup
    is invoked by the GNS3 entrypoint: `ifup -a -f`).
  * Docker firewall (alpine, persistent volume = /etc/network only):
    everything (addrs, VLANs, default route, ip_forward) expressed in
    /etc/network/interfaces so it survives with the volume.
  * Docker hosts (gns3/ubuntu, persistent /etc): static stanza in
    /etc/network/interfaces, applied by entrypoint `ifup -a -f`.
  * Ubuntu VMs: netplan file on disk (handled by plan.py/apply.py).

Usage: persist.py <device> | --all | --list
"""
import sys, os, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from telnet_lib import Telnet
from plan import PORTS
import apply as apply_mod

MARK_CMD = apply_mod.MARK_CMD
MARK_RE = apply_mod.MARK_RE
HERE = os.path.dirname(os.path.abspath(__file__))
LOGDIR = os.path.join(HERE, "..", "logs")

LO = ["auto lo", "iface lo inet loopback"]


def static(iface, addr, gw=None, extra=()):
    lines = [f"auto {iface}", f"iface {iface} inet static", f"\taddress {addr}"]
    if gw:
        lines.append(f"\tgateway {gw}")
    lines += [f"\tup {e}" for e in extra]
    return lines


def manual(iface, extra=()):
    lines = [f"auto {iface}", f"iface {iface} inet manual"]
    lines += [f"\tup {e}" for e in extra]
    return lines


def vlan(parent, vid, addr):
    ifn = f"{parent}.{vid}"
    return [f"auto {ifn}", f"iface {ifn} inet static", f"\taddress {addr}",
            f"\tvlan-raw-device {parent}"]


# ---- busybox ifupdown flavor (GNS3 containers): no CIDR, no vlan stanzas
def cidr_to_mask(cidr):
    bits = int(cidr.split("/")[1])
    m = (0xffffffff << (32 - bits)) & 0xffffffff
    return ".".join(str((m >> (8 * i)) & 0xff) for i in (3, 2, 1, 0))


def static_bb(iface, addr_cidr, gw=None, extra=()):
    addr = addr_cidr.split("/")[0]
    lines = [f"auto {iface}", f"iface {iface} inet static",
             f"\taddress {addr}", f"\tnetmask {cidr_to_mask(addr_cidr)}"]
    if gw:
        lines.append(f"\tgateway {gw}")
    lines += [f"\tup {e}" for e in extra]
    return lines


def vlan_bb(parent, vid, addr_cidr):
    ifn = f"{parent}.{vid}"
    return [f"auto {ifn}", f"iface {ifn} inet manual",
            f"\tup ip link add link {parent} name {ifn} type vlan id {vid} "
            "2>/dev/null || true",
            f"\tup ip link set {ifn} up",
            f"\tup ip addr add {addr_cidr} dev {ifn}"]


MASQ = ("iptables -t nat -C POSTROUTING -o eth0 -j MASQUERADE 2>/dev/null "
        "|| iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE")
IPFWD = "sysctl -w net.ipv4.ip_forward=1"

FILES = {}   # device -> {path: [lines]}
POST = {}    # device -> [runtime commands after files written]

# ------------------------------------------------------------- routers (VM)
FILES["Gateway-Router"] = {"/etc/network/interfaces": LO + [
    ""] + static("eth0", "192.168.122.10/24", gw="192.168.122.1", extra=[MASQ]) + [
    ""] + static("eth1", "10.99.10.1/24") + [
    ""] + static("eth7", "172.16.255.1/24")}

FILES["Survillance-Network-Router"] = {"/etc/network/interfaces": LO + [
    ""] + manual("eth0") + [
    ""] + vlan("eth0", 111, "10.20.111.1/24") + [
    ""] + vlan("eth0", 112, "10.20.112.1/24") + [
    ""] + static("eth1", "10.99.10.5/24") + [
    ""] + static("eth2", "172.16.255.5/24")}

FILES["VDI-Router"] = {"/etc/network/interfaces": LO + [
    ""] + static("eth0", "10.99.10.6/24") + [
    ""] + manual("eth1") + [
    ""] + vlan("eth1", 121, "10.40.121.1/24") + [
    ""] + vlan("eth1", 122, "10.40.122.1/24") + [
    ""] + static("eth2", "172.16.255.6/24")}

FILES["C4I-Network-Router"] = {"/etc/network/interfaces": LO + [
    ""] + static("eth0", "10.99.10.4/24") + [
    ""] + static("eth1", "10.99.143.1/24") + [
    ""] + static("eth7", "172.16.255.4/24")}

FILES["DMZ-Router"] = {"/etc/network/interfaces": LO + [
    ""] + static("eth0", "10.99.10.3/24") + [
    ""] + manual("eth1") + [
    ""] + vlan("eth1", 141, "10.30.141.1/24") + [
    ""] + static("eth7", "172.16.255.3/24")}

for r, lo_ip in [("Gateway-Router", "10.255.255.1"),
                 ("Survillance-Network-Router", "10.255.255.5"),
                 ("VDI-Router", "10.255.255.6"),
                 ("C4I-Network-Router", "10.255.255.4"),
                 ("DMZ-Router", "10.255.255.3")]:
    FILES[r]["/etc/network/interfaces"] += [
        "", f"auto lo:ospf", f"iface lo:ospf inet static",
        f"\taddress {lo_ip}/32"]
    POST.setdefault(r, []).append(f"ip addr replace {lo_ip}/32 dev lo")

# ------------------------------------------------------------- switches
# Containers: no lo stanza (GNS3 init.sh already runs `ip link set lo up`).
# Boot order hazard: init.sh runs `ifup -a -f` BEFORE the container CMD
# (start.sh) starts ovsdb-server/ovs-vswitchd, so br0 does not exist yet when
# the up-hook fires. The up-hook therefore spawns a background retry script
# (also stored in the persistent /etc) that brings br0 up once OVS creates it.
BR0_WAIT = [
    "#!/bin/sh",
    "# wait for ovs-vswitchd to (re)create br0 after boot, then bring it up",
    "i=0",
    "while [ \"$i\" -lt 90 ]; do",
    "    if ip link set br0 up 2>/dev/null; then",
    "        exit 0",
    "    fi",
    "    i=$((i+1))",
    "    sleep 1",
    "done",
    "exit 1",
]
for sw in ["Switch-0", "Switch-1", "Switch-2", "Switch-3", "Switch-4"]:
    FILES[sw] = {
        "/etc/network/interfaces": [
            "auto br0", "iface br0 inet manual",
            "\tup /etc/network/br0-wait-up.sh >/dev/null 2>&1 &"],
        "/etc/network/br0-wait-up.sh": BR0_WAIT,
    }
    POST.setdefault(sw, []).append("chmod +x /etc/network/br0-wait-up.sh")
    POST[sw].append("ip link set br0 up")

# ------------------------------------------------------------- firewall
FILES["Firewall"] = {"/etc/network/interfaces": [
    ""] + static_bb("eth0", "10.99.143.2/24", gw="10.99.143.1", extra=[IPFWD]) + [
    ""] + static_bb("eth4", "172.16.255.2/24") + [
    ""] + manual("eth1") + [
    ""] + vlan_bb("eth1", 131, "10.10.131.1/24") + [
    ""] + vlan_bb("eth1", 132, "10.10.132.1/24") + [
    ""] + vlan_bb("eth1", 133, "10.10.133.1/24")}
POST["Firewall"] = [IPFWD]

# ------------------------------------------------------------- docker hosts
FILES["TOD-Cam"] = {"/etc/network/interfaces": [
    ""] + static_bb("eth0", "10.20.111.10/24", gw="10.20.111.1")}
FILES["UAV"] = {"/etc/network/interfaces": [
    ""] + static_bb("eth0", "10.20.112.10/24", gw="10.20.112.1")}
FILES["VDI-1"] = {"/etc/network/interfaces": [
    ""] + static_bb("eth0", "10.40.121.10/24", gw="10.40.121.1")}
FILES["VDI-2"] = {"/etc/network/interfaces": [
    ""] + static_bb("eth0", "10.40.122.10/24", gw="10.40.122.1")}

ROUTERS = {"Gateway-Router", "Survillance-Network-Router", "VDI-Router",
           "C4I-Network-Router", "DMZ-Router"}
SWITCHES = {"Switch-0", "Switch-1", "Switch-2", "Switch-3", "Switch-4"}


def send_block(t, lines):
    for ln in lines:
        t.send(ln)
        t.wait(0.25)
    t.send(MARK_CMD)
    if t.expect([MARK_RE], timeout=15)[0] == -1:
        print(f"!! timeout after block line: {lines[-1]}")


def write_file(t, path, content):
    """Write a file over the console without heredocs.

    The telnet console swallows TABs (completion) and mangles heredoc
    prompts, so ship the payload base64-encoded in short `echo | base64 -d`
    chunks (144 raw bytes = 192 b64 chars each, so every chunk decodes
    standalone) and verify with md5sum.
    """
    import base64, hashlib
    raw = ("\n".join(content) + "\n").encode()
    want = hashlib.md5(raw).hexdigest()
    for attempt in range(2):
        chunks = [raw[i:i + 144] for i in range(0, len(raw), 144)] or [b""]
        for n, ch in enumerate(chunks):
            op = ">" if n == 0 else ">>"
            b64 = base64.b64encode(ch).decode()
            t.send(f"echo '{b64}' | base64 -d {op} {path} ; " + MARK_CMD)
            if t.expect([MARK_RE], timeout=10)[0] == -1:
                print(f"!! timeout writing chunk {n} of {path}")
        t.send(f"md5sum {path} ; " + MARK_CMD)
        idx, _, _ = t.expect([MARK_RE], timeout=10)
        got = ""
        m = re.search(r"([0-9a-f]{32})\s", t.transcript[t.transcript.rfind(">>> md5sum"):])
        if m:
            got = m.group(1)
        if got == want:
            return True
        print(f"!! {path}: md5 mismatch (attempt {attempt+1}), retrying")
    return False


def persist_device(name):
    t = Telnet(port=PORTS[name], timeout=8).connect()
    apply_mod.login(t, name)
    for path, content in FILES[name].items():
        if not write_file(t, path, content):
            print(f"!! {name}: failed to write {path}")
    if name in ROUTERS:
        t.send("grep -q '^net.ipv4.ip_forward' /etc/sysctl.conf || "
               "echo 'net.ipv4.ip_forward=1' >> /etc/sysctl.conf ; " + MARK_CMD)
        t.expect([MARK_RE], timeout=10)
        t.send("sysctl -w net.ipv4.ip_forward=1 ; " + MARK_CMD)
        t.expect([MARK_RE], timeout=10)
    # runtime apply through the SAME hook the boot uses (ifup -a).
    # Flush addresses added by earlier runtime-only `ip` commands first so
    # ifupdown does not trip over duplicates.
    ifaces = []
    for content in FILES[name].values():
        for ln in content:
            if ln.startswith("auto ") and not ln.split()[1].startswith("lo"):
                ifaces.append(ln.split()[1])
    for ifn in ifaces:
        t.send(f"ip addr flush dev {ifn} 2>/dev/null ; " + MARK_CMD)
        t.expect([MARK_RE], timeout=10)
    ifup = "/tmp/gns3/bin/ifup" if name not in ROUTERS else "ifup"
    send_block(t, [f"{ifup} -a 2>&1 | tail -5"])
    for c in POST.get(name, []):
        t.send(c + " ; " + MARK_CMD)
        t.expect([MARK_RE], timeout=10)
    t.send("ip -br addr ; " + MARK_CMD)
    t.expect([MARK_RE], timeout=10)
    os.makedirs(LOGDIR, exist_ok=True)
    with open(os.path.join(LOGDIR, name + ".persist.log"), "w") as f:
        f.write(t.transcript)
    t.close()
    print(f"== {name}: persisted {list(FILES[name])} and applied")


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args or args == ["--list"]:
        print("\n".join(FILES))
    elif args == ["--all"]:
        for n in FILES:
            persist_device(n)
    else:
        for n in args:
            persist_device(n)
