#!/usr/bin/env python3
"""GNS3 console helper - sends commands over a telnet console and returns output.

Run this on the GNS3 host (127.0.0.1 consoles are local there).
It performs minimal telnet negotiation (refuse every option) and then
sends a command, returning the collected output.

usage:
    python3 gns3_console.py PORT [-t SECONDS] [-c "cmd1" -c "cmd2" ...]
    python3 gns3_console.py PORT --read-only          # just dump the banner
"""

import argparse
import socket
import sys
import time

IAC = 255
DONT = 254
DO = 253
WONT = 252
WILL = 251
SB = 250
SE = 240


def negotiate(sock):
    """Read whatever is pending and refuse all telnet options."""
    sock.settimeout(0.4)
    deadline = time.time() + 1.2
    data = b""
    while time.time() < deadline:
        try:
            chunk = sock.recv(4096)
        except socket.timeout:
            break
        except OSError:
            break
        if not chunk:
            break
        data += chunk
        # Reply to every IAC DO/WILL with the matching refusal.
        i = 0
        reply = b""
        while i < len(chunk):
            if chunk[i] == IAC and i + 2 < len(chunk):
                cmd = chunk[i + 1]
                opt = chunk[i + 2]
                if cmd == DO:
                    reply += bytes([IAC, WONT, opt])
                elif cmd == WILL:
                    reply += bytes([IAC, DONT, opt])
                i += 3
            else:
                i += 1
        if reply:
            try:
                sock.sendall(reply)
            except OSError:
                break
    return data


def drain(sock, idle=0.7, limit=20.0):
    """Read until the console goes idle for `idle` seconds."""
    sock.settimeout(idle)
    buf = b""
    start = time.time()
    while time.time() - start < limit:
        try:
            chunk = sock.recv(65535)
        except socket.timeout:
            break
        except OSError:
            break
        if not chunk:
            break
        buf += chunk
    return buf


def clean(raw):
    """Strip telnet IAC sequences and normalize CRLF."""
    out = bytearray()
    i = 0
    while i < len(raw):
        if raw[i] == IAC:
            if i + 1 < len(raw) and raw[i + 1] == SB:
                j = raw.find(bytes([IAC, SE]), i)
                i = len(raw) if j < 0 else j + 2
                continue
            i += 3
            continue
        out.append(raw[i])
        i += 1
    return out.decode("utf-8", "replace").replace("\r\n", "\n").replace("\r", "\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("port", type=int)
    ap.add_argument("-c", "--command", action="append", default=[])
    ap.add_argument("-t", "--wait", type=float, default=2.0)
    ap.add_argument("--read-only", action="store_true")
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()

    try:
        sock = socket.create_connection(("127.0.0.1", args.port), timeout=6)
    except OSError as exc:
        print(f"CONNECT_FAIL {args.port}: {exc}")
        return 2

    banner = clean(negotiate(sock))

    if args.read_only:
        if not args.quiet:
            print(banner, end="")
        sock.close()
        return 0

    # Wake the console / get a fresh prompt.
    try:
        sock.sendall(b"\n")
    except OSError:
        pass
    banner += clean(drain(sock, idle=0.5, limit=4))

    if not args.quiet:
        print("=== console banner ===")
        print(banner, end="" if banner.endswith("\n") else "\n")

    for command in args.command:
        try:
            sock.sendall(command.encode() + b"\n")
        except OSError as exc:
            print(f"SEND_FAIL: {exc}")
            break
        print(f"\n=== $ {command} ===")
        print(clean(drain(sock, idle=args.wait, limit=args.wait * 8 + 15)), end="")

    sock.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
