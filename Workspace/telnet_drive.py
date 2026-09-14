#!/usr/bin/env python3
"""Minimal expect-style telnet driver (pure stdlib).

Usage: python3 telnet_drive.py <host> <port> <script.json> [tail_seconds]

script.json: list of steps:
  {"expect": "<regex>", "send": "<text with \\n>", "timeout": 15, "optional": false}
After the last step, reads for tail_seconds (default 3) and dumps everything.
All received (IAC-stripped) output is printed to stdout.
"""
import json
import re
import socket
import sys
import time

ANSI = re.compile(rb"\x1b(?:\[[0-9;?]*[ -/]*[@-~]|\][^\x07\x1b]*(?:\x07|\x1b\\)|[@-Z\\-_])")

IAC, WILL, WONT, DO, DONT, SB, SE = 255, 251, 252, 253, 254, 250, 240


class Telnet:
    def __init__(self, host, port, timeout=10):
        self.s = socket.create_connection((host, port), timeout=timeout)
        self.buf = b""
        self.pos = 0
        self.log = bytearray()

    def _clean(self, data: bytes) -> bytes:
        out = bytearray()
        resp = bytearray()
        i = 0
        n = len(data)
        while i < n:
            c = data[i]
            if c == IAC:
                if i + 1 >= n:
                    break
                cmd = data[i + 1]
                if cmd in (WILL, WONT, DO, DONT):
                    if i + 2 >= n:
                        break
                    opt = data[i + 2]
                    if cmd == WILL:
                        resp += bytes([IAC, DONT, opt])
                    elif cmd == DO:
                        resp += bytes([IAC, WONT, opt])
                    elif cmd == DONT:
                        resp += bytes([IAC, WONT, opt])
                    else:
                        resp += bytes([IAC, DONT, opt])
                    i += 3
                elif cmd == SB:
                    j = data.find(bytes([IAC, SE]), i)
                    if j == -1:
                        break
                    i = j + 2
                elif cmd == IAC:
                    out.append(IAC)
                    i += 2
                else:
                    i += 2
            else:
                out.append(c)
                i += 1
        if resp:
            self.s.sendall(bytes(resp))
        return bytes(out)

    def pump(self, wait: float) -> None:
        self.s.settimeout(wait)
        try:
            chunk = self.s.recv(65536)
        except socket.timeout:
            return
        except OSError:
            return
        if not chunk:
            return
        clean = ANSI.sub(b"", self._clean(chunk))
        self.buf += clean
        self.log += clean

    def text(self) -> str:
        return self.buf.decode("latin-1")

    def send(self, s: str) -> None:
        self.s.sendall(s.encode("latin-1", "replace"))


def main() -> int:
    host, port = sys.argv[1], int(sys.argv[2])
    with open(sys.argv[3]) as f:
        steps = json.load(f)
    tail = float(sys.argv[4]) if len(sys.argv) > 4 else 3.0

    tn = Telnet(host, port)
    deadline_all = time.time() + 300
    for step in steps:
        pat = re.compile(step["expect"])
        timeout = step.get("timeout", 20)
        deadline = time.time() + timeout
        matched = False
        while time.time() < deadline and time.time() < deadline_all:
            tn.pump(0.5)
            m = pat.search(tn.text(), tn.pos)
            if m:
                tn.pos = m.end()
                matched = True
                break
        if not matched:
            if step.get("optional"):
                continue
            print("=== EXPECT TIMEOUT for pattern: %r ===" % step["expect"])
            print(tn.text()[-4000:])
            return 2
        time.sleep(step.get("gap", 0.7))
        tn.pump(0.3)
        send = step.get("send")
        if send is not None:
            tn.send(send.replace("\\n", "\n") if "\\n" in send else send)
        time.sleep(step.get("pause", 0.3))

    end = time.time() + tail
    while time.time() < end:
        tn.pump(0.5)
    sys.stdout.write(tn.text())
    sys.stdout.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main())
