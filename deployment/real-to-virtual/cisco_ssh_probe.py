#!/usr/bin/env python3
"""Cisco 8000v 로 SSH 접근 가능한 경로를 모두 탐색한다.

왜: 콘솔(텔넷) 스크래핑은 불안정합니다. SSH 로 들어갈 수 있으면 훨씬 안정적입니다.
    Cisco 문서상 guestshell SSH 는 키 인증만 지원하므로,
    (a) IOS SSH 계정, (b) guestshell SSH 노출 여부, (c) 포트 포워딩을 확인한다.
"""

import socket
import sys

TARGETS = [
    ("10.20.0.1", 22, "IOS (관리평면 Gi4)"),
    ("192.168.122.254", 22, "Gi1 (NAT 외부)"),
    ("192.168.122.254", 2222, "Gi1 +2222 (guestshell 포워딩?)"),
    ("192.168.122.254", 22222, "Gi1 +22222"),
    ("10.20.0.1", 2222, "IOS +2222"),
    ("10.20.0.1", 22222, "IOS +22222"),
]


def probe(host, port, label):
    try:
        s = socket.create_connection((host, port), timeout=4)
        s.settimeout(3)
        try:
            banner = s.recv(256).decode("utf-8", "ignore").strip()
        except Exception:
            banner = ""
        s.close()
        return f"  ✅ {host}:{port:<6} {label:32} OPEN  banner={banner[:60]!r}"
    except Exception as e:
        return f"  ❌ {host}:{port:<6} {label:32} {type(e).__name__}"


print("=== SSH 후보 포트 스캔 ===")
for host, port, label in TARGETS:
    print(probe(host, port, label))