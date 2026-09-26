#!/usr/bin/env python3
"""GNS3 콘솔 경유 Ubuntu 24 VM Agent 배포·진단 도구.

## 왜 콘솔인가

Ubuntu VM 은 데이터 평면(`10.0.8.0/24`, `10.0.9.0/24`)에 있고
관리망(`10.20.0.0/24`)에서 직접 도달할 수 없습니다.
GNS3 콘솔 포트도 GNS3 호스트의 `127.0.0.1` 에만 바인딩되어 있어
**GNS3 호스트에 SSH 로 들어가 텔넷으로 접속하는 것**이 유일한 경로입니다.

    Ubuntu-24-VM   → 콘솔 5021 → 10.0.8.100 (Subnet A)
    Ubuntu-24-VM1  → 콘솔 5027 → 10.0.9.100 (Subnet B)

## 사용

    scp ubuntu_console.py ssh1032007@192.168.122.1:/tmp/
    ssh ssh1032007@192.168.122.1 'python3 /tmp/ubuntu_console.py --action inspect --port 5021'
    ssh ssh1032007@192.168.122.1 \
      'python3 /tmp/ubuntu_console.py --action deploy --port 5021 \
         --http 192.168.122.32:8899 --server-ip 10.20.0.3'

⚠️ `--server-ip` 는 **VM 이 도달할 수 있는 주소**여야 합니다.
   데이터 평면에서 관리망으로 가는 경로가 있으면 `10.20.0.3`,
   없으면 NAT 주소(`192.168.122.32`)를 씁니다.
"""

import argparse
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cisco_console import Console, gs_put_file  # noqa: E402

# login/일반 프롬프트
LOGIN = r"(?i)login:"
PASSWORD = r"(?i)password:"
SHELL_PROMPT = r"[\w.\-]+@[\w.\-]+:[^$#]*[$#]"
ANY_PROMPT = r"(?i)login:|" + SHELL_PROMPT


def enter_shell(console, user, password):
    """콘솔에 로그인해 bash 프롬프트까지 갑니다."""
    state = console.send("", idle=1.5, limit=6)
    if not state.strip():
        console.sock.sendall(b"\r\n")
        state = console.send("", idle=2.0, limit=8)

    if not _has_shell(state):
        if _search(state, LOGIN):
            console.sock.sendall(user.encode() + b"\r\n")
            state = console.send("", idle=2.5, limit=10)
        if _search(state, PASSWORD):
            console.sock.sendall(password.encode() + b"\r\n")
            state = console.send("", idle=3.0, limit=15)
        if not _has_shell(state):
            # 이미 로그인돼 있거나 자동 로그인인 경우
            console.sock.sendall(b"\r\n")
            state = console.send("", idle=2.0, limit=10)
    return state


def _has_shell(text):
    import re
    return bool(re.search(SHELL_PROMPT, text))


def _search(text, pattern):
    import re
    return bool(re.search(pattern, text))


def sh(console, command, wait=6.0):
    """bash 명령을 보내고 출력을 모읍니다."""
    return console.send(command, idle=2.5, limit=wait * 6)


def action_inspect(console, user, password):
    """OS·IP·도구·서버 도달성을 진단합니다."""
    print("=== 로그인 ===")
    state = enter_shell(console, user, password)
    print(state[-400:])

    print("=== OS / 커널 / 아키텍처 ===")
    print(sh(console, "echo MARK1; uname -srm; cat /etc/os-release | head -2; echo MARK1", 10)[-600:])

    print("=== 인터페이스 / IP ===")
    print(sh(console, "echo MARK2; ip -4 -o addr show 2>/dev/null || ifconfig -a; echo MARK2", 12)[-900:])

    print("=== 라우팅 / 기본 게이트웨이 ===")
    print(sh(console, "echo MARK3; ip route 2>/dev/null | head -10; echo MARK3", 10)[-700:])

    print("=== 도구 존재 ===")
    print(sh(console, "echo MARK4; for t in curl wget python3 base64 tar sudo; do "
                      "printf '%s=' \"$t\"; command -v $t >/dev/null && echo yes || echo no; done; echo MARK4", 15)[-700:])

    print("=== 사용자 / 권한 ===")
    print(sh(console, "echo MARK5; id; echo HOME=$HOME; df -h / | tail -1; echo MARK5", 12)[-700:])


