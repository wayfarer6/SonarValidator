#!/usr/bin/env python3
"""Apply configuration to devices over telnet consoles.

Usage: apply.py <device> | --all | --list
Plan lives in plan.py: PLAN[device] = (kind, [items])
item = str                       -> shell command (marker appended)
item = ("block", [line, ...])    -> raw lines (heredoc), marker after block
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from telnet_lib import Telnet
from plan import PLAN, PORTS, LOGIN

HERE = os.path.dirname(os.path.abspath(__file__))
LOGDIR = os.path.join(HERE, "..", "logs")
os.makedirs(LOGDIR, exist_ok=True)
MARK_CMD = "echo RDY_$(hostname)"
MARK_RE = r"RDY_[A-Za-z0-9._-]+"


def login(t, name):
    """Get to a usable shell regardless of prior console state.

    Consoles persist VM state between telnet connections, so a device may be
    at a login prompt, a user shell, an alpine shell, or inside vtysh.
    """
    kind = PLAN[name][0]
    t.wait(1.0)
    if kind == "ubuntu":
        # Walk getty/sudo prompts until we hold a user shell. The console may
        # sit silently waiting for a username (prompt already consumed by a
        # previous session), so on timeout assume "waiting for username" and
        # send the username (harmless if we are actually in a shell).
        for _ in range(10):
            idx, _, _ = t.expect(
                [r"login:\s*$", r"[Pp]assword:\s*$", r"[\$#]\s*$"], timeout=4)
            if idx == 0:
                t.send(LOGIN[name][0]); t.wait(0.8); continue
            if idx == 1:
                t.send(LOGIN[name][1]); t.wait(1.0); continue
            if idx == 2:
                break
            t.send(LOGIN[name][0]); t.wait(0.8)   # silent username prompt
        t.send("echo ubuntu | sudo -S -k true"); t.wait(0.8)
        t.buf = ""
        return
    if kind == "frr":
        for _ in range(4):
            t.send("echo RDY_$(hostname)")
            i2, _, _ = t.expect([MARK_RE], timeout=5)
            tail = t.buf
            t.buf = ""
            if i2 == 0 and "% Unknown" not in tail:
                return
            t.send("exit"); t.wait(0.8)   # leave vtysh if we were inside it
        return
    t.send(""); t.wait(0.8)


def apply_device(name):
    kind, items = PLAN[name]
    t = Telnet(port=PORTS[name], timeout=8).connect()
    login(t, name)
    for item in items:
        if isinstance(item, tuple) and item[0] == "block":
            for ln in item[1]:
                t.send(ln)
                t.wait(0.25)
            t.send(MARK_CMD)
        else:
            t.send(item + " ; " + MARK_CMD)
        idx, _, _ = t.expect([MARK_RE], timeout=15)
        if idx == -1:
            print(f"!! {name}: timeout waiting after: {item if isinstance(item,str) else item[1][-1]}")
    log = os.path.join(LOGDIR, name + ".log")
    with open(log, "w") as f:
        f.write(t.transcript)
    t.close()
    print(f"== {name}: applied {len(items)} items -> {log}")


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args or args == ["--list"]:
        print("\n".join(PLAN))
    elif args == ["--all"]:
        for n in PLAN:
            apply_device(n)
    else:
        for n in args:
            apply_device(n)
