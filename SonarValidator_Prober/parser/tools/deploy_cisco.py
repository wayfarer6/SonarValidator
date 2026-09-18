#!/usr/bin/env python3
"""
Cisco 8000v (IOS-XE guestshell) 프로버 배포/검증

왜 이 방식인가 (실측으로 확정)
  Cisco 라우터에 외부에서 접속하는 경로는 다음뿐이었다.
    1) IOS SSH(22)      : 관리자 계정은 CLI 전용이고 guestshell SSH 는 키 인증만 지원
                          (Cisco 문서: "SSH access is setup with a key-based authentication")
                          → 비밀번호로 접속 불가
    2) IOS WebUI(443)   : 로그인 성공해도 CLI 명령 실행 불가
    3) RESTCONF         : 미설정 (404)
    4) GNS3 콘솔 텔넷    : guestshell bash 에 직접 연결됨  ← 이 경로를 사용
  그리고 파일 전송은 guestshell 에서 외부로 나갈 수 없으므로
    IOS 의 `copy http://...` 로 flash 에 넣고,
    IOS 와 guestshell 이 공유하는 /bootflash/guest-share 를 경유한다.

전제
  - GNS3 콘솔(기본 5018)이 guestshell bash 로 연결되어 있어야 한다.
  - IOS 에서 프로버 호스트로 HTTP 접근이 가능해야 한다 (10.20.0.3:PORT).
  - SSH 로 GNS3 호스트에 접속할 수 있어야 한다.

사용법
  python3 deploy_cisco.py [--host H] [--console PORT] [--http-port PORT]
                          [--arista-pw] [--action deploy|run|status|clean]

  예) python3 deploy_cisco.py --action deploy    # 전송 + 실행
      python3 deploy_cisco.py --action status    # 수집 결과 확인
      python3 deploy_cisco.py --action status    # 수집 결과 확인
"""

import argparse
import os
import re
import socket
import subprocess
import sys
import time

# ---------------------------------------------------------------------------
# 상수
# ---------------------------------------------------------------------------
GNS3_SSH = os.environ.get("SONAR_GNS3_SSH", "ssh1032007@172.18.136.244")
GNS3_CONSOLE = int(os.environ.get("SONAR_GNS3_CONSOLE", "5018"))
PROBER_HOST_IP = os.environ.get("SONAR_PROBER_HOST", "10.20.0.3")
HTTP_PORT = int(os.environ.get("SONAR_HTTP_PORT", "8899"))
PROBER_BIN = os.environ.get("SONAR_PROBER_BIN", "/tmp/sonar_validator_prober")
TEMPLATE = os.environ.get("SONAR_TEMPLATE",
                          "/home/osboxes/SonarValidator/SonarValidator_Prober/"
                          "Installer/default_template.sqlite")

SSH_OPTS = ["-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null",
            "-o", "BatchMode=yes", "-o", "ConnectTimeout=10"]
ANSI = re.compile(r"\x1b\[[0-9;?]*[a-zA-Z]|\x1b\[m")


def clean(raw: bytes) -> str:
    return ANSI.sub("", raw.decode("utf-8", "ignore")).replace("\x00", "")


