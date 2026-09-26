#!/usr/bin/env python3
"""GNS3 콘솔(telnet) 경유 Cisco 8000v Agent 배포·진단 도구.

## 왜 필요한가

Cisco IOS 는 관리자 계정으로 **CLI 전용**이고 guestshell SSH 는 **키 인증만**
지원합니다. 그래서 비밀번호로는 들어갈 수 없습니다.
콘솔 포트(5018)는 **GNS3 호스트의 127.0.0.1 에만** 바인딩되어 있으므로,
GNS3 호스트에 SSH 로 들어가 텔넷으로 접속하는 것이 유일한 경로입니다.

## guestshell 의 핵심 함정 (실측)
    guestshell eth0 : 192.168.35.2/24  (VirtualPortGroup0)
    ip nat inside source list GUEST_NAT_ACL interface GigabitEthernet1 overload
    GUEST_NAT_ACL   : permit 192.168.35.0/24

guestshell 은 **NAT(Gi1)를 통해서만** 외부에 나갑니다.
  - 192.168.122.32 (서버 NAT 주소)  → TCP_OK
  - 10.20.0.3      (서버 관리 주소) → TCP_FAIL
**따라서 SERVER_IP 에 관리 주소를 넣으면 Agent 가 조용히 무응답이 됩니다.**

자세한 분석: docs/Agent/Cisco_Guestshell_Troubleshooting.md

## 사용법

이 스크립트는 **GNS3 호스트에서** 실행합니다 (콘솔이 localhost 에만 열림).

    # GNS3 호스트로 전송
    scp cisco_console.py ssh1032007@192.168.122.1:/tmp/

    # 진단
    ssh ssh1032007@192.168.122.1 'python3 /tmp/cisco_console.py --action inspect'

    # 배포 (호스트에서 HTTP 서버가 떠 있어야 함)
    ssh ssh1032007@192.168.122.1 \
      'python3 /tmp/cisco_console.py --action deploy --http 192.168.122.32:8899'

    # 상태/중지
    ssh ssh1032007@192.168.122.1 'python3 /tmp/cisco_console.py --action status'
    ssh ssh1032007@192.168.122.1 'python3 /tmp/cisco_console.py --action stop'
"""

import argparse
import os
import re
import socket
import sys
import time

ANSI = re.compile(rb"\x1b\[[0-9;?]*[a-zA-Z]")

# 콘솔 프롬프트
IOS_EXEC = r"Router>"
IOS_ENABLE = r"Router#"
GS_PROMPT = r"\[guestshell@guestshell.*\]\$"


def clean(raw: bytes) -> str:
    return ANSI.sub(b"", raw).decode("utf-8", "ignore").replace("\r\n", "\n").replace("\r", "\n")


class Console:
    """GNS3 텔넷 콘솔 세션 (표준 라이브러리만 사용)."""

    def __init__(self, port=5018, timeout=10):
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=timeout)
        self.sock.settimeout(0.5)
        time.sleep(1.5)
        self._drain(1.0, 4.0)

    def _drain(self, idle=1.5, limit=30.0):
        """출력이 idle 초 동안 멈출 때까지 읽습니다."""
        buf = b""
        start = time.time()
        last = time.time()
        while time.time() - start < limit:
            try:
                chunk = self.sock.recv(65536)
                if not chunk:
                    break
                buf += chunk
                last = time.time()
            except Exception:
                if time.time() - last >= idle:
                    break
                time.sleep(0.05)
        return buf

    def send(self, text, idle=2.0, limit=35.0):
        self.sock.sendall(text.encode() + b"\r\n")
        time.sleep(0.4)
        return clean(self._drain(idle, limit))

    def wait_for(self, pattern, timeout=30.0):
        """패턴이 나올 때까지 읽습니다."""
        rx = re.compile(pattern)
        buf = b""
        end = time.time() + timeout
        while time.time() < end:
            try:
                chunk = self.sock.recv(65536)
                if not chunk:
                    break
                buf += chunk
                text = clean(buf)
                if rx.search(text):
                    return text
            except Exception:
                time.sleep(0.1)
        return clean(buf)

    def close(self):
        try:
            self.sock.close()
        except Exception:
            pass


