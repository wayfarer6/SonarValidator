#!/bin/sh
set -eu
# Atomic replacement of our table. No flush ruleset; existing tables remain.
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
if nft list table inet astra_cso >/dev/null 2>&1; then
  echo 'delete table inet astra_cso' > "$tmp"
fi
cat /etc/sonar_validator_prober/firewall-cso.nft >> "$tmp"
nft -c -f "$tmp"
nft -f "$tmp"

# Install the ACL before enabling VLAN gateways.
sh /etc/sonar_validator_prober/cso-vlans.sh
