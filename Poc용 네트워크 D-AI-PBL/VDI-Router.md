# VDI-Router — 적용 명령어

- VDI 존 라우터
- 콘솔: `telnet localhost:5017`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

```sh
sysctl -w net.ipv4.ip_forward=1
ip link set lo up
ip addr replace 10.255.255.6/32 dev lo
ip link set eth2 up
ip addr replace 172.16.255.6/24 dev eth2
ip link set eth0 up
ip addr replace 10.99.10.6/24 dev eth0
ip link set eth1 up
ip link add link eth1 name eth1.121 type vlan id 121 2>/dev/null || true
ip link set eth1.121 up
ip addr replace 10.40.121.1/24 dev eth1.121
ip link add link eth1 name eth1.122 type vlan id 122 2>/dev/null || true
ip link set eth1.122 up
ip addr replace 10.40.122.1/24 dev eth1.122
vtysh <<'VEOF'
configure terminal
interface eth1.121
 ip ospf passive
interface eth1.122
 ip ospf passive
interface eth2
 ip ospf passive
router ospf
 ospf router-id 10.255.255.6
 network 10.255.255.6/32 area 0.0.0.0
 network 10.99.10.0/24 area 0.0.0.0
 network 10.40.121.0/24 area 0.0.0.0
 network 10.40.122.0/24 area 0.0.0.0
end
write memory
VEOF
```
