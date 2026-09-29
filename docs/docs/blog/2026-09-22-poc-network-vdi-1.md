---
slug: poc-network-vdi-1
title: "VDI-1 — 적용 명령어"
authors: [sonarvalidator]
tags: [network, poc-network, vm]
date: 2026-09-22
---
- VDI 데스크톱 1 (docker)
- 콘솔: `telnet localhost:5032`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

{/* truncate */}

```sh
ip addr replace 10.40.121.10/24 dev eth0
ip link set eth0 up
ip route replace default via 10.40.121.1 dev eth0
ip -br addr show eth0
```
