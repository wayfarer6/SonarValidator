#!/usr/bin/env python3
"""Check telnet console reachability for all devices (read-only, no fixes)."""
import sys, os, socket
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from telnet_lib import Telnet
from probe import PORTS

MARK_CMD = "echo RDY_$(hostname)"
MARK_RE = r"RDY_[A-Za-z0-9._-]+"

# login kinds: ubuntu VMs need login, alpine routers may be at vtysh, containers root shell
KIND = {
    "Public-Web-Server": "ubuntu", "ATICS": "ubuntu", "KNCCS": "ubuntu", "AFCCS": "ubuntu",
    "Gateway-Router": "frr", "Survillance-Network-Router": "frr", "VDI-Router": "frr",
    "C4I-Network-Router": "frr", "DMZ-Router": "frr",
}


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
                return True
            t.send("ubuntu"); t.wait(0.8)
        return False
    if kind == "frr":
        for _ in range(4):
            t.send(MARK_CMD)
            i2, _, _ = t.expect([MARK_RE], timeout=5)
            tail = t.buf; t.buf = ""
            if i2 == 0 and "% Unknown" not in tail:
                return True
            t.send("exit"); t.wait(0.8)
        return False
    # containers: just probe for a shell prompt
    t.send(MARK_CMD)
    i2, _, _ = t.expect([MARK_RE], timeout=6)
    return i2 == 0


def main():
    names = sys.argv[1:] or sorted(PORTS, key=lambda n: PORTS[n])
    results = []
    for name in names:
        port = PORTS[name]
        # 1) TCP connect test
        try:
            s = socket.create_connection(("127.0.0.1", port), timeout=6)
            s.close()
            tcp = "OK"
        except Exception as e:
            results.append((name, port, f"TCP FAIL ({e})", ""))
            print(f"{name:30s} :{port}  TCP FAIL ({e})")
            continue
        # 2) console shell response test
        try:
            t = Telnet(port=port, timeout=6).connect()
            ok = login(t, KIND.get(name, "docker"))
            t.close()
            status = "SHELL OK" if ok else "SHELL NO-MARKER"
        except Exception as e:
            status = f"SHELL FAIL ({e})"
        results.append((name, port, tcp, status))
        print(f"{name:30s} :{port}  TCP {tcp}  {status}")

    print("\n===== SUMMARY =====")
    bad = [r for r in results if r[2] != "OK" or r[3] != "SHELL OK"]
    print(f"total={len(results)}  ok={len(results)-len(bad)}  problem={len(bad)}")
    for r in bad:
        print("  PROBLEM:", r)


if __name__ == "__main__":
    main()
