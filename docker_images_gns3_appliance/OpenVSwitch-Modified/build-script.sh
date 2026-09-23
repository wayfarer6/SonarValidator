#!/bin/sh
echo "nameserver 8.8.8.8" > /etc/resolv.conf
apk update
apk add openssh traceroute curl iputils busybox-extras iproute2 nftables dnsmasq