#!/usr/bin/env python3
"""Arista vEOS 에 정적 프로버를 배포한다. (비밀번호 인증, RVI 랩)

기존 deploy_arista.sh 는 `BatchMode=yes` 라 키 인증 전용이다.
RVI 랩의 Arista 는 **비밀번호 인증만** 되므로 이 스크립트를 쓴다.

전송은 base64 heredoc 으로 한다 (pty 라인 버퍼 한계 때문에 청크 분할).
  - 실측: Arista EOS 의 bash 는 CentOS 7 기반, glibc 2.17 → 정적 바이너리 필수
  - `scp -O` / sftp 는 비밀번호 대화형이라 자동화가 번거롭다

usage:
    python3 deploy_arista_rvi.py [--host H] [--user U] [--password P]
                                 [--binary PATH] [--action deploy|run|status|stop]
"""

import argparse
import base64
import os
import re
import sys
import time

import pexpect

HERE = os.path.dirname(os.path.abspath(__file__))
PROBER_DIR = os.path.abspath(os.path.join(HERE, "..", "..", ".."))

DEFAULT_HOST = "10.20.0.4"
DEFAULT_USER = "admin"
DEFAULT_PASSWORD = "ChangeThisPassword"
DEFAULT_BINARY = os.path.join(PROBER_DIR, "build_static", "sonar_validator_prober")
DEFAULT_CONF = os.path.join(PROBER_DIR, "Installer", "default.conf")
DEFAULT_TEMPLATE = os.path.join(PROBER_DIR, "Installer", "default_template.sqlite")

REMOTE_DIR = "/mnt/flash/sonar_validator"
CHUNK = 12000  # base64 문자 수 (실측 16384 까지 안전)

ANSI = re.compile(r"\x1b\[[0-9;?]*[a-zA-Z]")


def clean(text):
    return ANSI.sub("", text.replace("\r\n", "\n").replace("\r", "\n"))


class AristaSession:
    """EOS CLI → enable → bash 세션."""

    def __init__(self, host, user, password, timeout=40):
        self.child = pexpect.spawn(
            "ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "
            "-o PubkeyAuthentication=no "
            "-o PreferredAuthentications=password,keyboard-interactive "
            f"-o ConnectTimeout=12 {user}@{host}",
            encoding="utf-8", timeout=timeout)
        i = self.child.expect([r"(?i)password:", r"(?i)denied", pexpect.EOF, pexpect.TIMEOUT])
        if i != 0:
            raise RuntimeError(f"비밀번호 프롬프트를 받지 못했습니다 (index={i})")
        self.child.sendline(password)
        j = self.child.expect([r"ARISTA[#>]", r"(?i)denied", pexpect.EOF, pexpect.TIMEOUT])
        if j != 0:
            raise RuntimeError("인증 실패")
        self.child.sendline("enable")
        self.child.expect([r"ARISTA#", pexpect.TIMEOUT])
        self.child.sendline("bash")
        self.child.expect([r"[$#]", pexpect.TIMEOUT])
        self.child.sendline("stty -echo; export PS1='RVI> '")
        self.child.expect([r"RVI>", pexpect.TIMEOUT])

    def run(self, command, timeout=180, marker="__RVI_DONE__"):
        """명령을 실행하고, marker 가 나올 때까지 출력을 모은다."""
        self.child.sendline(f"{command}; echo {marker}")
        self.child.expect([marker, pexpect.TIMEOUT], timeout=timeout)
        out = clean(self.child.before or "")
        # 명령 에코 제거
        lines = out.split("\n")
        if lines and command.split(";")[0][:20] in lines[0]:
            lines = lines[1:]
        return "\n".join(lines).strip()

    def close(self):
        try:
            self.child.sendline("exit; exit")
            self.child.close()
        except Exception:
            pass


