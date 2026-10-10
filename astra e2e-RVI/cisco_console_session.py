#!/usr/bin/env python3
"""IOS 콘솔로 guestshell 세션(IOSP)을 복구합니다.

왜 콘솔인가
----------
- IOS SSH(22)는 관리자 로그인 암호를 요구하는데 enable 암호와 다릅니다.
- 콘솔은 로그인 없이 `Router>` 로 들어가고, `enable` 에만 암호가 필요합니다.
- 콘솔은 GNS3 호스트(`192.168.122.1`)의 loopback 에만 열려 있어
  (`127.0.0.1:5018`) 그 호스트를 경유해야 합니다.

왜 필요한가
----------
guestshell 의 `dohost` 는 guestshell↔IOS CLI 세션(IOSP)이 있어야 동작합니다.
그 세션은 **IOS 측이** 만들어야 하며, IOS 에서 guestshell 앱을 재시작
(`guestshell disable` → `guestshell enable`)해야 생깁니다.
컨테이너 안에서 SSH 로 들어가거나 systemd 로 띄우는 것만으로는 생기지 않습니다.

비밀번호 취급
------------
비밀번호는 파일(`~/.sonar_cisco_pw`)에서만 읽고, 화면·로그에 남기지 않습니다.
명령줄 인수로도 받지 않으므로 `ps` 에도 나타나지 않습니다.

사용:
    python3 cisco_console_session.py <동작>
      status     콘솔 접속 + guestshell 상태 확인 (변경 없음)
      restart    guestshell disable → enable (IOSP 세션 재생성)
      verify     guestshell run dohost "show version" 로 세션 확인
"""
import os
import sys
import time

import pexpect

GNS3_HOST = os.environ.get("SONAR_GNS3_HOST", "ssh1032007@192.168.122.1")
CONSOLE_PORT = os.environ.get("SONAR_CISCO_CONSOLE_PORT", "5018")
PW_FILE = os.path.expanduser("~/.sonar_cisco_pw")
SSH_KEY = os.path.expanduser("~/.ssh/id_ed25519")

# IOS 프롬프트는 호스트명·모드에 따라 달라지므로 접미사만 봅니다.
#   `Router>`, `Router#`, `Router(config)#`, `Router(config-applet)#`
#   ⚠️ 설정 모드 프롬프트에는 괄호가 들어갑니다. 괄호를 빼면 config 모드에서
#      프롬프트를 못 찾아 타임아웃합니다.
PROMPT_EXEC = r"[A-Za-z0-9._()\-]+>\s*$"
PROMPT_PRIV = r"[A-Za-z0-9._()\-]+#\s*$"
PROMPT_ANY = r"[A-Za-z0-9._()\-]+[>#]\s*$"
# ⚠️ 콘솔이 이미 guestshell 안에 들어가 있을 수 있습니다.
#    (누군가 콘솔에서 `guestshell` 을 실행한 채로 두면 그 상태가 유지됩니다.
#     그러면 프롬프트가 `[guestshell@guestshell ~]$` 로 보입니다.
#     이때 IOS 프롬프트를 기다리면 영원히 오지 않습니다.)
PROMPT_SHELL = r"\[guestshell@guestshell [^\]]*\]\$\s*$"
# 설정 모드 프롬프트입니다. `Router(config)#`, `Router(config-applet)#` 등.
# ⚠️ 이 상태에서는 `show ...` 같은 exec 명령이 거부됩니다 (`% Invalid input`).
#    그래서 privileged 로 보이더라도 먼저 `end` 로 빠져나와야 합니다.
PROMPT_CONFIG = r"[A-Za-z0-9._\-]+\([A-Za-z0-9._\-]+\)#\s*$"


def settle_to_ios(child):
    """콘솔 세션을 IOS <b>exec</b> privileged mode 로 되돌립니다.

    guestshell 안에 들어가 있으면 `exit` 로, 설정 모드에 있으면 `end` 로
    빠져나옵니다.

    @return (child, 이미 exec privileged mode 인지 여부)
    """
    for _ in range(8):
        idx = child.expect(
            [PROMPT_SHELL, PROMPT_CONFIG, PROMPT_PRIV, PROMPT_EXEC, pexpect.TIMEOUT],
            timeout=20,
        )
        if idx == 0:
            # guestshell 안 → 빠져나온다.
            child.sendline("exit")
            continue
        if idx == 1:
            # 설정 모드 → exec 로 빠져나온다. (show 명령을 쓰기 위해 필요)
            child.sendline("end")
            continue
        if idx == 2:
            return True          # 이미 exec privileged
        if idx == 3:
            return False         # exec mode → enable 필요
        # TIMEOUT: 프롬프트가 안 보이면 Enter 한 번.
        child.sendline("")
    sys.exit("콘솔 프롬프트를 찾지 못했습니다.")


def load_password():
    """비밀번호 파일을 읽습니다. 파일이 없으면 안내하고 종료합니다."""
    if not os.path.isfile(PW_FILE):
        sys.exit("비밀번호 파일이 없습니다: " + PW_FILE)
    with open(PW_FILE, "r", encoding="utf-8") as handle:
        value = handle.read().strip()
    if not value:
        sys.exit("비밀번호 파일이 비어 있습니다: " + PW_FILE)
    return value


