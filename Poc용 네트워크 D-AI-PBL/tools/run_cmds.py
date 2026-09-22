#!/usr/bin/env python3
"""Run a list of commands on a console and dump full transcript.

Usage: run_cmds.py <device> <cmd1> ;; <cmd2> ;; ...
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

LOGIN = {  # (user, pass) for ubuntu VMs
    "Public-Web-Server": ("ubuntu", "ubuntu"),
    "ATICS": ("ubuntu", "ubuntu"), "KNCCS": ("ubuntu", "ubuntu"),
    "AFCCS": ("ubuntu", "ubuntu"),
}

def main():
    name = sys.argv[1]
    cmds = " ".join(sys.argv[2:]).split(";;")
    cmds = [c.strip() for c in cmds if c.strip()]
    t = Telnet(port=PORTS[name], timeout=6).connect()
    t.wait(1.2)
    if name in LOGIN:
        idx, _, _ = t.expect([r"login:\s*$", r"\$\s*$", r"#\s*$"], timeout=5)
        if idx == 0:
            t.send(LOGIN[name][0]); t.wait(1.2)
            idx2, _, _ = t.expect([r"[Pp]assword:\s*$", r"\$\s*$", r"#\s*$"], timeout=5)
            if idx2 == 0:
                t.send(LOGIN[name][1]); t.wait(1.5)
    else:
        t.send(""); t.wait(0.8)
    for c in cmds:
        t.send(c)
        t.wait(1.5)
    print(f"===== {name} =====")
    print(t.transcript[-12000:])
    t.close()

if __name__ == "__main__":
    main()
