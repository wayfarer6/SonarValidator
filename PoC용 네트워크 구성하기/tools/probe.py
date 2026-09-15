#!/usr/bin/env python3
"""Probe a console: connect, send a couple of harmless commands, dump output."""
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

def main():
    name = sys.argv[1]
    cmds = sys.argv[2:] or [""]
    t = Telnet(port=PORTS[name], timeout=6).connect()
    t.wait(1.5)
    for c in cmds:
        t.send(c)
        t.wait(1.5)
    print(f"===== {name} (:{PORTS[name]}) =====")
    print(t.transcript[-6000:])
    t.close()

if __name__ == "__main__":
    main()
