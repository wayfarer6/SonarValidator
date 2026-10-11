#!/usr/bin/env python3
"""IOS CLI 한 명령 실행 도우미 (RVI Cisco 진단용).

비밀번호는 다음 순서로 찾습니다. 저장소에는 절대 기록하지 않습니다.
  1) 환경변수 SONAR_CISCO_PW
  2) ~/.sonar_cisco_pw 파일 (권한 600 권장)

사용: python3 run_ios_cmd.py "show version"
"""
import os
import pathlib
import sys
import pexpect

HOST = os.environ.get("SONAR_CISCO_HOST", "10.20.0.1")
USER = os.environ.get("SONAR_CISCO_USER", "cisco")
PW_FILE = pathlib.Path.home() / ".sonar_cisco_pw"


def resolve_password():
    pw = os.environ.get("SONAR_CISCO_PW")
    if pw:
        return pw
    if PW_FILE.is_file():
        return PW_FILE.read_text().strip()
    raise SystemExit(
        "Cisco 비밀번호가 없습니다. ~/.sonar_cisco_pw(600) 또는 SONAR_CISCO_PW 를 설정하세요.")


def run(commands, timeout=30):
    pw = resolve_password()
    child = pexpect.spawn(
        "ssh",
        ["-o", "StrictHostKeyChecking=no", "-o", "PreferredAuthentications=password",
         "-o", "PubkeyAuthentication=no", "-o", "ConnectTimeout=10",
         "-o", "KexAlgorithms=+diffie-hellman-group14-sha1",
         "-o", "HostKeyAlgorithms=+ssh-rsa", "-o", "PubkeyAcceptedAlgorithms=+ssh-rsa",
         "-l", USER, HOST],
        encoding="utf-8", timeout=timeout,
    )
    out = []
    i = child.expect([r"[Pp]assword:", r"[>#]", pexpect.EOF, pexpect.TIMEOUT], timeout=timeout)
    if i == pexpect.TIMEOUT or i == pexpect.EOF:
        raise SystemExit("SSH 접속 실패\n" + child.before)
    if i == 0:
        child.sendline(pw)
        child.expect([r"[>#]", pexpect.TIMEOUT], timeout=timeout)

    # enable 로 관리자 모드 진입 (프롬프트가 > 인 경우)
    child.sendline("")
    child.expect([r"[>#]", pexpect.TIMEOUT], timeout=5)
    child.sendline("enable")
    j = child.expect([r"[Pp]assword:", r"#", pexpect.TIMEOUT], timeout=10)
    if j == 0:
        child.sendline(pw)
        child.expect(r"#", timeout=10)

    child.sendline("terminal length 0")
    child.expect(r"#", timeout=10)
    for cmd in commands:
        child.sendline(cmd)
        child.expect(r"#", timeout=timeout)
        out.append(child.before)
    child.sendline("exit")
    child.close(force=True)
    return "\n".join(out)


def clean(result):
    skip = {"enable", "terminal length 0", "exit", ""}
    lines = []
    for line in result.splitlines():
        s = line.strip()
        if not s or s in skip or s.endswith("#") or "password" in s.lower():
            continue
        lines.append(line)
    return "\n".join(lines)


if __name__ == "__main__":
    cmds = sys.argv[1:] or ["show version"]
    print(clean(run(cmds)))