def ensure_ios(console, password):
    """현재 세션이 IOS exec/enable 인지 확인하고 필요하면 guestshell 에서 나옵니다.

    ⚠️ 콘솔은 이전 작업이 끝난 위치를 그대로 이어받습니다.
    guestshell 안에서 IOS 명령(show ...)을 보내면
    `bash: show: command not found` 가 나옵니다.
    """
    state = console.send("", idle=1.5, limit=6.0)
    if re.search(GS_PROMPT, state):
        console.sock.sendall(b"exit\r\n")
        state = console.wait_for(IOS_EXEC + r"|" + IOS_ENABLE, timeout=25)
    return state


def enter_enable(console, password):
    """enable 모드로 진입합니다."""
    state = ensure_ios(console, password)
    if re.search(IOS_ENABLE, state):
        console.sock.sendall(b"terminal length 0\r\n")
        time.sleep(1.0)
        console._drain(1.0, 5.0)
        return state

    console.sock.sendall(b"enable\r\n")
    state = console.wait_for(r"(?i)password:|" + IOS_ENABLE, timeout=20)
    if re.search(r"(?i)password:", state):
        console.sock.sendall(password.encode() + b"\r\n")
        state = console.wait_for(IOS_ENABLE, timeout=20)
    console.sock.sendall(b"terminal length 0\r\n")
    time.sleep(1.0)
    console._drain(1.0, 5.0)
    return state


def enter_guestshell(console):
    """guestshell bash 로 진입합니다. 이미 들어가 있으면 그대로 둡니다."""
    state = console.send("", idle=1.5, limit=5.0)
    if re.search(GS_PROMPT, state):
        return state
    console.sock.sendall(b"guestshell\r\n")
    return console.wait_for(GS_PROMPT, timeout=45)


def ios_cmd(console, command, wait=5.0, confirm=None):
    """IOS 명령을 보내고 출력을 모읍니다.

    ⚠️ `copy` 는 대상 파일이 있으면 `Destination filename?` 과
    `over write? [confirm]` 을 물어보고 여기서 멈춥니다.
    confirm 이 주어지면 그 문자열을 추가로 보내 진행시킵니다.
    """
    out = console.send(command, idle=2.5, limit=wait * 6)
    if confirm is not None and re.search(r"\[confirm\]|Destination filename", out):
        console.sock.sendall(confirm.encode() + b"\r\n")
        time.sleep(1.0)
        out += console.send("", idle=2.5, limit=wait * 6)
    return out


def gs_cmd(console, command, wait=3.0):
    return console.send(command, idle=2.0, limit=wait * 6)


# ---------------------------------------------------------------------------
# 동작
# ---------------------------------------------------------------------------

def action_inspect(console, password):
    """guestshell 상태·자원·네트워크 도달성을 진단합니다."""
    enter_enable(console, password)

    print("=== IOS 인터페이스 ===")
    print(ios_cmd(console, "show ip interface brief", 6)[:1100])

    print("=== guestshell 상태 ===")
    print(ios_cmd(console, "show app-hosting list", 5)[:500])

    print("=== guestshell 상세 (자원·NIC) ===")
    detail = ios_cmd(console, "show app-hosting detail", 8)
    print(detail[:1800])

    print("=== NAT 규칙 ===")
    print(ios_cmd(console, "show running-config | include ip nat", 5)[:600])

    print("=== GUEST_NAT_ACL ===")
    print(ios_cmd(console, "show ip access-lists GUEST_NAT_ACL", 5)[:500])

    print("=== guestshell 도달성 (핵심) ===")
    enter_guestshell(console)
    print(gs_cmd(console, "ping -c 2 -W 2 192.168.122.32 2>&1 | tail -3", 8))
    print(gs_cmd(
        console,
        "timeout 4 bash -c 'echo > /dev/tcp/192.168.122.32/3000' && echo NAT_TCP_OK || echo NAT_TCP_FAIL",
        8))
    print(gs_cmd(
        console,
        "timeout 4 bash -c 'echo > /dev/tcp/10.20.0.3/3000' && echo MGMT_TCP_OK || echo MGMT_TCP_FAIL",
        8))
    console.sock.sendall(b"exit\r\n")
    time.sleep(2.0)