# ---------------------------------------------------------------------------
# GNS3 호스트에서 실행되는 텔넷 세션 (표준 라이브러리만 사용)
#
#  guestshell 콘솔은 프롬프트가 나타나기까지 `less` 페이저가 먼저 뜬다.
#  따라서 접속 직후 `q` 로 페이저를 빠져나와야 bash 프롬프트를 얻는다.
# ---------------------------------------------------------------------------
REMOTE_SESSION = r'''
import socket, time, re, sys, json

ANSI = re.compile(r"\x1b\[[0-9;?]*[a-zA-Z]|\x1b\[m")
def clean(b): return ANSI.sub("", b.decode("utf-8","ignore")).replace("\x00","")

def rd(s, t=3.0):
    buf=b""; end=time.time()+t
    while time.time()<end:
        try:
            d=s.recv(65536)
            if not d: break
            buf+=d; end=time.time()+0.6
        except Exception:
            break
    return buf

def session(port):
    s=socket.create_connection(("127.0.0.1",port),15); s.settimeout(1.5)
    time.sleep(1.5); rd(s,2)
    s.sendall(b"q"); time.sleep(1.2); rd(s,2)      # less 페이저 탈출
    s.sendall(b"stty -echo\r\n"); time.sleep(1); rd(s,2)
    s.sendall(b"export PS1='P> '\r\n"); time.sleep(1); rd(s,2)
    return s

def cmd(s, c, wait=5):
    s.sendall(c.encode()+b"\r\n"); time.sleep(wait); return clean(rd(s,wait))

def main():
    port = int(sys.argv[1]) if len(sys.argv)>1 else 5018
    action = sys.argv[2] if len(sys.argv)>2 else "status"
    host = sys.argv[3] if len(sys.argv)>3 else "10.20.0.3"
    hport = sys.argv[4] if len(sys.argv)>4 else "8899"

    s = session(port)
    out = {}

    if action == "deploy":
        # IOS 의 copy 로 flash 에 넣고 guest-share 로 옮긴다
        for src, dst in [("sonar_validator_prober","guest-share/sv"),
                         ("default_template.sqlite","guest-share/tpl.sqlite"),
                         ("default.conf","guest-share/prober.conf")]:
            out[f"copy_{dst}"] = cmd(
                s, 'dohost "copy http://%s:%s/%s bootflash:%s"' % (host,hport,src,dst), 60)
        out["prepare"] = cmd(
            s,
            "mkdir -p ~/svdir && cp /bootflash/guest-share/sv ~/svdir/prober && "
            "cp /bootflash/guest-share/tpl.sqlite ~/svdir/ && "
            "cp /bootflash/guest-share/prober.conf ~/svdir/default.conf && "
            "chmod +x ~/svdir/prober && echo PREPARED", 15)
        # 실행 래퍼 생성. guestshell 은 root 가 아니므로
        # 데이터/템플릿/설정 경로를 환경변수로 홈 아래로 돌린다.
        script_lines = [
            "cat > ~/svdir/run.sh << 'EOS'",
            "#!/bin/sh",
            "cd $HOME/svdir",
            "export SONAR_DATA_DIR=$HOME/svdir/data",
            "export SONAR_TEMPLATE_PATH=$HOME/svdir/tpl.sqlite",
            "export SONAR_CONFIG_PATH=$HOME/svdir/default.conf",
            "mkdir -p $SONAR_DATA_DIR",
            "pkill -f 'svdir/prober' 2>/dev/null || true",
            "sleep 1",
            "nohup ./prober > run.log 2>&1 &",
            "echo STARTED pid=$!",
            "EOS",
            "chmod +x ~/svdir/run.sh && echo RUNNER_OK",
        ]
        for line in script_lines:
            out.setdefault("runner", "")
            s.sendall(line.encode() + b"\r\n")
            time.sleep(1.0)
            out["runner"] += clean(rd(s, 1.5))
        out["run"] = cmd(s, "cd ~/svdir && sh run.sh && sleep 3 && echo LAUNCHED", 12)

    elif action == "run":
        out["run"] = cmd(s, "cd ~/svdir && sh run.sh && sleep 3 && echo LAUNCHED", 12)

    elif action == "stop":
        out["stop"] = cmd(s, "pkill -f 'svdir/prober'; sleep 1; echo STOPPED", 6)

    elif action == "clean":
        out["clean"] = cmd(s, "pkill -f 'svdir/prober'; rm -rf ~/svdir; echo CLEANED", 8)

    # 상태/결과 조회는 항상 수행
    out["settings"] = cmd(s, "cat ~/svdir/data/settings.conf 2>&1 | head -12", 6)
    out["process"] = cmd(s, "pgrep -f svdir/prober >/dev/null && echo RUNNING || echo STOPPED", 5)
    out["counts"] = cmd(s,
        "python3 -c \"import sqlite3;c=sqlite3.connect('/home/guestshell/svdir/data/prober_db.sqlite');"
        "[print(t[0], c.execute('SELECT COUNT(*) FROM '+t[0]).fetchone()[0]) for t in "
        "c.execute(\\\"SELECT name FROM sqlite_master WHERE type='table' AND name IN "
        "('route_table','nic_info','nic_address','arp_table','vlan_status','trunk_status') "
        "ORDER BY name\\\")]\" 2>&1", 12)
    out["sample"] = cmd(s,
        "python3 -c \"import sqlite3;c=sqlite3.connect('/home/guestshell/svdir/data/prober_db.sqlite');"
        "print('--route--');[print(r) for r in c.execute('SELECT protocol,prefix,next_hop,interface_name "
        "FROM route_table LIMIT 5')];"
        "print('--arp--');[print(r) for r in c.execute('SELECT address,mac,interface_name FROM arp_table "
        "LIMIT 5')];"
        "print('--nic--');[print(r) for r in c.execute('SELECT name,state FROM nic_info LIMIT 6')]\" 2>&1", 14)
    s.close()
    print(json.dumps(out, ensure_ascii=False, indent=2))

main()
'''


