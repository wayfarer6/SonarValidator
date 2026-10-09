#!/bin/sh
set -eu
# A separate chain protects WAN routing without modifying existing NAT or OSPF.
iptables -N ASTRA_CSO_WAN 2>/dev/null || true
iptables -F ASTRA_CSO_WAN
for prefix in 10.10.131.0/24 10.10.132.0/24 10.10.133.0/24 10.99.143.0/24; do
  iptables -A ASTRA_CSO_WAN -s "$prefix" -o eth0 -m comment --comment ASTRA-C-WAN-deny -j DROP
  iptables -A ASTRA_CSO_WAN -d "$prefix" -i eth0 -m comment --comment ASTRA-WAN-C-deny -j DROP
done
iptables -A ASTRA_CSO_WAN -j RETURN
iptables -C FORWARD -j ASTRA_CSO_WAN 2>/dev/null || iptables -I FORWARD 1 -j ASTRA_CSO_WAN
