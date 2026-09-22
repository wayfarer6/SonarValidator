#!/usr/bin/env python3
"""Recon: dump link/interface info for all devices.

Usage: recon.py [device ...]   (default: all)
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from telnet_lib import Telnet

PORTS = {
    "Public-Web-Server": 5000, "Survillance-Network-Router": 5013,
    "Gateway-Router": 5015, "VDI-Router": 5017, "C4I-Network-Router": 5019,
    "ATICS": 5022, "KNCCS": 5024, "AFCCS": 5026, "TOD-Cam": 5028, "UAV": 5030,
    "VDI-1": 5032, "VDI-2": 5034, "Firewall": 5037, "DMZ-Router": 5040,
    "Switch-0": 5042, "Switch-1": 5044, "Switch-2": 5046, "Switch-3": 5048,
    "Switch-4": 5050,
}
LOGIN = {"Public-Web-Server": ("ubuntu", "ubuntu"), "ATICS": ("ubuntu", "ubuntu"),
         "KNCCS": ("ubuntu", "ubuntu"), "AFCCS": ("ubuntu", "ubuntu")}
UBUNTU = set(LOGIN)
ROUTERS = {"Gateway-Router", "Survillance-Network-Router", "VDI-Router",
           "C4I-Network-Router", "DMZ-Router"}
SWITCHES = {"Switch-0", "Switch-1", "Switch-2", "Switch-3", "Switch-4"}

# The console echoes typed input, so a literal marker would match its own echo.
# Use shell expansion: the echoed line contains RDY_$(hostname) (no match for
# the regex), while the executed output contains RDY_<name> (match).
MARK_CMD = "echo RDY_$(hostname)"
MARK_RE = r"RDY_[A-Za-z0-9._-]+"

def body(name):
    if name in UBUNTU:
        return "echo ubuntu | sudo -S -k ip -br link 2>/dev/null"
    if name in ROUTERS:
        return ("ip -br link; echo ---VLANTEST---; "
                "ip link add link eth0 name eth0.901 type vlan id 901 2>&1 && echo VLAN_OK && ip link del eth0.901")
    if name in SWITCHES:
        return "ip -br link; echo ---OVS---; ovs-vsctl show 2>&1 | head -24"
    if name == "Firewall":
        return ("ip link; echo ---TOOLS---; ls /usr/sbin | grep -E 'nft|iptables|arptables' ; "
                "echo ---SYSCTL---; sysctl net.ipv4.ip_forward")
    return "ip -br link"

def run(name):
    t = Telnet(port=PORTS[name], timeout=6).connect()
    t.wait(1.0)
    if name in UBUNTU:
        t.send(""); t.wait(0.8)  # wake getty so login: prompt appears
        idx, _, _ = t.expect([r"login:\s*$", r"\$\s*$", r"#\s*$"], timeout=5)
        if idx == 0:
            t.send(LOGIN[name][0]); t.wait(1.0)
            i2, _, _ = t.expect([r"[Pp]assword:\s*$", r"\$\s*$"], timeout=5)
            if i2 == 0:
                t.send(LOGIN[name][1]); t.wait(1.2)
    else:
        t.send(""); t.wait(1.5)
        if name in ROUTERS:
            t.send("exit"); t.wait(1.0)   # leave vtysh
    cmd = body(name)
    t.send(cmd + " ; " + MARK_CMD)
    idx, _, _ = t.expect([MARK_RE], timeout=10)
    out = t.transcript
    seg = out[out.rfind(">>> " + cmd):] if (">>> " + cmd) in out else out[-2000:]
    print(f"===== {name} =====")
    print(seg[:3000])
    t.close()

if __name__ == "__main__":
    for n in (sys.argv[1:] or list(PORTS)):
        run(n)
