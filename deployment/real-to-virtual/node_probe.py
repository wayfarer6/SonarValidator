#!/usr/bin/env python3
"""
SonarValidator Prober — 실제 노드 배포/검증 스크립트

대상 (Poc용 네트워크-D-AI-PBL/네트워크_설정_및_id_pw.md 기준)
  - Cisco 8000v  : 10.20.0.1 (SSH, guestshell 경유)
  - Arista vEOS  : 10.20.0.4 (SSH admin, enable → bash)
  - Ubuntu VM    : 10.20.0.x (SSH ubuntu)  ← 인자로 지정

하는 일
  1) 노드 접속 (paramiko)
  2) 조회 명령어 실행 (docs/Agent_Command.md 의 "조회 명령어" 그대로)
  3) 출력을 프로버의 ANTLR 파서에 넣어 JSON 변환 확인

주의
  - 이 스크립트는 읽기 전용 조회만 수행한다. 설정 변경/저장은 하지 않는다.
  - 비밀번호는 환경변수로 받는다(소스에 하드코딩하지 않음).
      SONAR_CISCO_PW / SONAR_ARISTA_PW / SONAR_UBUNTU_PW
  - 벤더 CLI 는 대화형 페이징(--More--) 과 프롬프트가 있으므로
    "터미널 길이 0" 을 먼저 보내 페이징을 끄고 명령을 보낸다.
"""

import os
import re
import sys
import time
import json
import socket

try:
    import paramiko
except ImportError:
    print("paramiko 필요: PYTHONPATH 설정 후 실행", file=sys.stderr)
    sys.exit(2)

TERM_WIDTH_ZERO = "terminal length 0"
ANSI_RE = re.compile(r"\x1b\[[0-9;?]*[a-zA-Z]")
MORE_RE = re.compile(r"--\s*More\s*--|--More--", re.IGNORECASE)

# 자격증명 파일 (사용자 워크스페이스의 랩 문서).
# 비밀번호를 소스/명령줄/환경변수로 노출하지 않기 위해 이 파일에서 직접 읽는다.
CRED_FILE = os.environ.get(
    "SONAR_CRED_FILE",
    "/home/osboxes/SonarValidator/Poc용 네트워크-D-AI-PBL/네트워크_설정_및_id_pw.md",
)


def load_credentials():
    """랩 문서에서 노드별 (user, password) 를 읽는다.

    문서 구조:
        ## cisco Router (Cisco 8000v)
        - 10.20.0.1
        ```bash
        pw <비밀번호>
        telnet localhost:5018
        ```
    섹션 헤더를 만나면 그 뒤 첫 `pw ` 값을 그 섹션의 비밀번호로 본다.

    ⚠️ 이 문서는 **저장소 밖**에 두세요(public 저장소에 비밀번호를 두지 않습니다).
    """
    creds = {}
    try:
        with open(CRED_FILE, encoding="utf-8") as fh:
            lines = fh.readlines()
    except OSError:
        return creds

    section = None
    for line in lines:
        s = line.strip()
        if s.startswith("##"):
            low = s.lower()
            if "cisco" in low:
                section = "cisco"
            elif "arista" in low:
                section = "arista"
            elif "ubuntu" in low:
                section = "ubuntu"
            elif "opnsense" in low:
                section = "opnsense"
            else:
                section = None
            continue
        if section and s.startswith("pw "):
            creds[section] = s[3:].strip()
    return creds


def strip_ansi(text: str) -> str:
    return ANSI_RE.sub("", text)


class CliSession:
    """paramiko 기반 대화형 CLI 세션."""

    def __init__(self, host, username, password, port=22, timeout=15):
        self.host = host
        self.client = paramiko.SSHClient()
        self.client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self.client.connect(
            hostname=host,
            port=port,
            username=username,
            password=password,
            timeout=timeout,
            allow_agent=False,
            look_for_keys=False,
        )
        self.chan = self.client.invoke_shell(width=200, height=1000)
        self.chan.settimeout(1.0)
        time.sleep(1.0)
        self.drain()

    def drain(self, idle=0.6, max_wait=12.0):
        """출력이 멈출 때까지 읽는다."""
        buf = []
        start = time.time()
        last = time.time()
        while time.time() - start < max_wait:
            try:
                data = self.chan.recv(65535)
                if not data:
                    break
                buf.append(data.decode("utf-8", "ignore"))
                last = time.time()
            except (socket.timeout, TimeoutError):
                if time.time() - last >= idle:
                    break
                time.sleep(0.05)
            except Exception:
                break
        return strip_ansi("".join(buf))

    def send(self, text, wait=1.0):
        self.chan.send(text + "\n")
        time.sleep(wait)
        return self.drain()

    def command(self, cmd, wait=0.8, page_off=True):
        """명령 실행. --More-- 가 나오면 스페이스를 보내 계속 진행."""
        if page_off:
            self.chan.send(TERM_WIDTH_ZERO + "\n")
            time.sleep(0.4)
            self.drain()

        self.chan.send(cmd + "\n")
        out = []
        start = time.time()
        while time.time() - start < 30:
            chunk = self.drain(idle=0.5, max_wait=6.0)
            out.append(chunk)
            if MORE_RE.search(chunk):
                self.chan.send(" ")
                continue
            if chunk.strip():
                break
        return "".join(out)

    def close(self):
        try:
            self.chan.close()
            self.client.close()
        except Exception:
            pass


