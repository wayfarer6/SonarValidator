#!/bin/bash
set -eu

apt update
apt-get install -y openssh-server traceroute nginx curl build-essential iputils-ping telnet telnetd openbsd-inetd

./gns3-fix-links.sh
