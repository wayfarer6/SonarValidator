#!/usr/bin/env python3
"""Cisco 8000v 의 관리망 경로 관련 설정을 덤프합니다.

Gi4(10.20.0.1) 가 관리망에 붙어 있는데도 guestshell 이 관리망에
도달하지 못하는 이유를 확정하기 위한 조회입니다.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cisco_console import Console, enter_enable, enter_guestshell, ios_cmd, gs_cmd, resolve_password  # noqa: E402

COMMANDS = [
    ("show ip route", 8),
    ("show running-config interface GigabitEthernet4", 6),
    ("show running-config interface VirtualPortGroup0", 6),
    ("show running-config interface GigabitEthernet1", 6),
    ("show running-config | include ip nat", 5),
    ("show running-config | include ip route", 5),
    ("show ip nat statistics", 8),
    ("show platform software interface F0 brief", 8),
]


def main():
    password = resolve_password()
    if not password:
        print("비밀번호 없음 (~/.sonar_cisco_pw)", file=sys.stderr)
        return 2
    console = Console(int(os.environ.get("SONAR_CISCO_CONSOLE", "5018")))
    try:
        enter_enable(console, password)
        for cmd, wait in COMMANDS:
            print(f"\n===== {cmd} =====")
            out = ios_cmd(console, cmd, wait)
            # 프롬프트/에코 제거
            out = re.sub(r"^Router[#>]\s*$", "", out, flags=re.M)
            print(out.strip()[:2000])

        print("\n===== guestshell 라우팅/도달성 =====")
        enter_guestshell(console)
        print(gs_cmd(console, "ip route 2>/dev/null || route -n 2>/dev/null || cat /proc/net/route", 6))
        print(gs_cmd(console, "ping -c 2 -W 2 10.20.0.1 2>&1 | tail -2", 8))
        print(gs_cmd(console, "ping -c 2 -W 2 10.20.0.3 2>&1 | tail -2", 8))
        console.sock.sendall(b"exit\r\n")
    finally:
        console.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())