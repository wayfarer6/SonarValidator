#!/bin/sh
set -eu
ip link set eth1 up
for vlan in 131 132 133; do
  ip link show "eth1.$vlan" >/dev/null 2>&1 || ip link add link eth1 name "eth1.$vlan" type vlan id "$vlan"
  ip link set "eth1.$vlan" up
  ip address replace "10.10.$vlan.1/24" dev "eth1.$vlan"
done
