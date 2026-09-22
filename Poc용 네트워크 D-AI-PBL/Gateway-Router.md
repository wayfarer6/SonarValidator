# Gateway-Router — 적용 명령어

- 인터넷 게이트웨이 라우터 (Alpine + FRR, OSPF ASBR/default originate)
- 콘솔: `telnet localhost:5015`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

```sh
sysctl -w net.ipv4.ip_forward=1
ip link set lo up
ip addr replace 10.255.255.1/32 dev lo
ip link set eth0 up
ip addr replace 192.168.122.10/24 dev eth0
ip link set eth1 up
ip addr replace 10.99.10.1/24 dev eth1
ip link set eth7 up
ip addr replace 172.16.255.1/24 dev eth7
ip route replace default via 192.168.122.1 dev eth0
iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE 2>/dev/null || true
vtysh <<'VEOF'
configure terminal
interface eth0
 ip ospf passive
interface eth7
 ip ospf passive
router ospf
 ospf router-id 10.255.255.1
 network 10.255.255.1/32 area 0.0.0.0
 network 10.99.10.0/24 area 0.0.0.0
 default-information originate always
end
write memory
VEOF
```
