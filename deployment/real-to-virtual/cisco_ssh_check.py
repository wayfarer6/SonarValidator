#!/usr/bin/env python3
"""Cisco 8000v 에 SSH 키 인증으로 접속을 시도하고 결과를 확인합니다.

`ip ssh pubkey-chain` 에 등록한 공개키가 실제로 동작하는지 검증합니다.
"""

import sys

import pexpect

ANSI_CMD = (
    "ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "
    "-o PreferredAuthentications=publickey -o ConnectTimeout=8 "
    "-i {key} {user}@{host}"
)

USERS = ["cisco"]
HOST = "10.20.0.1"
KEY = "/home/osboxes/.ssh/id_ed25519"


def try_login(user):
    cmd = ANSI_CMD.format(key=KEY, user=user, host=HOST)
    c = pexpect.spawn(cmd, encoding="utf-8", timeout=25)
    i = c.expect([r"(?i)password:", r"(?i)denied", r"[A-Za-z0-9_.-]+[#>]",
                  pexpect.TIMEOUT, pexpect.EOF])
    label = ["password 요구", "거부", "✅ SSH 프롬프트 획득", "타임아웃", "EOF"][i]
    print(f"  {user}@{HOST} → {label}")

    if i == 2:
        c.sendline("terminal length 0")
        c.expect([r"[#>]", pexpect.TIMEOUT], timeout=10)
        c.sendline("show app-hosting list")
        c.expect([r"[#>]", pexpect.TIMEOUT], timeout=15)
        for line in (c.before or "").split("\n"):
            if "guestshell" in line:
                print("    guestshell 상태:", line.strip()[:80])
                break
    c.close()
    return i


if __name__ == "__main__":
    for u in USERS:
        try_login(u)