def action_deploy(console, user, password, http, agent_name, node_type,
                  server_ip, remote_dir, transfer="curl"):
    """정적 프로버를 Ubuntu VM 에 배포하고 실행합니다."""
    enter_shell(console, user, password)

    print("=== 환경 확인 ===")
    home_out = sh(console, "echo GHOME:$HOME; echo GUSER:$(id -un)", 8)
    import re
    home_path = "/home/ubuntu"
    m = re.search(r"GHOME:([^\s;]+)", home_out)
    if m:
        home_path = m.group(1)
    actual_dir = remote_dir.replace("$HOME", home_path)
    print(f"  home={home_path} → {actual_dir}")

    print("=== 디렉터리 준비 ===")
    print(sh(console, f"mkdir -p {actual_dir}/data && echo MKDIR_OK", 10)[-200:])

    print("=== 설정 파일 생성 (서버 주소 주입) ===")
    conf_text = (
        "# ubuntu_console.py 가 생성\n"
        f"SERVER_IP={server_ip};\n"
        "SERVER_PORT=3000;\n"
        f"NODE_TYPE={node_type};\n"
        f"AGENT_NAME={agent_name};\n"
        "DATA_DIRECTORY=;\n"
    )
    local_conf = "/tmp/ubuntu_default.conf"
    with open(local_conf, "w", encoding="utf-8") as fh:
        fh.write(conf_text)
    print(f"  SERVER_IP={server_ip}  AGENT_NAME={agent_name}")

    print("=== 실행 래퍼 생성 ===")
    runner = (
        "#!/bin/sh\n"
        "set -u\n"
        f"ROOT={actual_dir}\n"
        "export SONAR_DATA_DIR=\"$ROOT/data\"\n"
        "export SONAR_TEMPLATE_PATH=\"$ROOT/default_template.sqlite\"\n"
        "export SONAR_CONFIG_PATH=\"$ROOT/default.conf\"\n"
        "mkdir -p \"$SONAR_DATA_DIR\"\n"
        "# 종료는 SIGTERM (kill -9 는 SQLite hot journal 을 남긴다)\n"
        "pkill -TERM -f sonar_validator_prober 2>/dev/null || true\n"
        "sleep 2\n"
        "cd \"$ROOT\"\n"
        "nohup ./sonar_validator_prober > run.log 2>&1 &\n"
        "echo STARTED pid=$!\n"
    )
    local_runner = "/tmp/ubuntu_run.sh"
    with open(local_runner, "w", encoding="utf-8") as fh:
        fh.write(runner)
    print(gs_put_file(console, local_runner, f"{actual_dir}/run.sh")[-120:])

    print("=== 파일 배치 ===")
    if transfer == "curl":
        # ⚠️ 긴 전송은 명령 직접 전송이 아니라 스크립트 + 폴링으로 해야 합니다.
        #    (8MB 는 콘솔 idle 창을 넘겨 다음 명령이 끼어들면 잘립니다)
        fetch_script = (
            "#!/bin/sh\n"
            "set -u\n"
            f"DIR='{actual_dir}'\n"
            f"HTTP='{http}'\n"
            "cd \"$DIR\" || { echo NO_DIR; exit 1; }\n"
            "for f in sonar_validator_prober default_template.sqlite; do\n"
            "  rm -f \"$f.part\"\n"
            "  if curl -fsS --max-time 900 -o \"$f.part\" \"http://$HTTP/$f\"; then\n"
            "    mv \"$f.part\" \"$f\"\n"
            "    echo \"OK $f $(wc -c < \"$f\")\"\n"
            "  else\n"
            "    echo \"FAIL $f rc=$?\"\n"
            "  fi\n"
            "done\n"
            "chmod +x sonar_validator_prober\n"
            "echo FETCH_DONE\n"
        )
        local_fetch = "/tmp/ubuntu_fetch.sh"
        with open(local_fetch, "w", encoding="utf-8") as fh:
            fh.write(fetch_script)
        print(gs_put_file(console, local_fetch, f"{actual_dir}/fetch.sh")[-120:])
        console.send(f"cd {actual_dir} && rm -f fetch.log && nohup sh fetch.sh > fetch.log 2>&1 & "
                     f"echo FETCH_STARTED", idle=2.5, limit=15)
        probe = ""
        for _ in range(40):
            time.sleep(5)
            probe = sh(console, f"cat {actual_dir}/fetch.log 2>/dev/null; echo FLAG_END", 8)
            if "FETCH_DONE" in probe:
                break
        for line in probe.split("\n"):
            if line.startswith(("OK ", "FAIL ", "FETCH_DONE", "NO_DIR")):
                print("    " + line.strip())
    else:
        print("    ⚠️ ios 방식은 Ubuntu 에서 지원하지 않습니다 (curl 사용)")

    print(gs_put_file(console, local_conf, f"{actual_dir}/default.conf")[-120:])
    print(sh(console, f"chmod +x {actual_dir}/sonar_validator_prober; ls -la {actual_dir}", 12)[-800:])

    print("=== 실행 ===")
    print(sh(console, f"sh {actual_dir}/run.sh", 15)[-300:])
    time.sleep(4)
    print(sh(console, f"pgrep -af sonar_validator_prober || echo NOT_RUNNING; "
                      f"echo '--- log ---'; tail -20 {actual_dir}/run.log 2>&1", 15)[-1200:])


