#!/usr/bin/env python3
"""Verification: ping tests across zones + OSPF neighbor check."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from telnet_lib import Telnet
from plan import PORTS, LOGIN

MARK_CMD = "echo RDY_$(hostname)"
MARK_RE = r"RDY_[A-Za-z0-9._-]+"

TESTS = [
    ("Gateway-Router", "frr", [
        "ping -c 2 -W 2 10.99.10.3", "ping -c 2 -W 2 10.99.10.4",
        "ping -c 2 -W 2 10.99.10.5", "ping -c 2 -W 2 10.99.10.6",
        "ping -c 2 -W 2 10.99.143.2", "ping -c 2 -W 2 192.168.122.1",
        "vtysh -c 'show ip ospf neighbor'",
    ]),
    ("AFCCS", "ubuntu", [
        "ping -c 2 -W 2 10.10.133.1", "ping -c 2 -W 2 10.10.132.10",
        "ping -c 2 -W 2 10.10.131.10", "ping -c 2 -W 2 10.99.10.1",
        "ping -c 2 -W 2 10.30.141.10", "ping -c 2 -W 2 10.40.121.10",
    ]),
    ("TOD-Cam", "docker", [
        "ping -c 2 -W 2 10.20.111.1", "ping -c 2 -W 2 10.20.112.10",
        "ping -c 2 -W 2 10.10.133.10", "ping -c 2 -W 2 10.30.141.10",
    ]),
    ("VDI-1", "docker", [
        "ping -c 2 -W 2 10.40.121.1", "ping -c 2 -W 2 10.40.122.10",
        "ping -c 2 -W 2 10.10.133.10", "ping -c 2 -W 2 10.20.111.10",
    ]),
    ("Public-Web-Server", "ubuntu", [
        "ping -c 2 -W 2 10.30.141.1", "ping -c 2 -W 2 10.99.10.1",
        "ping -c 2 -W 2 192.168.122.1", "ping -c 2 -W 2 10.10.133.10",
    ]),
]


def login(t, kind):
    t.wait(1.0)
    if kind == "ubuntu":
        for _ in range(10):
            idx, _, _ = t.expect(
                [r"login:\s*$", r"[Pp]assword:\s*$", r"[\$#]\s*$"], timeout=4)
            if idx == 0:
                t.send("ubuntu"); t.wait(0.8); continue
            if idx == 1:
                t.send("ubuntu"); t.wait(1.0); continue
            if idx == 2:
                break
            t.send("ubuntu"); t.wait(0.8)
        t.buf = ""
    elif kind == "frr":
        for _ in range(4):
            t.send("echo RDY_$(hostname)")
            i2, _, _ = t.expect([MARK_RE], timeout=5)
            tail = t.buf; t.buf = ""
            if i2 == 0 and "% Unknown" not in tail:
                return
            t.send("exit"); t.wait(0.8)
    else:
        t.send(""); t.wait(0.8)


for dev, kind, cmds in TESTS:
    t = Telnet(port=PORTS[dev], timeout=8).connect()
    login(t, kind)
    for c in cmds:
        t.send(c + " ; " + MARK_CMD)
        t.expect([MARK_RE], timeout=20)
    print(f"===== {dev} =====")
    print(t.transcript[t.transcript.find(">>> " + cmds[0]):][:4000])
    t.close()
