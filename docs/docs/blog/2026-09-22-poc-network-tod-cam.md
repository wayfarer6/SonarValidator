---
slug: poc-network-tod-cam
title: "TOD-Cam — 적용 명령어"
authors: [sonarvalidator]
tags: [network, poc-network]
date: 2026-09-22
---
- 감시 카메라 호스트 (docker)
- 콘솔: `telnet localhost:5028`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

{/* truncate */}

```sh
ip addr replace 10.20.111.10/24 dev eth0
ip link set eth0 up
ip route replace default via 10.20.111.1 dev eth0
ip -br addr show eth0
```
