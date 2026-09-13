#!/bin/sh

# 패키지 목록 업데이트
apk update

# ssh, 방화벽(nftables), dhcp(dnsmasq), 네트워크 관리 도구(iproute2) 설치
apk add openssh nftables dnsmasq iproute2 busybox-extras
