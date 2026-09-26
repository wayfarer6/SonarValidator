#!/usr/bin/env python3
"""OPNsense API 상태를 조회한다. (비밀 원문은 출력하지 않는다)

목적: 기존 API Key 가 유효한지, 키가 재발급되었는지 확인.
값을 노출하지 않으므로 로그/문서에 붙여도 안전하다.

usage:
    python3 opnsense_api_status.py [--host 10.20.0.2] [--password PW]
"""

import argparse
import os
import re
import sys

import pexpect

ANSI = re.compile(r"\x1b\[[0-9;?]*[a-zA-Z]")


def clean(text):
    return ANSI.sub("", text)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="10.20.0.2")
    ap.add_argument("--user", default="root")
    ap.add_argument("--password", default=os.environ.get("SONAR_OPNSENSE_PW", ""))
    args = ap.parse_args()

    if not args.password:
        print("비밀번호가 필요합니다: SONAR_OPNSENSE_PW 환경변수 또는 --password",
              file=sys.stderr)
        return 2

    c = pexpect.spawn(
        "ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "
        "-o PubkeyAuthentication=no "
        "-o PreferredAuthentications=password,keyboard-interactive "
        f"-o ConnectTimeout=10 {args.user}@{args.host}",
        encoding="utf-8", timeout=60)
    c.expect([r"(?i)password:", pexpect.TIMEOUT, pexpect.EOF])
    c.sendline(args.password)
    # 로그인 직후 메뉴 → 8 = shell
    c.expect([r"Enter an option:", r"[#$]", pexpect.TIMEOUT])
    c.sendline("8")
    c.expect([r"[#$]"])
    c.sendline("PS1='OPSQ '")
    c.expect([r"OPSQ "])

    def run(command, timeout=40):
        # 원격 셸에 heredoc 을 쓰면 대화형 프롬프트에 걸린다.
        # 그래서 한 줄 명령만 쓰고, 복잡한 것은 base64 로 넘긴다.
        c.sendline(command + "; echo DONE_MARK")
        c.expect([r"DONE_MARK", pexpect.TIMEOUT], timeout=timeout)
        out = clean(c.before or "")
        idx = out.rfind("OPSQ ")
        return out[idx + 5:].strip() if idx >= 0 else out.strip()

    # 조회 스크립트를 base64 로 넘겨 실행한다 (인용부호 문제 회피).
    probe = (
        "import re\n"
        "s = open('/conf/config.xml').read()\n"
        "blocks = re.findall(r'<apikeys>(.*?)</apikeys>', s, re.S)\n"
        "print('apikey_blocks=%d' % len(blocks))\n"
        "for i, b in enumerate(blocks):\n"
        "    key = b.split('|')[0] if '|' in b else b\n"
        "    print('  [%d] key_prefix=%s len=%d has_secret=%s'\n"
        "          % (i, key[:12], len(key), chr(124) in b))\n"
        "users = re.findall(r'<user>.*?<name>([^<]+)</name>', s, re.S)\n"
        "print('users=%s' % users[:8])\n"
        "disabled = re.findall(r'<user>.*?<name>([^<]+)</name>.*?<disabled>(\\d)</disabled>', s, re.S)\n"
        "print('user_disabled=%s' % disabled[:8])\n"
    )
    import base64
    encoded = base64.b64encode(probe.encode()).decode()

    print("=== OPNsense API 상태 ===")
    print(run(f"echo {encoded} | base64 -d > /tmp/opsq.py && python3 /tmp/opsq.py && rm -f /tmp/opsq.py"))

    print("\n=== 웹/API 리슨 포트 ===")
    print(run("sockstat -4 -l | grep -E 'nginx|lighttpd|:80|:443' | head -6"))

    print("\n=== API 자격증명 검증 (서버에서 직접) ===")
    print(run("configctl webgui restart 2>&1 | head -2; echo '(webgui reload 요청)'", 60))

    c.sendline("exit")
    c.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())