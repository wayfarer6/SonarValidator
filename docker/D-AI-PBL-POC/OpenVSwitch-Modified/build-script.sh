#!/bin/sh
set -eu

apk update
apk add openssh traceroute curl iputils busybox-extras iproute2 nftables dnsmasq

./gns3-fix-links.sh