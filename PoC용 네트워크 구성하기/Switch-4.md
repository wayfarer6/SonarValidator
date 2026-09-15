# Switch-4 — 적용 명령어

- Open(DMZ) 존 액세스 스위치 (VLAN 141)
- 콘솔: `telnet localhost:5050`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

```sh
ovs-vsctl --if-exists del-br br0
ovs-vsctl add-br br0
ip link set eth1 up
ovs-vsctl add-port br0 eth1 tag=141
ip link set eth0 up
ovs-vsctl add-port br0 eth0 trunks=141
ip link set br0 up
ovs-vsctl show
```
