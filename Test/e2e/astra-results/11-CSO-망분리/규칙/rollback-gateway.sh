#!/bin/sh
set -eu
while iptables -C FORWARD -j ASTRA_CSO_WAN 2>/dev/null; do iptables -D FORWARD -j ASTRA_CSO_WAN; done
iptables -F ASTRA_CSO_WAN 2>/dev/null || true
iptables -X ASTRA_CSO_WAN 2>/dev/null || true
rm -f /etc/local.d/astra-cso.start
