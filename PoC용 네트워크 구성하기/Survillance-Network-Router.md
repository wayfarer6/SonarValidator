# Survillance-Network-Router — 적용 명령어

- 감시(Surveillance) 존 라우터
- 콘솔: `telnet localhost:5013`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

```sh
sysctl -w net.ipv4.ip_forward=1
ip link set lo up
ip addr replace 10.255.255.5/32 dev lo
ip link set eth2 up
ip addr replace 172.16.255.5/24 dev eth2
ip link set eth1 up
ip addr replace 10.99.10.5/24 dev eth1
ip link set eth0 up
ip link add link eth0 name eth0.111 type vlan id 111 2>/dev/null || true
ip link set eth0.111 up
ip addr replace 10.20.111.1/24 dev eth0.111
ip link add link eth0 name eth0.112 type vlan id 112 2>/dev/null || true
ip link set eth0.112 up
ip addr replace 10.20.112.1/24 dev eth0.112
vtysh <<'VEOF'
configure terminal
interface eth0.111
 ip ospf passive
interface eth0.112
 ip ospf passive
interface eth2
 ip ospf passive
router ospf
 ospf router-id 10.255.255.5
 network 10.255.255.5/32 area 0.0.0.0
 network 10.99.10.0/24 area 0.0.0.0
 network 10.20.111.0/24 area 0.0.0.0
 network 10.20.112.0/24 area 0.0.0.0
end
write memory
VEOF
```