def run_remote(action: str, console: int, host: str, hport: int) -> str:
    """GNS3 호스트로 스크립트를 보내 텔넷 세션을 수행하고 결과를 돌려준다."""
    proc = subprocess.run(
        ["ssh", *SSH_OPTS, GNS3_SSH, f"python3 - {console} {action} {host} {hport}"],
        input=REMOTE_SESSION, capture_output=True, text=True, timeout=600)
    return proc.stdout + (("\n[STDERR]\n" + proc.stderr) if proc.stderr.strip() else "")


def start_http_server(directory: str, port: int):
    """프로버 호스트에서 전송용 HTTP 서버를 띄운다."""
    subprocess.run(["pkill", "-f", f"http.server {port}"], capture_output=True)
    time.sleep(1)
    subprocess.Popen(["python3", "-m", "http.server", str(port)],
                     cwd=directory,
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2)
    # 기동 확인
    try:
        with socket.create_connection(("127.0.0.1", port), 3):
            return True
    except OSError:
        return False


def prepare_serving_dir() -> str:
    """전송할 파일을 한 디렉터리에 모은다."""
    import shutil
    serve = "/tmp/cisco_serve"
    os.makedirs(serve, exist_ok=True)

    if not os.path.exists(PROBER_BIN):
        sys.exit(f"프로버 바이너리가 없습니다: {PROBER_BIN}\n"
                 f"  먼저 정적 빌드를 만드세요 (README 참고)")
    shutil.copy(PROBER_BIN, os.path.join(serve, "sonar_validator_prober"))

    if os.path.exists(TEMPLATE):
        shutil.copy(TEMPLATE, os.path.join(serve, "default_template.sqlite"))
    else:
        sys.exit(f"SQLite 템플릿이 없습니다: {TEMPLATE}")

    # Cisco 라우터용 설정
    with open(os.path.join(serve, "default.conf"), "w", encoding="utf-8") as fh:
        fh.write("# Cisco 8000v 라우터용 설정\n")
        fh.write(f"SERVER_IP={PROBER_HOST_IP};\n")
        fh.write(f"SERVER_PORT=3000;\n")
        fh.write("NODE_TYPE=Router;\n")
    return serve


def main():
    parser = argparse.ArgumentParser(description="Cisco 8000v 프로버 배포/검증")
    parser.add_argument("--action", default="deploy",
                        choices=["deploy", "run", "status", "stop", "clean"])
    parser.add_argument("--host", default=PROBER_HOST_IP,
                        help="프로버 호스트 IP (HTTP 서버 주소)")
    parser.add_argument("--http-port", type=int, default=HTTP_PORT)
    parser.add_argument("--console", type=int, default=GNS3_CONSOLE)
    args = parser.parse_args()

    if args.action in ("deploy", "run"):
        serve = prepare_serving_dir()
        if not start_http_server(serve, args.http_port):
            sys.exit(f"HTTP 서버 기동 실패 (포트 {args.http_port})")
        print(f"HTTP 서버: http://{args.host}:{args.http_port}/  ({serve})")

    print(f"GNS3 콘솔 {args.console} 경유로 작업 실행: {args.action}\n")
    output = run_remote(args.action, args.console, args.host, args.http_port)
    print(output)

    if args.action == "status":
        if "route_table 0" in output and "arp_table 0" in output:
            print("[안내] 수집 데이터가 아직 없습니다. "
                  "기본 수집 주기가 30초이므로 잠시 후 다시 확인하세요.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
