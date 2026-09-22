# Firewall — 적용 명령어

- C4I 내부 방화벽 (Alpine, 라우팅 게이트웨이 역할 — OSPF 미참여)
- 콘솔: `telnet localhost:5037`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

```sh
sysctl -w net.ipv4.ip_forward=1
ip link set lo up
ip link set eth4 up
ip addr replace 172.16.255.2/24 dev eth4
ip link set eth0 up
ip addr replace 10.99.143.2/24 dev eth0
ip link set eth1 up
ip link add link eth1 name eth1.131 type vlan id 131 2>/dev/null || true
ip link set eth1.131 up
ip addr replace 10.10.131.1/24 dev eth1.131
ip link add link eth1 name eth1.132 type vlan id 132 2>/dev/null || true
ip link set eth1.132 up
ip addr replace 10.10.132.1/24 dev eth1.132
ip link add link eth1 name eth1.133 type vlan id 133 2>/dev/null || true
ip link set eth1.133 up
ip addr replace 10.10.133.1/24 dev eth1.133
ip route replace default via 10.99.143.1 dev eth0
```
