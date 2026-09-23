#!/bin/bash
echo "nameserver 8.8.8.8" > /etc/resolv.conf
apt update
apt-get install -y openssh-server traceroute nginx curl build-essential iputils-ping telnet telnetd openbsd-inetd