def gs_put_file(console, local_path, remote_path):
    """로컬 파일을 base64 로 guestshell 에 전송합니다.

    ⚠️ telnet 콘솔에 heredoc 을 직접 쓰면 들여쓰기와 변수 확장이 얽혀
    조용히 깨집니다(실측: `for p in ...` 줄이 중간에 잘렸습니다).
    그래서 파일은 base64 로 넘기고 원격에서 디코드합니다.
    """
    import base64
    with open(local_path, "rb") as fh:
        blob = base64.b64encode(fh.read()).decode()

    console.send(f"rm -f {remote_path}.b64", idle=1.5, limit=6)
    # 12000자 청크 (pty 라인 버퍼 한계 회피)
    chunk = 12000
    for off in range(0, len(blob), chunk):
        part = blob[off:off + chunk]
        console.send(f"printf '%s' '{part}' >> {remote_path}.b64", idle=1.0, limit=20)
    out = console.send(
        f"base64 -d {remote_path}.b64 > {remote_path} && rm -f {remote_path}.b64 "
        f"&& chmod +x {remote_path} && echo B64_OK || echo B64_FAIL",
        idle=2.0, limit=30)
    return out


def action_deploy(console, password, http, agent_name, node_type, server_ip, remote_dir,
                  transfer="curl"):
    """정적 프로버를 guestshell 에 배포하고 실행합니다.

    전송 방식 두 가지:
      - ``curl``(기본): guestshell 이 HTTP 서버에서 **직접** 받습니다.
        IOS copy·bootflash 경유가 필요 없어 가장 단순합니다. (실측 검증)
      - ``ios``: IOS ``copy`` → ``/bootflash/guest-share`` 경유.
        guestshell 에 curl/wget 이 없거나 HTTP 가 막힌 경우의 폴백입니다.
    """
    enter_enable(console, password)

    files = [
        ("sonar_validator_prober", "sonar_validator_prober"),
        ("default_template.sqlite", "default_template.sqlite"),
        # ⚠️ default.conf 는 서버 주소를 주입해야 하므로 아래에서 base64 로 보냅니다.
    ]

    if transfer == "ios":
        print("=== [폴백] IOS copy → bootflash:guest-share (공유 디렉터리) ===")
        # ⚠️ /bootflash 루트는 guestshell 사용자가 읽지 못합니다.
        #    (소유자 nobody:network-admin 이고, 컨테이너에서 접근이 막힘 — 실측)
        for src, target in files:
            out = ios_cmd(
                console,
                f"copy http://{http}/{src} bootflash:guest-share/{target}",
                60,
                confirm="\r\n")
            done = "bytes copied" in out.lower() or "copied" in out.lower()
            print(f"  {src} → {'전송 완료' if done else '확인 필요'}")
            if not done:
                print(f"    (출력) {out.strip()[-300:]}")

    enter_guestshell(console)

    print("=== guestshell 홈 확인 ===")
    home = console.send("printf 'GHOME=%s\\n' \"$HOME\"", idle=1.5, limit=8)
    print(home[-300:])
    home_path = "/home/guestshell"
    # ⚠️ 명령 구분자(;)나 개행이 경로에 섞이지 않도록 [^;\s] 만 취합니다.
    m = re.search(r"GHOME=([^;\s]+)", home)
    if m:
        home_path = m.group(1).strip()
    # remote_dir 가 $HOME 이면 실제 경로로 바꿉니다.
    actual_dir = remote_dir.replace("$HOME", home_path) if "$HOME" in remote_dir else remote_dir
    if not actual_dir.startswith("/"):
        actual_dir = f"{home_path}/{actual_dir}"
    print(f"  home={home_path}")
    print(f"  → 배포 경로: {actual_dir}")

    print("=== bootflash 접근 확인 (파일 전달 경로) ===")
    probe = console.send(
        "ls -la /bootflash/ 2>&1 | head -12; echo '---';",
        idle=2.0, limit=12)
    print(probe[-700:])

    print("=== 디렉터리 준비 ===")
    print(console.send(f"mkdir -p {actual_dir}/data && echo MKDIR_OK", idle=1.5, limit=8)[-200:])

    print("=== 실행 래퍼 생성 (base64 전송) ===")
    runner = (
        "#!/bin/sh\n"
        "set -eu\n"
        f"ROOT={actual_dir}\n"
        "export SONAR_DATA_DIR=\"$ROOT/data\"\n"
        "export SONAR_TEMPLATE_PATH=\"$ROOT/default_template.sqlite\"\n"
        "export SONAR_CONFIG_PATH=\"$ROOT/default.conf\"\n"
        "mkdir -p \"$SONAR_DATA_DIR\"\n"
        "# 종료는 SIGTERM (kill -9 는 SQLite hot journal 을 남긴다)\n"
        "for p in /proc/[0-9]*; do\n"
        "  e=$(readlink \"$p/exe\" 2>/dev/null) || continue\n"
        "  case \"$e\" in *sonar_validator_prober) kill -TERM \"${p#/proc/}\" ;; esac\n"
        "done\n"
        "sleep 2\n"
        "cd \"$ROOT\"\n"
        "nohup ./sonar_validator_prober > run.log 2>&1 &\n"
        "echo STARTED pid=$!\n"
    )
    local_runner = "/tmp/cisco_run_runner.sh"
    with open(local_runner, "w", encoding="utf-8") as fh:
        fh.write(runner)
    print(gs_put_file(console, local_runner, f"{actual_dir}/run.sh")[-300:])

    print("=== 설정 파일 생성 (서버 주소 주입) ===")
    # 프로버는 default.conf 의 SERVER_IP 로 접속합니다.
    # ⚠️ guestshell 은 NAT(Gi1) 로만 나가므로 관리 주소(10.20.x)를 넣으면
    #    조용히 무응답이 됩니다. 반드시 NAT 대역 주소를 넣어야 합니다.
    conf_text = (
        "# cisco_console.py 가 생성 (guestshell NAT 경로)\n"
        f"SERVER_IP={server_ip};\n"
        "SERVER_PORT=3000;\n"
        f"NODE_TYPE={node_type};\n"
        f"AGENT_NAME={agent_name};\n"
        "DATA_DIRECTORY=;\n"
    )
    local_conf = "/tmp/cisco_default.conf"
    with open(local_conf, "w", encoding="utf-8") as fh:
        fh.write(conf_text)
    print(f"  SERVER_IP={server_ip}")
    print(f"  AGENT_NAME={agent_name}")

    print("=== 파일 배치 ===")
    if transfer == "curl":
        # guestshell 이 HTTP 서버에서 직접 받습니다. (실측: curl=yes, 8MB 성공)
        #
        # ⚠️ 명령을 직접 보내면 안 됩니다. 바이너리가 8MB 라 다운로드가
        #    콘솔의 idle 창을 넘기고, 그 사이 다음 명령이 끼어들어 전송이
        #    잘립니다(실측: HTTP=200 인데 DL_FAIL, 파일은 이전 배포본 그대로).
        #    그래서 **스크립트로 넘기고 완료를 폴링**합니다.
        print("  방식: guestshell → curl (HTTP 직접, 스크립트+폴링)")
        fetch_script = (
            "#!/bin/sh\n"
            "set -u\n"
            f"DIR='{actual_dir}'\n"
            f"HTTP='{http}'\n"
            "cd \"$DIR\" || { echo NO_DIR; exit 1; }\n"
            "for f in sonar_validator_prober default_template.sqlite; do\n"
            "  rm -f \"$f.part\"\n"
            "  if curl -sS --max-time 900 -o \"$f.part\" \"http://$HTTP/$f\"; then\n"
            "    mv \"$f.part\" \"$f\"\n"
            "    echo \"OK $f $(wc -c < \"$f\")\"\n"
            "  else\n"
            "    echo \"FAIL $f curl_rc=$?\"\n"
            "  fi\n"
            "done\n"
            "chmod +x sonar_validator_prober\n"
            "echo FETCH_DONE\n"
        )
        local_fetch = "/tmp/cisco_fetch.sh"
        with open(local_fetch, "w", encoding="utf-8") as fh:
            fh.write(fetch_script)
        print(gs_put_file(console, local_fetch, f"{actual_dir}/fetch.sh")[-120:])
        # 백그라운드로 실행하고 완료를 폴링합니다.
        console.send(
            f"cd {actual_dir} && rm -f fetch.log && "
            f"nohup sh fetch.sh > fetch.log 2>&1 & echo FETCH_STARTED",
            idle=2.5, limit=15)
        fetch_ok = False
        for attempt in range(40):          # 최대 200초
            time.sleep(5)
            probe = console.send(
                f"cat {actual_dir}/fetch.log 2>/dev/null; "
                f"pgrep -f 'curl.*sonar_validator' >/dev/null && echo STILL_RUNNING || echo CURL_IDLE",
                idle=2.0, limit=12)
            if "FETCH_DONE" in probe:
                fetch_ok = True
                break
        for line in probe.split("\n"):
            if line.startswith(("OK ", "FAIL ", "FETCH_DONE", "NO_DIR")):
                print("    " + line.strip())
        if not fetch_ok:
            print(f"    ⚠️ 폴링 시간 초과 (시도 {attempt + 1}) — fetch.log 확인 필요")
    else:
        print("  방식: IOS guest-share → guestshell (cp)")
        check = console.send(
            "ls -la /bootflash/guest-share/ 2>&1 | head -12", idle=2.0, limit=12)
        print(check[-500:])
        for src, target in files:
            out = console.send(
                f"cp -f /bootflash/guest-share/{target} {actual_dir}/{target} && "
                f"echo COPIED_{target} || echo MISSING_{target}",
                idle=2.5, limit=25)
            for line in out.split("\n"):
                if line.startswith("COPIED_") or line.startswith("MISSING_"):
                    print("  " + line.strip())

    # 설정 파일은 값이 주입되어야 하므로 base64 로 전송합니다 (소량).
    print(gs_put_file(console, local_conf, f"{actual_dir}/default.conf")[-200:])
    print(console.send(f"chmod +x {actual_dir}/sonar_validator_prober; ls -la {actual_dir}",
                       idle=2.5, limit=15)[-800:])

    print("=== 실행 ===")
    print(console.send(f"sh {actual_dir}/run.sh", idle=3.0, limit=30)[-400:])
    time.sleep(3)
    print(console.send(f"tail -25 {actual_dir}/run.log 2>&1", idle=2.5, limit=20)[-1200:])
    console.sock.sendall(b"exit\r\n")
    time.sleep(2.0)


