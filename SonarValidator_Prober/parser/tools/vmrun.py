#!/usr/bin/env python3
"""Drive a GNS3 QEMU Ubuntu VM console: login, then run given shell lines.

Waits for a prompt after each line instead of a fixed sleep, which is far
more reliable on these consoles.

usage: vmrun.py <port> <user> <pw> "<line1>" "<line2>" ...
"""
import re, socket, sys, time
IAC,DONT,DO,WONT,WILL,SB,SE = 255,254,253,252,251,250,240
ANSI = re.compile(r"\x1b\[[0-9;?]*[a-zA-Z]|\x1b\][^\x07]*\x07")
PROMPT = re.compile(r"[$#]\s*$")

def conn(port):
    s=socket.create_connection(("127.0.0.1",port),timeout=8); s.settimeout(0.5)
    end=time.time()+2.0
    while time.time()<end:
        try: ch=s.recv(4096)
        except (socket.timeout,OSError): break
        if not ch: break
        rep=bytearray(); i=0
        while i<len(ch):
            if ch[i]==IAC and i+2<len(ch):
                c,o=ch[i+1],ch[i+2]
                if c==DO: rep+=bytes([IAC,WONT,o])
                elif c==WILL: rep+=bytes([IAC,DONT,o])
                i+=3
            else: i+=1
        if rep:
            try: s.sendall(bytes(rep))
            except OSError: break
    return s

def strip(raw):
    out=bytearray(); i=0
    while i<len(raw):
        if raw[i]==IAC:
            if i+1<len(raw) and raw[i+1]==SB:
                j=raw.find(bytes([IAC,SE]),i); i=len(raw) if j<0 else j+2; continue
            i+=3; continue
        out.append(raw[i]); i+=1
    return ANSI.sub("", out.decode("utf-8","replace").replace("\r\n","\n").replace("\r","\n"))

def collect(s, patterns, timeout=45.0, idle=1.5):
    buf=""; st=time.time(); last=time.time()
    while time.time()-st < timeout:
        s.settimeout(idle)
        try:
            ch=s.recv(65535)
            if not ch: break
            buf += strip(ch); last=time.time()
        except (socket.timeout,OSError):
            if buf and any(re.search(p, buf) for p in patterns) and time.time()-last>idle:
                break
            if not buf and time.time()-st > timeout/2:
                break
    return buf

def send(s, line, patterns=(r"[$#]\s*$",), timeout=45.0):
    s.sendall(line.encode()+b"\n")
    return collect(s, patterns, timeout=timeout)

port=int(sys.argv[1]); user=sys.argv[2]; pw=sys.argv[3]; lines=sys.argv[4:]
s=conn(port)
send(s, "", timeout=10)
b=send(s, "", timeout=10)
if "login:" in b:
    send(s, user, patterns=(r"[Pp]assword",), timeout=20)
    b=send(s, pw, timeout=30)
    print("[logged in]")
for ln in lines:
    out = send(s, ln, timeout=180)
    print(f"$ {ln}")
    for l in out.split("\n"):
        if l.strip() and ln.strip() not in l:
            print("  ", l)
s.close()
