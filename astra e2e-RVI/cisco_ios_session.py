#!/usr/bin/env python3
"""Cisco IOS guestshell 세션(IOSP) 복구 도우미.

왜 필요한가
----------
guestshell 안의 `dohost` 는 guestshell↔IOS CLI 세션(IOSP)이 있어야 동작합니다.
그 세션은 **IOS 측이** 만들어야 하며, IOS 에서 guestshell 앱을 재시작
(`guestshell disable` → `guestshell enable`)해야 생깁니다.
컨테이너 안에서 SSH 로 들어가거나 systemd 로 서비스를 띄우는 것만으로는
절대 생기지 않습니다. (실측 확인 — astra e2e-RVI/cisco-dohost-diagnosis.md)

비밀번호 취급
------------
비밀번호는 **파일에서만** 읽고 stdout/stderr 로 내보내지 않습니다.
명령줄 인수로 받지 않으므로 `ps` 에도 남지 않습니다.

사용:
    python3 cisco_ios_session.py <동작>
      status          IOS 접속만 하고 프롬프트/권한 확인
      restart         guestshell disable → enable (IOSP 세션 재생성)
      verify          guestshell run dohost "show version" 로 세션 확인
"""
import os
import sys
import time

import pexpect

HOST = os.environ.get("SONAR_CISCO_HOST", "10.20.0.1")
USER = os.environ.get("SONAR_CISCO_USER", "admin")
PORT = os.environ.get("SONAR_CISCO_PORT", "22")
PW_FILE = os.path.expanduser("~/.sonar_cisco_pw")

# IOS 프롬프트: exec mode 는 `Router>`, privileged 는 `Router#`,
# config 는 `Router(config)#`. 호스트명이 바뀔 수 있어 접미사로만 본다.
PROMPT_EXEC = r"[\r\n][A-Za-z0-9._-]+>\s*$"
PROMPT_PRIV = r"[\r\n][A-Za-z0-9._-]+#\s*$"
PROMPT_ANY = r"[\r\n][A-Za-z0-9._-]+[>#]\s*$"
PROMPT_PW = r"[Pp]assword:\s*$"


def load_password():
    """비밀번호 파일을 읽습니다. 없으면 안내하고 종료합니다."""
    if not os.path.isfile(PW_FILE):
        sys.exit("비밀번호 파일이 없습니다: " + PW_FILE)
    with open(PW_FILE, "r", encoding="utf-8") as handle:
        value = handle.read().strip()
    if not value:
        sys.exit("비밀번호 파일이 비어 있습니다: " + PW_FILE)
    return value


def login(child, password):
    """SSH 로그인 후 privileged mode 까지 올립니다."""
    idx = child.expect(
        [PROMPT_PW, PROMPT_PRIV, PROMPT_EXEC, r"Are you sure you want to continue connecting"],
        timeout=25,
    )
    if idx == 3:
        child.sendline("yes")
        idx = child.expect([PROMPT_PW, PROMPT_PRIV, PROMPT_EXEC], timeout=25)
    if idx == 0:
        child.sendline(password)
        idx = child.expect([PROMPT_PW, PROMPT_PRIV, PROMPT_EXEC,
                            r"Permission denied"], timeout=25)
        if idx == 3:
            sys.exit("SSH 로그인 실패(비밀번호 불일치). "
                     "로그인 비밀번호와 enable 비밀번호가 다를 수 있습니다.")
        if idx == 0:
            sys.exit("로그인 비밀번호를 다시 물었습니다 — 인증이 거부된 것으로 보입니다.")
    # exec mode 면 enable 을 시도합니다. (권한 15 계정이면 이미 # 입니다)
    if idx in (0, 2):
        child.sendline("enable")
        idx2 = child.expect([PROMPT_PW, PROMPT_PRIV], timeout=20)
        if idx2 == 0:
            child.sendline(password)
            child.expect(PROMPT_PRIV, timeout=20)
    child.expect(PROMPT_PRIV, timeout=20)


def run(child, command, timeout=30):
    """명령을 보내고 프롬프트가 돌아올 때까지의 출력을 돌려줍니다."""
    child.sendline(command)
    child.expect(PROMPT_ANY, timeout=timeout)
    text = child.before or ""
    # 에코된 명령과 프롬프트 잔여물을 제거합니다.
    lines = [ln for ln in text.replace("\r", "").split("\n")
             if ln.strip() and ln.strip() != command.strip()]
    return "\n".join(lines)


def main():
    action = sys.argv[1] if len(sys.argv) > 1 else "status"
    password = load_password()

    child = pexpect.spawn(
        "ssh",
        ["-o", "StrictHostKeyChecking=no",
         "-o", "UserKnownHostsFile=/dev/null",
         "-o", "PubkeyAuthentication=no",
         "-o", "PreferredAuthentications=password,keyboard-interactive",
         "-o", "ConnectTimeout=10",
         "-p", PORT, f"{USER}@{HOST}"],
        encoding="utf-8",
        timeout=30,
    )
    # 로그에는 SSH 대화만 남깁니다. 비밀번호는 입력 순간 화면에만 있고
    # 파일로 저장하지 않습니다.
    child.logfile_read = None

    try:
        login(child, password)
        print("[ios] privileged mode 진입")

        if action == "status":
            print("[ios] guestshell 상태:")
            print(run(child, "show app-hosting list"))
            return

        if action == "restart":
            print("[ios] guestshell disable ...")
            print(run(child, "guestshell disable", timeout=60))
            time.sleep(5)
            print("[ios] guestshell enable ... (1~3분 걸립니다)")
            print(run(child, "guestshell enable", timeout=240))
            time.sleep(10)
            print("[ios] 재활성화 후 guestshell 목록:")
            print(run(child, "show app-hosting list"))
            return

        if action == "verify":
            print("[ios] guestshell run dohost 'show version':")
            print(run(child, 'guestshell run dohost "show version"', timeout=90))
            return

        sys.exit("알 수 없는 동작: " + action)
    finally:
        child.sendline("exit")
        child.close(force=True)


if __name__ == "__main__":
    main()
