#!/usr/bin/env python3
"""Simple send-and-capture telnet console driver (no expect matching).

Usage: python3 console_simple.py <host> <port> <cmds.txt> [gap] [tail]
cmds.txt: one command per line. Lines starting with '#' are comments.
A line '@<seconds>' means: just wait/collect for that many seconds.
"""
import socket
import sys
import time
import re

IAC, WILL, WONT, DO, DONT, SB, SE = 255, 251, 252, 253, 254, 250, 240
ANSI = re.compile(rb"\x1b(?:\[[0-9;?]*[ -/]*[@-~]|\][^\x07\x1b]*(?:\x07|\x1b\\)|[@-Z\\-_])")


def clean(data, sock):
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
                else:
                    resp += bytes([IAC, WONT, opt])
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
        sock.sendall(bytes(resp))
    return bytes(out)


def main():
    host, port = sys.argv[1], int(sys.argv[2])
    with open(sys.argv[3]) as f:
        lines = [l.rstrip("\n") for l in f if (l.strip() or l.strip() == ".") and not l.startswith("#")]
    lines = ["" if l == "." else l for l in lines]
    gap = float(sys.argv[4]) if len(sys.argv) > 4 else 2.5
    tail = float(sys.argv[5]) if len(sys.argv) > 5 else 3.0
    eol = {"cr": "\r", "lf": "\n", "crlf": "\r\n"}.get(sys.argv[6] if len(sys.argv) > 6 else "lf", "\n")

    s = socket.create_connection((host, port), timeout=10)
    log = bytearray()

    def pump(d):
        s.settimeout(d)
        try:
            c = s.recv(65536)
        except socket.timeout:
            return
        except OSError:
            return
        if c:
            log.extend(ANSI.sub(b"", clean(c, s)))

    pump(2.0)
    for line in lines:
        if line.startswith("@"):
            end = time.time() + float(line[1:])
            while time.time() < end:
                pump(0.5)
            continue
        time.sleep(gap)
        pump(0.3)
        s.sendall((line + eol).encode("latin-1", "replace"))
        pump(gap)
    end = time.time() + tail
    while time.time() < end:
        pump(0.5)
    sys.stdout.write(log.decode("latin-1"))
    sys.stdout.flush()


if __name__ == "__main__":
    main()
