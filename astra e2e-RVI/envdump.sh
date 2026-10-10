#!/bin/sh
# EEM applet 이 guestshell 에 전달하는 환경을 조사하기 위한 임시 스크립트.
#
# 왜 필요한가
#   EEM `cli command "guestshell run <script>"` 로 띄운 프로세스에는
#   IOSP_TOKEN/IOSP_SOCKET/IOSP_LOG 는 있는데 IOSP_SESSION 이 없었습니다.
#   그 원인을 추정하지 않고 실제 환경을 그대로 덤프해 확인합니다.
env | sort > /tmp/eem_env.txt 2>&1
dohost "show clock" > /tmp/eem_dohost.txt 2>&1
echo "exit=$?" >> /tmp/eem_dohost.txt
