# DMZ-Router — 적용 명령어

- DMZ(Open) 존 라우터
- 콘솔: `telnet localhost:5040`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

```sh
sysctl -w net.ipv4.ip_forward=1
ip link set lo up
ip addr replace 10.255.255.3/32 dev lo
ip link set eth7 up
ip addr replace 172.16.255.3/24 dev eth7
ip link set eth0 up
ip addr replace 10.99.10.3/24 dev eth0
ip link set eth1 up
ip link add link eth1 name eth1.141 type vlan id 141 2>/dev/null || true
ip link set eth1.141 up
ip addr replace 10.30.141.1/24 dev eth1.141
vtysh <<'VEOF'
configure terminal
interface eth1.141
 ip ospf passive
interface eth7
 ip ospf passive
router ospf
 ospf router-id 10.255.255.3
 network 10.255.255.3/32 area 0.0.0.0
 network 10.99.10.0/24 area 0.0.0.0
 network 10.30.141.0/24 area 0.0.0.0
end
write memory
VEOF
```
