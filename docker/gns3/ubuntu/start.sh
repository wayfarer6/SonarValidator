#!/bin/bash
set -eu

/root/gns3-fix-links.sh
ssh-keygen -A
mkdir -p /run/sshd
exec /usr/sbin/sshd -D
