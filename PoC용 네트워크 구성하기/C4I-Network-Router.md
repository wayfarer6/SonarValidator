# C4I-Network-Router — 적용 명령어

- C4I 존 라우터 — Firewall과 OSPF 경계, static redistribute
- 콘솔: `telnet localhost:5019`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

```sh
sysctl -w net.ipv4.ip_forward=1
ip link set lo up
ip addr replace 10.255.255.4/32 dev lo
ip link set eth7 up
ip addr replace 172.16.255.4/24 dev eth7
ip link set eth0 up
ip addr replace 10.99.10.4/24 dev eth0
ip link set eth1 up
ip addr replace 10.99.143.1/24 dev eth1
vtysh <<'VEOF'
configure terminal
ip route 10.10.128.0/21 10.99.143.2
interface eth7
 ip ospf passive
router ospf
 ospf router-id 10.255.255.4
 network 10.255.255.4/32 area 0.0.0.0
 network 10.99.10.0/24 area 0.0.0.0
 network 10.99.143.0/24 area 0.0.0.0
 redistribute static
end
write memory
VEOF
```