def push_file(session, local_path, remote_path):
    """base64 청크 전송 후 디코드.

    주의: 디코드 전에 반드시 `rm -f <대상>` 한다. 랩에 이미 배포된 파일이
    **다른 uid 소유**이면 셸 리다이렉트(`>`)가 Permission denied 로 실패하고,
    기존 파일이 그대로 남는다. (실측: Sep 18 배포본이 uid 85136592 소유라
    새 바이너리를 덮어쓰지 못하고 옛 바이너리가 계속 실행됨)
    """
    with open(local_path, "rb") as fh:
        blob = base64.b64encode(fh.read()).decode()
    session.run(f"rm -f {remote_path} {remote_path}.b64")
    total = len(blob)
    for off in range(0, total, CHUNK):
        part = blob[off:off + CHUNK]
        session.run(f"printf '%s' '{part}' >> {remote_path}.b64")
    out = session.run(
        f"base64 -d {remote_path}.b64 > {remote_path} 2>&1 || echo B64_DECODE_FAIL; "
        f"ls -la {remote_path} 2>&1")
    session.run(f"rm -f {remote_path}.b64")
    return total, out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default=os.environ.get("ARISTA_HOST", DEFAULT_HOST))
    ap.add_argument("--user", default=os.environ.get("ARISTA_USER", DEFAULT_USER))
    ap.add_argument("--password", default=os.environ.get("ARISTA_PASSWORD", DEFAULT_PASSWORD))
    ap.add_argument("--binary", default=os.environ.get("SONAR_PROBER_BIN", DEFAULT_BINARY))
    ap.add_argument("--conf", default=DEFAULT_CONF)
    ap.add_argument("--template", default=DEFAULT_TEMPLATE)
    ap.add_argument("--action", default="deploy",
                    choices=["deploy", "run", "status", "stop", "clean"])
    args = ap.parse_args()

    print(f"== Arista vEOS 배포: {args.user}@{args.host} → {REMOTE_DIR} ==")
    for path in (args.binary,):
        if not os.path.exists(path):
            print(f"바이너리 없음: {path}", file=sys.stderr)
            return 2
    print(f"   바이너리: {args.binary} ({os.path.getsize(args.binary)} bytes)")

    session = AristaSession(args.host, args.user, args.password)
    try:
        out = session.run("id; hostname; uname -m")
        print(f"[접속] {out}")

        # 실행 래퍼는 로컬에서 만들어 전송한다.
        # (ssh 채널에 heredoc 을 직접 쓰면 완료 marker 와 충돌해 조용히 깨진다)
        runner_local = os.path.join(HERE, "arista_run.sh")
        with open(runner_local, "w", encoding="utf-8") as fh:
            fh.write("#!/bin/sh\n"
                     "# RVI 랩 Arista vEOS 용 프로버 실행 래퍼\n"
                     "#\n"
                     "# 핵심: EOS 는 관리 인터페이스(Management1)를 ns-MGMT 네임스페이스에 둔다.\n"
                     "#       bash 는 기본 네임스페이스라 10.20.0.3(서버)에 도달하지 못한다.\n"
                     "#       실측: 기본 ns ping 100% 손실 / sudo -n ip netns exec ns-MGMT → 0% 손실, TCP 3000 OK\n"
                     "#       admin 은 sudo -n(비밀번호 없이) root 를 얻을 수 있다.\n"
                     "set -eu\n"
                     f"ROOT={REMOTE_DIR}\n"
                     "DATA=\"$ROOT/data\"\n"
                     "mkdir -p \"$DATA\"\n"
                     "# 종료는 SIGTERM (kill -9 는 SQLite hot journal 을 남긴다)\n"
                     "for p in /proc/[0-9]*; do\n"
                     "  e=$(readlink \"$p/exe\" 2>/dev/null) || continue\n"
                     "  case \"$e\" in *sonar_validator_prober) sudo -n kill -TERM \"${p#/proc/}\" 2>/dev/null || kill -TERM \"${p#/proc/}\" 2>/dev/null || true ;; esac\n"
                     "done\n"
                     "sleep 2\n"
                     "cd \"$ROOT\"\n"
                     "sudo -n ip netns exec ns-MGMT env \\\n"
                     "  SONAR_DATA_DIR=\"$DATA\" \\\n"
                     "  SONAR_TEMPLATE_PATH=\"$ROOT/sqlite_template.sqlite\" \\\n"
                     "  SONAR_CONFIG_PATH=\"$ROOT/default.conf\" \\\n"
                     "  nohup \"$ROOT/sonar_validator_prober\" > \"$ROOT/run.log\" 2>&1 &\n"
"echo STARTED pid=$!\n")

        if args.action in ("deploy",):
            session.run(f"mkdir -p {REMOTE_DIR}")
            n, ls_out = push_file(session, args.binary, f"{REMOTE_DIR}/sonar_validator_prober")
            print(f"[전송] 바이너리 base64 {n} chars")
            print(f"[검증] {ls_out}")
            if "B64_DECODE_FAIL" in ls_out:
                print("!! base64 디코드 실패", file=sys.stderr)
                return 3
            push_file(session, args.conf, f"{REMOTE_DIR}/default.conf")
            push_file(session, args.template, f"{REMOTE_DIR}/sqlite_template.sqlite")
            push_file(session, runner_local, f"{REMOTE_DIR}/run.sh")
            session.run(f"chmod +x {REMOTE_DIR}/sonar_validator_prober {REMOTE_DIR}/run.sh")
            print("[sha256] " + session.run(f"sha256sum {REMOTE_DIR}/sonar_validator_prober"))

        if args.action in ("deploy", "run"):
            print("[실행] " + session.run(f"sh {REMOTE_DIR}/run.sh"))
            time.sleep(3)

        if args.action in ("deploy", "run", "status"):
            print("[프로세스] " + session.run(
                "pgrep -f sonar_validator_prober >/dev/null && echo RUNNING || echo STOPPED"))
            print("[설정] " + session.run(f"cat {REMOTE_DIR}/data/settings.conf 2>&1 | head -12"))
            print("[로그] " + session.run(f"tail -25 {REMOTE_DIR}/run.log 2>&1"))

        if args.action == "stop":
            session.run("for p in /proc/[0-9]*; do e=$(readlink \"$p/exe\" 2>/dev/null) || continue; "
                        "case \"$e\" in */sonar_validator_prober) kill -TERM \"${p#/proc/}\";; esac; done")
            print("[중지] " + session.run(
                "sleep 2; pgrep -f sonar_validator_prober >/dev/null && echo STILL_RUNNING || echo STOPPED"))

        if args.action == "clean":
            session.run("rm -rf " + REMOTE_DIR)
            print("[정리] 삭제 완료")
    finally:
        session.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())