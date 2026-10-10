"""Minimal pure-python telnet client with expect-style helpers.

Python 3.13+ removed telnetlib, and pexpect/expect are not installed on this
host, so this module implements just enough of RFC854 (IAC negotiation) plus
an expect()-style read loop to drive GNS3 telnet consoles (Linux shells, IOS,
IOSvL2, docker consoles).
"""
import re
import socket
import time

IAC = 255
DONT = 254
DO = 253
WONT = 252
WILL = 251
SB = 250
SE = 240

_ANSI = re.compile(r"\x1b\[[0-9;?]*[a-zA-Z]|\x1b\][^\x07]*\x07|\x1b[=>(][0-9A-Za-z]?")


class Telnet:
    def __init__(self, host="127.0.0.1", port=0, timeout=8):
        self.host = host
        self.port = port
        self.timeout = timeout
        self.sock = None
        self.buf = ""          # unconsumed decoded text
        self.transcript = ""   # everything consumed so far
        self._raw = b""        # leftover incomplete IAC sequence

    # ------------------------------------------------------------------ conn
    def connect(self):
        self.sock = socket.create_connection((self.host, self.port), timeout=self.timeout)
        self.sock.setblocking(False)
        return self

    def close(self):
        try:
            self.sock.close()
        except Exception:
            pass

    # -------------------------------------------------------------- protocol
    def _feed(self, data: bytes) -> bytes:
        """Strip/handle IAC sequences, return printable payload bytes."""
        i = 0
        out = bytearray()
        n = len(data)
        while i < n:
            c = data[i]
            if c != IAC:
                out.append(c)
                i += 1
                continue
            if i + 1 >= n:
                self._raw = bytes(data[i:])
                return bytes(out)
            cmd = data[i + 1]
            if cmd == IAC:
                out.append(IAC)
                i += 2
                continue
            if cmd in (DO, DONT, WILL, WONT):
                if i + 2 >= n:
                    self._raw = bytes(data[i:])
                    return bytes(out)
                opt = data[i + 2]
                reply = WONT if cmd in (DO, DONT) else DONT
                try:
                    self.sock.sendall(bytes([IAC, reply, opt]))
                except Exception:
                    pass
                i += 3
                continue
            if cmd == SB:
                j = i + 2
                while j + 1 < n and not (data[j] == IAC and data[j + 1] == SE):
                    j += 1
                if j + 1 >= n:
                    self._raw = bytes(data[i:])
                    return bytes(out)
                i = j + 2
                continue
            i += 2  # one-byte command (NOP, GA, ...)
        return bytes(out)

    def _push(self, data: bytes):
        clean = self._feed(self._raw + data)
        self._raw = b""
        text = clean.decode("latin-1")
        text = text.replace("\x00", "")
        text = _ANSI.sub("", text)
        text = text.replace("\r\n", "\n").replace("\r", "\n")
        self.buf += text

    def _recv(self):
        try:
            data = self.sock.recv(65536)
        except (BlockingIOError, socket.timeout):
            return b""
        if not data:
            raise EOFError("console connection closed")
        return data

    # ----------------------------------------------------------------- expect
    def expect(self, patterns, timeout=None):
        """Wait until one of patterns matches. Returns (idx, match, text)."""
        if isinstance(patterns, (str, bytes)):
            patterns = [patterns]
        rx = [p if isinstance(p, re.Pattern) else re.compile(p, re.S) for p in patterns]
        timeout = self.timeout if timeout is None else timeout
        end = time.time() + timeout
        while True:
            for idx, p in enumerate(rx):
                m = p.search(self.buf)
                if m:
                    text = self.buf[: m.end()]
                    self.buf = self.buf[m.end():]
                    self.transcript += text
                    return idx, m, text
            if time.time() > end:
                text = self.buf
                self.buf = ""
                self.transcript += text
                return -1, None, text
            try:
                data = self._recv()
            except EOFError:
                text = self.buf
                self.buf = ""
                self.transcript += text
                return -1, None, text
            if data:
                self._push(data)
            else:
                time.sleep(0.03)

    def wait(self, seconds):
        """Drain socket for `seconds`, appending to transcript."""
        end = time.time() + seconds
        while time.time() < end:
            try:
                data = self._recv()
            except EOFError:
                break
            if data:
                self._push(data)
            else:
                time.sleep(0.03)
        text = self.buf
        self.buf = ""
        self.transcript += text
        return text

    def send(self, line=""):
        self.sock.sendall((line + "\r").encode("latin-1", "replace"))
        self.transcript += f"\n>>> {line}\n"

    def sendline(self, line=""):
        self.send(line)