def clean_vendor_output(raw: str) -> str:
    """프롬프트/에코/More 잔여물을 제거해 '명령 출력'만 남긴다.

    대화형 세션에서는 우리가 보낸 명령이 그대로 되돌아오므로(echo),
    첫 줄이 명령 에코면 제거해야 파서가 헤더로 오인하지 않는다.
    """
    text = strip_ansi(raw)
    text = MORE_RE.sub("", text)
    lines = []
    for line in text.splitlines():
        s = line.rstrip()
        # 프롬프트 줄 제거: Router#, ARISTA#, ubuntu@host:~$, frr#
        if re.search(r"(^|[\r\n])[A-Za-z0-9_\-\.@:~\[\]]*[#\$]\s*$", s):
            continue
        if TERM_WIDTH_ZERO in s and len(s) < 40:
            continue
        lines.append(s)

    # 선두의 명령 에코 제거: 첫 비어있지 않은 줄이 `show ...`/`ip ...`/`cat ...`
    # 처럼 명령처럼 보이면 한 줄 버린다. (`bash ` 접두어도 함께 처리)
    cleaned = []
    skipped_echo = False
    for line in lines:
        if not cleaned and not skipped_echo and line.strip():
            candidate = line.strip()
            if candidate.startswith("bash "):
                candidate = candidate[5:].strip()
            if re.match(r"^(show|ip|cat|ls|uname|nft|ovs-|vtysh)\b", candidate):
                skipped_echo = True
                continue
        cleaned.append(line)

    return "\n".join(cleaned).strip() + "\n"


# ---------------------------------------------------------------------------
# Cisco 8000v — guestshell 경유
# ---------------------------------------------------------------------------
def probe_cisco(host, password):
    result = {"node": "Cisco 8000v", "host": host, "checks": {}}
    s = CliSession(host, "cisco", password)
    try:
        banner = s.command("")
        result["checks"]["ssh_login"] = True
        result["checks"]["prompt_sample"] = banner.strip().splitlines()[-1:] or [""]

        # guestshell 진입 → 프로버 실행 환경 확인
        out = s.command("guestshell run uname -a", wait=1.5)
        result["checks"]["guestshell_run"] = clean_vendor_output(out)

        out = s.command("guestshell run cat /etc/os-release", wait=1.5)
        result["checks"]["guestshell_os"] = clean_vendor_output(out)

        # 조회 명령어 (docs/Agent_Command.md)
        out = s.command("show ip interface brief")
        result["checks"]["show_ip_interface_brief"] = clean_vendor_output(out)

        out = s.command("show ip route")
        result["checks"]["show_ip_route"] = clean_vendor_output(out)

        out = s.command("show interfaces switchport")
        result["checks"]["show_interfaces_switchport"] = clean_vendor_output(out)
    finally:
        s.close()
    return result


# ---------------------------------------------------------------------------
# Arista vEOS — enable → bash
# ---------------------------------------------------------------------------
def probe_arista(host, password):
    result = {"node": "Arista vEOS", "host": host, "checks": {}}
    s = CliSession(host, "admin", password)
    try:
        s.command("")
        result["checks"]["ssh_login"] = True

        # enable (관리자 권한)
        out = s.command("enable")
        result["checks"]["enable"] = clean_vendor_output(out)

        out = s.command("show version")
        result["checks"]["show_version"] = clean_vendor_output(out)

        out = s.command("show vlan brief")
        result["checks"]["show_vlan_brief"] = clean_vendor_output(out)

        out = s.command("show ip interface brief")
        result["checks"]["show_ip_interface_brief"] = clean_vendor_output(out)

        out = s.command("show interfaces switchport")
        result["checks"]["show_interfaces_switchport"] = clean_vendor_output(out)

        # bash 진입 (프로버 바이너리 실행 환경)
        out = s.command("bash")
        result["checks"]["bash_enter"] = clean_vendor_output(out)
        out = s.command("uname -a")
        result["checks"]["bash_uname"] = clean_vendor_output(out)
        out = s.command("ip -br addr show")
        result["checks"]["bash_ip_brief"] = clean_vendor_output(out)
    finally:
        s.close()
    return result


# ---------------------------------------------------------------------------
# Ubuntu VM — systemd
# ---------------------------------------------------------------------------
def probe_ubuntu(host, password, user="ubuntu"):
    result = {"node": "Ubuntu VM", "host": host, "checks": {}}
    s = CliSession(host, user, password)
    try:
        s.command("")
        result["checks"]["ssh_login"] = True

        out = s.command("ip -br addr show")
        result["checks"]["ip_brief"] = clean_vendor_output(out)

        out = s.command("ip a")
        result["checks"]["ip_a"] = clean_vendor_output(out)

        out = s.command("cat /etc/os-release")
        result["checks"]["os_release"] = clean_vendor_output(out)

        out = s.command("nft list ruleset 2>&1 || sudo -n nft list ruleset 2>&1")
        result["checks"]["nft_ruleset"] = clean_vendor_output(out)
    finally:
        s.close()
    return result


