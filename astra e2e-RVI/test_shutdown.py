#!/usr/bin/env python3
"""Local process regression: no device commands/policies and no production DB."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import threading
import time


def run_case(binary, template, mode):
    listener = socket.socket()
    listener.bind(('127.0.0.1', 0))
    port = listener.getsockname()[1]
    listener.listen()
    listener.settimeout(0.1)
    stopped = threading.Event()
    peers = []
    upgraded = []

    def serve_peer(peer):
        peer.settimeout(0.1)
        data = b''
        while not stopped.is_set():
            try:
                chunk = peer.recv(65536)
                if not chunk:
                    return
                data += chunk
                if mode == 'idle-websocket' and b'\r\n\r\n' in data and peer not in upgraded:
                    headers = dict(line.split(b':', 1) for line in data.split(b'\r\n')[1:] if b':' in line)
                    key = next(v.strip() for k, v in headers.items() if k.lower() == b'sec-websocket-key')
                    accept = base64.b64encode(hashlib.sha1(key + b'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest())
                    peer.sendall(b'HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' + accept + b'\r\n\r\n')
                    upgraded.append(peer)
            except socket.timeout:
                continue
            except OSError:
                return

    def serve():
        while not stopped.is_set():
            try:
                peer, _ = listener.accept()
                peers.append(peer)
                threading.Thread(target=serve_peer, args=(peer,), daemon=True).start()
            except socket.timeout:
                continue
            except OSError:
                return

    if mode == 'refused':
        listener.close()
    else:
        threading.Thread(target=serve, daemon=True).start()
    with tempfile.TemporaryDirectory(prefix='sonar-rvi-stop-') as tmp:
        conf = Path(tmp) / 'default.conf'
        conf.write_text(f'SERVER_IP=127.0.0.1;\nSERVER_PORT={port};\nNODE_TYPE=VM;\nAGENT_NAME=RVI-Stop-Test;\nTERMINAL_SHARED_SECRET={"a" * 64}; // test only\n')
        env = dict(os.environ, SONAR_CONFIG_PATH=str(conf), SONAR_DATA_DIR=tmp,
                   SONAR_TEMPLATE_PATH=str(template))
        env.pop('SONAR_TERMINAL_SHARED_SECRET', None)
        process = subprocess.Popen([str(binary)], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + 4
            while time.monotonic() < deadline:
                if mode == 'refused' or len(upgraded if mode == 'idle-websocket' else peers) >= 2:
                    break
                if process.poll() is not None:
                    raise RuntimeError('agent exited before SIGTERM')
                time.sleep(0.02)
            if mode != 'refused' and len(upgraded if mode == 'idle-websocket' else peers) < 2:
                raise RuntimeError('management and terminal channels did not reach test server')
            time.sleep(0.25)
            started = time.monotonic()
            process.send_signal(signal.SIGTERM)
            output, _ = process.communicate(timeout=9)
            elapsed = time.monotonic() - started
            if process.returncode != 0 or 'Clean shutdown complete.' not in output:
                raise RuntimeError(output)
            return {'scenario': mode, 'exit_code': process.returncode, 'shutdown_seconds': round(elapsed, 3), 'passed': True}
        finally:
            if process.poll() is None:
                process.kill()
                process.communicate()
            stopped.set()
            listener.close()
            for peer in peers:
                peer.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('binary', type=Path)
    parser.add_argument('template', type=Path)
    parser.add_argument('--output', type=Path, default=Path(__file__).with_name('shutdown-results.json'))
    args = parser.parse_args()
    results = [run_case(args.binary.resolve(), args.template.resolve(), mode)
               for mode in ('refused', 'silent-handshake', 'idle-websocket')]
    args.output.write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps(results, indent=2))
