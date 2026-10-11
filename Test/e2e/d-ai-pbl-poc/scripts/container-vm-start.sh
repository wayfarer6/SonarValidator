#!/bin/bash
set -eu
/root/gns3-fix-links.sh
if [ -f /etc/ssh/sshd_config ] && getent passwd sshd >/dev/null; then
  ssh-keygen -A
  mkdir -p /run/sshd
  /usr/sbin/sshd
fi
if [ -f /etc/sonar_validator_prober/start.sh ]; then
  sh /etc/sonar_validator_prober/start.sh
fi
exec /bin/bash
