# Switch-1 — 적용 명령어

- Surveillance 존 액세스 스위치 (VLAN 111/112)
- 콘솔: `telnet localhost:5044`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

```sh
ovs-vsctl --if-exists del-br br0
ovs-vsctl add-br br0
ip link set eth1 up
ovs-vsctl add-port br0 eth1 tag=111
ip link set eth2 up
ovs-vsctl add-port br0 eth2 tag=112
ip link set eth0 up
ovs-vsctl add-port br0 eth0 trunks=111,112
ip link set br0 up
ovs-vsctl show
```