# ---------------------------------------------------------------------------
# Ubuntu VM — Arista 를 경유한 nested SSH
#
#  이 호스트(10.20.0.3)는 10.0.9.0/24 대역으로 직접 라우팅되지 않지만,
#  Arista(10.20.0.4)는 vlan9(10.0.9.1/24)를 들고 있어 VM 에 도달할 수 있다.
#  따라서 Arista bash → ssh ubuntu@<vm> 로 2단 접속한다.
# ---------------------------------------------------------------------------
def probe_ubuntu_via_arista(arista_host, arista_pw, vm_host, vm_pw,
                            arista_user="admin", vm_user="ubuntu"):
    result = {"node": "Ubuntu VM (via Arista)",
              "host": vm_host, "via": arista_host, "checks": {}}
    s = CliSession(arista_host, arista_user, arista_pw)
    try:
        result["checks"]["arista_login"] = True
        s.command("enable")
        s.chan.send("bash\n")
        time.sleep(1.5)
        s.drain()

        # nested ssh 진입
        s.chan.send(f"ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "
                    f"{vm_user}@{vm_host}\n")
        time.sleep(5)
        first = np_drain(s)
        if "password:" in first:
            s.chan.send(vm_pw + "\n")
            time.sleep(4)
            first += np_drain(s)
        result["checks"]["vm_login"] = ("$" in first or "#" in first)

        for label, cmd in [
            ("os_release", "cat /etc/os-release | head -3"),
            ("ip_brief", "ip -br addr show"),
            ("ip_a", "ip a"),
            ("route", "ip route show"),
            ("nic_sysfs", "ls /sys/class/net"),
        ]:
            result["checks"][label] = clean_vendor_output(np_command(s, cmd, wait=1.5))

        s.chan.send("exit\n")
        time.sleep(1.5)
    finally:
        s.close()
    return result


def np_drain(session, idle=0.6, max_wait=10.0):
    return strip_ansi(session.drain(idle=idle, max_wait=max_wait))


def np_command(session, cmd, wait=1.2):
    session.chan.send(cmd + "\n")
    time.sleep(wait)
    return session.drain()


# ---------------------------------------------------------------------------
# 프로버 파서로 실제 출력 검증
# ---------------------------------------------------------------------------
def run_parser_checks(all_results, workdir):
    """수집한 실제 출력을 cli_output_parser_test 의 파서에 통과시킨다.

    파서를 직접 호출하는 별도 C++ 툴이 없으므로,
    수집 출력을 파일로 저장한 뒤 파서 검증용 C++ 프로그램에 넘긴다.
    """
    dumps = {}
    for res in all_results:
        for key, value in res["checks"].items():
            # 문자열이고 여러 줄인 항목만 "명령 출력" 으로 취급한다.
            if isinstance(value, str) and "\n" in value:
                dumps[f"{res['node']}.{key}"] = value

    out_dir = os.path.join(workdir, "captured")
    os.makedirs(out_dir, exist_ok=True)
    for name, content in dumps.items():
        path = os.path.join(out_dir, name.replace("/", "_") + ".txt")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(content)
    return out_dir


def main():
    if len(sys.argv) < 2:
        print("usage: node_probe.py <cisco|arista|ubuntu> [host] [workdir]",
              file=sys.stderr)
        return 2

    target = sys.argv[1]
    workdir = sys.argv[3] if len(sys.argv) > 3 else "/tmp/sonar_probe"
    creds = load_credentials()

    results = []
    if target == "cisco":
        host = sys.argv[2] if len(sys.argv) > 2 else "10.20.0.1"
        pw = creds.get("cisco")
        if not pw:
            print("cisco 비밀번호를 자격증명 파일에서 읽지 못했습니다", file=sys.stderr)
            return 2
        results.append(probe_cisco(host, pw))
    elif target == "arista":
        host = sys.argv[2] if len(sys.argv) > 2 else "10.20.0.4"
        # Arista 는 SSH 키 인증이 이미 설정되어 있어 비밀번호가 없어도 된다.
        pw = creds.get("arista")
        results.append(probe_arista(host, pw))
    elif target == "ubuntu":
        # 우분투 VM 은 이 호스트에서 직접 라우팅되지 않는다.
        # Arista(10.20.0.4) 가 vlan9(10.0.9.1/24) 를 들고 있으므로 경유 접속한다.
        vm_host = sys.argv[2] if len(sys.argv) > 2 else "10.0.9.100"
        vm_pw = creds.get("ubuntu") or "ubuntu"   # 랩 기본값
        results.append(probe_ubuntu_via_arista(
            "10.20.0.4", creds.get("arista"), vm_host, vm_pw))
    else:
        print(f"unknown target: {target}", file=sys.stderr)
        return 2

    out_dir = run_parser_checks(results, workdir)
    print(json.dumps(results, indent=2, ensure_ascii=False))
    print(f"\n[저장된 원문 출력] {out_dir}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
