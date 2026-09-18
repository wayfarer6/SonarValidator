#!/usr/bin/env python3
"""
경량 WebSocket 텔레메트리 수신기 (검증용)

왜 필요한가
  저장소의 mock 서버(Agent_Test/mock_API/server.js)는 Node.js 가 필요하지만
  검증 호스트에 Node 가 없고 시스템 패키지 설치도 여의치 않다.
  프로버가 실제로 서버에 텔레메트리를 보내는지 확인하려면
  WebSocket 핸드셰이크와 프레임 수신만 하면 되므로 표준 라이브러리로 충분하다.

하는 일
  - /api/v1/management, /api/v1/telemetry 두 경로로 WebSocket 업그레이드를 수락
  - 수신 텍스트 프레임을 파싱해 통계와 함께 출력
  - management 연결 시 `get_sysinfo` command 봉투를 전송 (mock 서버와 동일 동작)

사용법
  python3 ws_collector.py [포트] [수신시간(초)]
  기본: 포트 3000, 30초
"""

import base64
import hashlib
import json
import os
import re
import socket
import struct
import sys
import threading
import time
from collections import Counter

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
SUPPORTED_PATHS = {"/api/v1/management", "/api/v1/telemetry"}

received = []
lock = threading.Lock()


def handshake(conn, request: bytes):
    """WebSocket 업그레이드 요청을 처리한다. 실패하면 None."""
    text = request.decode("utf-8", "ignore")
    first_line = text.split("\r\n", 1)[0]
    parts = first_line.split()
    if len(parts) < 2:
        return None
    path = parts[1]

    match = re.search(r"Sec-WebSocket-Key:\s*(\S+)", text, re.IGNORECASE)
    if not match:
        return None
    key = match.group(1)

    if path not in SUPPORTED_PATHS:
        conn.sendall(b"HTTP/1.1 404 Not Found\r\n\r\n")
        return None

    accept = base64.b64encode(
        hashlib.sha1((key + WS_GUID).encode()).digest()).decode()
    response = (
        "HTTP/1.1 101 Switching Protocols\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Accept: {accept}\r\n\r\n"
    )
    conn.sendall(response.encode())
    return path


def send_text(conn, message: str):
    """서버 → 클라이언트 텍스트 프레임(마스킹 없음)."""
    payload = message.encode()
    header = bytearray([0x81])
    length = len(payload)
    if length < 126:
        header.append(length)
    elif length < 65536:
        header.append(126)
        header += struct.pack(">H", length)
    else:
        header.append(127)
        header += struct.pack(">Q", length)
    conn.sendall(bytes(header) + payload)


def read_exact(conn, count):
    buf = b""
    while len(buf) < count:
        chunk = conn.recv(count - len(buf))
        if not chunk:
            return None
        buf += chunk
    return buf


def read_frame(conn):
    """프레임 하나를 읽어 (opcode, payload) 를 돌려준다."""
    head = read_exact(conn, 2)
    if head is None:
        return None
    opcode = head[0] & 0x0F
    masked = (head[1] & 0x80) != 0
    length = head[1] & 0x7F

    if length == 126:
        ext = read_exact(conn, 2)
        if ext is None:
            return None
        length = struct.unpack(">H", ext)[0]
    elif length == 127:
        ext = read_exact(conn, 8)
        if ext is None:
            return None
        length = struct.unpack(">Q", ext)[0]

    mask = b""
    if masked:
        mask = read_exact(conn, 4)
        if mask is None:
            return None

    payload = read_exact(conn, length) if length else b""
    if payload is None:
        return None

    if masked:
        payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))

    return opcode, payload


def handle_client(conn, addr, path):
    try:
        # management 경로면 mock 서버와 동일하게 초기 명령을 보낸다.
        if path == "/api/v1/management":
            send_text(conn, json.dumps({
                "type": "command",
                "service": "management",
                "command": "get_sysinfo",
                "payload": {},
            }))

        while True:
            frame = read_frame(conn)
            if frame is None:
                break
            opcode, payload = frame

            if opcode == 0x8:  # close
                break
            if opcode == 0x9:  # ping -> pong
                conn.sendall(b"\x8a\x00")
                continue
            if opcode != 0x1:  # text 만 처리
                continue

            text = payload.decode("utf-8", "ignore")
            try:
                message = json.loads(text)
            except json.JSONDecodeError:
                with lock:
                    received.append(("invalid", text[:200]))
                continue

            with lock:
                received.append((path, message))

            print(f"[RECV {path}] type={message.get('type')} "
                  f"agent={message.get('agent_id')} "
                  f"device={message.get('device_type')}")

            # ack 를 돌려준다(mock 서버와 동일).
            if message.get("type") in ("hello", "policy-request", "command"):
                send_text(conn, json.dumps({
                    "type": "ack",
                    "agent_id": message.get("agent_id", ""),
                    "device_type": message.get("device_type", ""),
                    "correlation_id": message.get("correlation_id", ""),
                    "payload": {},
                }))
    except Exception as exc:
        print(f"[WARN] client error: {exc}", file=sys.stderr)
    finally:
        try:
            conn.close()
        except Exception:
            pass


def serve(port, duration):
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", port))
    server.listen(8)
    server.settimeout(0.5)
    print(f"[LISTEN] ws://0.0.0.0:{port} (경로: {', '.join(sorted(SUPPORTED_PATHS))})")
    print(f"[INFO] {duration}초 동안 수신합니다...")

    deadline = time.time() + duration
    threads = []
    while time.time() < deadline:
        try:
            conn, addr = server.accept()
        except socket.timeout:
            continue
        except OSError:
            break

        conn.settimeout(5.0)
        try:
            request = conn.recv(4096)
        except Exception:
            conn.close()
            continue

        path = handshake(conn, request)
        if path is None:
            conn.close()
            continue

        print(f"[CONNECT] {addr[0]}:{addr[1]} -> {path}")
        thread = threading.Thread(target=handle_client,
                                  args=(conn, addr, path), daemon=True)
        thread.start()
        threads.append(thread)

    server.close()

    # 결과 요약
    print("\n=========== 수신 요약 ===========")
    with lock:
        total = len(received)
        print(f"총 수신 메시지: {total}")

        types = Counter()
        paths = Counter()
        agents = Counter()
        payload_keys = Counter()
        for path, message in received:
            if path == "invalid":
                types["<invalid>"] += 1
                continue
            paths[path] += 1
            types[message.get("type", "?")] += 1
            agents[message.get("agent_id", "?")] += 1
            payload = message.get("payload")
            if isinstance(payload, dict):
                for key in payload.keys():
                    payload_keys[key] += 1

        if types:
            print("\n[type 분포]")
            for name, count in types.most_common():
                print(f"  {name:18s} {count}")
        if paths:
            print("\n[경로 분포]")
            for name, count in paths.most_common():
                print(f"  {name:24s} {count}")
        if agents:
            print("\n[agent 분포]")
            for name, count in agents.most_common():
                print(f"  {name:18s} {count}")
        if payload_keys:
            print("\n[telemetry payload 키]")
            for name, count in payload_keys.most_common():
                print(f"  {name:18s} {count}")

    # 성공 판정: 텔레메트리를 1건 이상 받았으면 OK
    ok = any(isinstance(m, dict) and m.get("type") == "telemetry"
             for _, m in received)
    print(f"\n[결과] {'텔레메트리 수신 성공' if ok else '텔레메트리 수신 실패'}")
    return 0 if ok else 1


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
    duration = int(sys.argv[2]) if len(sys.argv) > 2 else 30
    return serve(port, duration)


if __name__ == "__main__":
    sys.exit(main())
