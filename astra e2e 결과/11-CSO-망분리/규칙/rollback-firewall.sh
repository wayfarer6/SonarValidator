#!/bin/sh
set -eu
# Removes only this test's ACL and boot hook. VLAN gateways stay restored.
nft delete table inet astra_cso 2>/dev/null || true
sed -i '\|^sh /etc/sonar_validator_prober/cso-apply.sh$|d' /root/start.sh