def clear_input_line(child, rounds=3):
    """vty 에 남은 미완성 입력을 줄바꿈으로 끊어 비웁니다.

    왜 필요한가
    ----------
    앞선 세션이 긴 줄(예: EEM 의 `action ... cli command "..."`) 을 보내다
    끊기면 그 텍스트가 vty 입력 버퍼에 남습니다. 그 상태로 명령을 보내면
    정상 명령이 앞 텍스트에 이어 붙어 `% Invalid input detected` 가 됩니다.

    ⚠️ 줄바꿈만으로는 안 지워집니다. 남은 텍스트가 **닫히지 않은 따옴표**를
    포함하면 그 뒤의 모든 줄바꿈이 문자열 안으로 빨려 들어가 줄이 끝나지
    않습니다. 그래서 닫는 따옴표를 먼저 보내 문자열을 끝냅니다.

    ⚠️ Ctrl-C 는 쓰지 않습니다 — 로컬 `ssh -t` 가 SIGINT 로 죽어 세션이
    끊기고(EOF) 그 뒤로 프롬프트를 받지 못합니다.
    """
    for _ in range(rounds):
        child.sendline('"')          # 미완성 문자열을 닫습니다
        try:
            child.expect(PROMPT_ANY, timeout=6)
            continue
        except pexpect.TIMEOUT:
            pass
        child.sendline("")           # 남은 줄을 실행시켜 흘려보냄
        try:
            child.expect(PROMPT_ANY, timeout=6)
        except pexpect.TIMEOUT:
            continue


def run(child, command, timeout=30, retry=True):
    """명령을 보내고 프롬프트가 돌아올 때까지의 출력을 돌려줍니다.

    ⚠️ vty 잉여 입력 때문에 첫 시도가 `% Invalid input` 으로 실패할 수 있어
    한 번은 입력을 비우고 다시 보냅니다.
    """
    child.sendline(command)
    child.expect(PROMPT_ANY, timeout=timeout)
    text = (child.before or "").replace("\r", "")
    lines = [ln for ln in text.split("\n")
             if ln.strip() and ln.strip() != command.strip()]
    output = "\n".join(lines)
    if retry and "Invalid input detected" in output:
        clear_input_line(child)
        return run(child, command, timeout=timeout, retry=False)
    return output


def main():
    action = sys.argv[1] if len(sys.argv) > 1 else "status"
    password = load_password()

    # 1) GNS3 호스트로 들어가 콘솔(telnet)을 연다.
    child = pexpect.spawn(
        "ssh",
        ["-t",
         "-i", SSH_KEY,
         "-o", "BatchMode=yes",
         "-o", "StrictHostKeyChecking=no",
         "-o", "ConnectTimeout=10",
         GNS3_HOST,
         f"telnet 127.0.0.1 {CONSOLE_PORT}"],
        encoding="utf-8",
        timeout=40,
    )

    try:
        # ⚠️ 앞선 세션이 남긴 미완성 입력이 vty 에 남아 있을 수 있습니다.
        #    그 상태로 명령을 보내면 정상 명령이 앞 텍스트에 이어 붙어
        #    `% Invalid input` 이 됩니다. 줄바꿈으로 끊어 비웁니다.
        clear_input_line(child)

        privileged = settle_to_ios(child)
        if not privileged:
            child.sendline("enable")
            child.expect(r"[Pp]assword:\s*$", timeout=15)
            child.sendline(password)          # 파일에서 읽은 값. 출력하지 않는다.
            child.expect(PROMPT_PRIV, timeout=20)
        print("[console] privileged mode 진입")

        if action == "status":
            print("[console] app-hosting 목록:")
            print(run(child, "show app-hosting list"))
            return

        if action == "restart":
            print("[console] guestshell disable ...")
            print(run(child, "guestshell disable", timeout=120))
            time.sleep(5)
            print("[console] guestshell enable ... (1~3분 걸립니다)")
            print(run(child, "guestshell enable", timeout=300))
            time.sleep(15)
            print("[console] 재활성화 후 목록:")
            print(run(child, "show app-hosting list"))
            return

        if action == "verify":
            print('[console] guestshell run dohost "show version":')
            print(run(child, 'guestshell run dohost "show version"', timeout=120))
            return

        if action == "ios":
            # 임의 IOS 명령 실행 (진단용). 예: ios "guestshell ?"
            command = " ".join(sys.argv[2:])
            if not command:
                sys.exit("ios 동작에는 명령이 필요합니다.")
            print(run(child, command, timeout=90))
            return

        if action == "cfg":
            # 여러 설정 명령을 **한 세션에서** 순서대로 보냅니다.
            # (세션을 나누면 config mode 가 풀려 다음 명령이 거부됩니다)
            commands = sys.argv[2:]
            if not commands:
                sys.exit("cfg 동작에는 명령이 필요합니다.")
            for command in commands:
                child.sendline(command)
                child.expect(PROMPT_ANY, timeout=60)
                output = (child.before or "").replace("\r", "").strip()
                shown = "\n".join(
                    ln for ln in output.split("\n")
                    if ln.strip() and ln.strip() != command.strip())
                print(f"$ {command}" + (f"\n{shown}" if shown else ""))
            return

        sys.exit("알 수 없는 동작: " + action)
    finally:
        # telnet 종료 → ssh 종료
        try:
            child.sendcontrol("]")
            time.sleep(1)
            child.sendline("quit")
        except Exception:
            pass
        child.close(force=True)


if __name__ == "__main__":
    main()
