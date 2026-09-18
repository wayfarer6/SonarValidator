#!/usr/bin/env python3
"""Run shell commands on a GNS3 FRR router console, escaping vtysh first.

FRR routers on this lab open the console directly inside vtysh, so shell
commands fail with "% Unknown command". This helper detects a vtysh prompt
and issues `exit` to reach the real shell before running anything.

usage:
    python3 frr_shell.py <port> -c "cmd" -c "cmd" ...
"""

import argparse
import re
import socket
import sys
import time

IAC, DONT, DO, WONT, WILL, SB, SE = 255, 254, 253, 252, 251, 250, 240

# `frr#` / `frr(config)#` = vtysh;  `frr:~#` / `~ #` = shell
VTYSH_PROMPT = re.compile(r"^[A-Za-z0-9_.:-]*(\([^)]*\))?#\s*$")
SHELL_PROMPT = re.compile(r"[~/:]\s*#\s*$")


def connect(port):
    sock = socket.create_connection(("127.0.0.1", port), timeout=8)
    sock.settimeout(0.4)
    deadline = time.time() + 1.5
    while time.time() < deadline:
        try:
            chunk = sock.recv(4096)
        except (socket.timeout, OSError):
            break
        if not chunk:
            break
        reply = bytearray()
        i = 0
        while i < len(chunk):
            if chunk[i] == IAC and i + 2 < len(chunk):
                cmd, opt = chunk[i + 1], chunk[i + 2]
                if cmd == DO:
                    reply += bytes([IAC, WONT, opt])
                elif cmd == WILL:
                    reply += bytes([IAC, DONT, opt])
                i += 3
            else:
                i += 1
        if reply:
            try:
                sock.sendall(bytes(reply))
            except OSError:
                break
    return sock


def read(sock, idle=1.0, limit=30.0):
    sock.settimeout(idle)
    buf = b""
    start = time.time()
    while time.time() - start < limit:
        try:
            chunk = sock.recv(65535)
        except (socket.timeout, OSError):
            break
        if not chunk:
            break
        buf += chunk
    return strip(buf)


def strip(raw):
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
    text = out.decode("utf-8", "replace").replace("\r\n", "\n").replace("\r", "\n")
    # drop terminal-capability replies such as ESC[30;8R
    return re.sub(r"\x1b\[[0-9;?]*[a-zA-Z]", "", text)


def clean_shell(text):
    """Remove echoed commands and prompts so only real output remains."""
    lines = text.split("\n")
    kept = []
    for line in lines:
        stripped = line.strip()
        if not stripped:
            continue
        if SHELL_PROMPT.search(stripped) and len(stripped) < 60:
            continue
        kept.append(line.rstrip())
    return "\n".join(kept)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("port", type=int)
    ap.add_argument("-c", "--command", action="append", default=[])
    ap.add_argument("--raw", action="store_true", help="print raw console output")
    args = ap.parse_args()

    try:
        sock = connect(args.port)
    except OSError as exc:
        print(f"CONNECT_FAIL {args.port}: {exc}")
        return 1

    read(sock, idle=0.6, limit=4)
    sock.sendall(b"\n")
    banner = read(sock, idle=0.8, limit=6)

    if args.raw:
        print("=== banner ===")
        print(banner, end="" if banner.endswith("\n") else "\n")

    # Escape vtysh if that is where the console landed.
    tail = [ln.strip() for ln in banner.strip().split("\n") if ln.strip()]
    last = tail[-1] if tail else ""
    if not SHELL_PROMPT.search(last):
        sock.sendall(b"exit\n")
        escaped = read(sock, idle=1.0, limit=8)
        if args.raw:
            print("=== after exit ===")
            print(escaped, end="" if escaped.endswith("\n") else "\n")

    for command in args.command:
        sock.sendall(command.encode() + b"\n")
        out = read(sock, idle=1.2, limit=40)
        print(f"=== $ {command} ===")
        print(clean_shell(out))

    sock.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