def action_status(console, user, password, remote_dir):
    enter_shell(console, user, password)
    d = remote_dir.replace("$HOME", "/home/ubuntu")
    print("=== 프로세스 ===")
    print(sh(console, "pgrep -af sonar_validator_prober || echo STOPPED", 10)[-400:])
    print("=== 로그 ===")
    print(sh(console, f"tail -25 {d}/run.log 2>&1", 12)[-1200:])
    print("=== 설정 ===")
    print(sh(console, f"cat {d}/data/settings.conf 2>&1 | head -14", 10)[-600:])


def action_stop(console, user, password, remote_dir):
    enter_shell(console, user, password)
    print(sh(console, "pkill -TERM -f sonar_validator_prober; sleep 3; "
                      "pgrep -f sonar_validator_prober >/dev/null && echo STILL || echo STOPPED", 15)[-300:])


def resolve_password(explicit=""):
    """Ubuntu 비밀번호를 찾습니다.

    우선순위:
      1. ``--password`` 인자 / ``SONAR_UBUNTU_PW`` 환경변수
      2. ``~/.sonar_ubuntu_pw`` 파일 (권한 600, 값만 저장)

    ⚠️ 기본값을 코드에 박지 않습니다 — 저장소에 평문이 남습니다.

    :param explicit: 명시적으로 전달된 값
    :return: 비밀번호 (못 찾으면 빈 문자열)
    """
    if explicit:
        return explicit.strip()
    env_value = os.environ.get("SONAR_UBUNTU_PW", "")
    if env_value:
        return env_value.strip()
    for path in (os.path.expanduser("~/.sonar_ubuntu_pw"), "/tmp/.sonar_ubuntu_pw"):
        try:
            with open(path, "r", encoding="utf-8") as handle:
                value = handle.read().strip()
            if value:
                return value
        except OSError:
            continue
    return ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=5021, help="Ubuntu-24-VM=5021, VM1=5027")
    ap.add_argument("--user", default="ubuntu")
    ap.add_argument("--password", default="",
                    help="미지정 시 SONAR_UBUNTU_PW 환경변수 → ~/.sonar_ubuntu_pw 파일 순으로 찾습니다")
    ap.add_argument("--action", default="inspect",
                    choices=["inspect", "deploy", "status", "stop"])
    ap.add_argument("--http", default=os.environ.get("SONAR_HTTP", "192.168.122.32:8899"))
    ap.add_argument("--server-ip", default=os.environ.get("SONAR_SERVER_IP", "10.20.0.3"),
                    help="VM 이 도달할 수 있는 서버 주소")
    ap.add_argument("--agent-name", required=False)
    ap.add_argument("--node-type", default="VM", choices=["Router", "Switch", "VM", "Firewall"])
    ap.add_argument("--remote-dir", default="/home/ubuntu/svdir")
    ap.add_argument("--transfer", default="curl", choices=["curl"])
    args = ap.parse_args()

    password = resolve_password(args.password)
    if not password:
        print("비밀번호가 필요합니다: SONAR_UBUNTU_PW 환경변수, --password, "
              "또는 ~/.sonar_ubuntu_pw 파일", file=sys.stderr)
        return 2

    agent_name = args.agent_name or ("Ubuntu-24-VM-agent" if args.port == 5021
                                     else f"Ubuntu-24-VM{args.port}-agent")

    console = Console(args.port)
    try:
        if args.action == "inspect":
            action_inspect(console, args.user, password)
        elif args.action == "deploy":
            action_deploy(console, args.user, password, args.http, agent_name,
                          args.node_type, args.server_ip, args.remote_dir, args.transfer)
        elif args.action == "status":
            action_status(console, args.user, password, args.remote_dir)
        elif args.action == "stop":
            action_stop(console, args.user, password, args.remote_dir)
    finally:
        console.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())