def action_status(console, password, remote_dir):
    enter_enable(console, password)
    enter_guestshell(console)
    d = remote_dir.replace("$HOME", "/home/guestshell")
    print("=== 프로세스 ===")
    print(gs_cmd(console, "pgrep -f sonar_validator_prober >/dev/null && echo RUNNING || echo STOPPED", 5))
    print("=== 로그 ===")
    print(gs_cmd(console, f"tail -25 {d}/run.log 2>&1", 8))
    print("=== 설정 ===")
    print(gs_cmd(console, f"cat {d}/data/settings.conf 2>&1 | head -12", 6))
    console.sock.sendall(b"exit\r\n")
    time.sleep(2.0)


def action_stop(console, password, remote_dir):
    enter_enable(console, password)
    enter_guestshell(console)
    print(gs_cmd(
        console,
        "for p in /proc/[0-9]*; do e=$(readlink \"$p/exe\" 2>/dev/null) || continue; "
        "case \"$e\" in *sonar_validator_prober) kill -TERM \"${p#/proc/}\" ;; esac; done; "
        "sleep 3; pgrep -f sonar_validator_prober >/dev/null && echo STILL || echo STOPPED",
        10))
    console.sock.sendall(b"exit\r\n")
    time.sleep(2.0)


def resolve_password(explicit: str = "") -> str:
    """관리자 비밀번호를 찾습니다.

    우선순위:
      1. ``--password`` 인자 / ``SONAR_CISCO_PW`` 환경변수
      2. ``~/.sonar_cisco_pw`` 파일 (권한 600, 값만 저장)

    파일을 2순위로 둔 이유: 셸 이력이나 프로세스 목록에 비밀번호가 남지
    않으면서도 매 실행마다 입력하지 않아도 되기 때문입니다.

    :param explicit: 명시적으로 전달된 값 (없으면 빈 문자열)
    :return: 비밀번호 (찾지 못하면 빈 문자열)
    """
    if explicit:
        return explicit.strip()
    env_value = os.environ.get("SONAR_CISCO_PW", "")
    if env_value:
        return env_value.strip()
    for path in (
        os.path.expanduser("~/.sonar_cisco_pw"),
        "/tmp/.sonar_cisco_pw",
    ):
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
    ap.add_argument("--console-port", type=int, default=int(os.environ.get("SONAR_CISCO_CONSOLE", "5018")))
    ap.add_argument("--password", default="",
                    help="미지정 시 SONAR_CISCO_PW 환경변수 → ~/.sonar_cisco_pw 파일 순으로 찾습니다")
    ap.add_argument("--action", default="inspect",
                    choices=["inspect", "deploy", "status", "stop"])
    ap.add_argument("--http", default=os.environ.get("SONAR_HTTP", "192.168.122.32:8899"),
                    help="호스트의 배포 파일 HTTP 서버 (host:port)")
    ap.add_argument("--server-ip", default=os.environ.get("SONAR_SERVER_IP", "192.168.122.32"),
                    help="⚠️ guestshell 은 NAT 주소로만 나갑니다. 관리 주소 금지")
    ap.add_argument("--agent-name", default="CiscoCatalyst8000V-Router")
    ap.add_argument("--node-type", default="Router", choices=["Router", "Switch", "VM", "Firewall"])
    ap.add_argument("--remote-dir", default=None,
                    help="기본: /home/guestshell/svdir (guestshell 절대경로)")
    ap.add_argument("--transfer", default="curl", choices=["curl", "ios"],
                    help="curl(기본): guestshell 이 HTTP 직접 다운로드 / "
                         "ios(폴백): IOS copy → bootflash:guest-share 경유")
    args = ap.parse_args()

    password = resolve_password(args.password)
    if not password:
        print("비밀번호가 필요합니다: SONAR_CISCO_PW 환경변수, --password, "
              "또는 ~/.sonar_cisco_pw 파일", file=sys.stderr)
        return 2

    if args.server_ip and args.server_ip.startswith("10.20."):
        print("⚠️ 경고: guestshell 은 관리 주소(10.20.0.x)에 도달하지 못합니다.", file=sys.stderr)
        print("   NAT 주소(192.168.122.x)를 쓰세요. 계속하면 무응답이 됩니다.", file=sys.stderr)

    console = Console(args.console_port)
    # 기본은 절대경로를 씁니다 ($HOME 확장 문제를 피한다).
    remote_dir = args.remote_dir or "/home/guestshell/svdir"
    try:
        if args.action == "inspect":
            action_inspect(console, password)
        elif args.action == "deploy":
            action_deploy(console, password, args.http,
                          args.agent_name, args.node_type, args.server_ip, remote_dir,
                          args.transfer)
        elif args.action == "status":
            action_status(console, password, remote_dir)
        elif args.action == "stop":
            action_stop(console, password, remote_dir)
    finally:
        console.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())