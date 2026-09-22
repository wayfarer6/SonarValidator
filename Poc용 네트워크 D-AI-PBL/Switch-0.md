# Switch-0 — 적용 명령어

- Core 스위치 (Alpine + OpenVSwitch) — 모든 라우터/방화벽 업링크, VLAN 10
- 콘솔: `telnet localhost:5042`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

```sh
ovs-vsctl --if-exists del-br br0
ovs-vsctl add-br br0
ip link set eth0 up
ovs-vsctl add-port br0 eth0 tag=10
ip link set eth1 up
ovs-vsctl add-port br0 eth1 tag=10
ip link set eth2 up
ovs-vsctl add-port br0 eth2 tag=10
ip link set eth3 up
ovs-vsctl add-port br0 eth3 tag=10
ip link set eth4 up
ovs-vsctl add-port br0 eth4 tag=10
ip link set br0 up
ovs-vsctl show
```
