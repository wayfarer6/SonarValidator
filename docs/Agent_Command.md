

### 날짜 2026-09-18 11:28

# 조회 명령어
## OpenvSwitch

- 현재 존재하는 bridge 인터페이스 확인
```bash
~ # ovs-vsctl show
3945208f-1a6d-418c-8909-ff1701a5b643
    Bridge br0
        Port br0
            Interface br0
                type: internal
        Port eth1
            tag: 141
            Interface eth1
        Port eth0
            trunks: [141]
            Interface eth0

```

- trunk 상태 확인
```bash
~ # ovs-vsctl list port | grep -E "name|tag|trunks|vlan_mode"
name                : br0
tag                 : []
trunks              : []
vlan_mode           : []
name                : eth1
tag                 : 141
trunks              : []
vlan_mode           : []
name                : eth0
tag                 : []
trunks              : [141]
vlan_mode           : []
~ # 

```

## Router 상태 확인

- Routing Table 확인 (다만 문제가 OSPF 등으로 라우터들이 서로간의 경로를 교환하기에 중복되는 경로가 나오면 이는 식별후 넘어 가야 함.)

- 방화벽이 vlan을 처리해주고 있기에 만약에 Router에서 vlan 정보가 제대로 보이지 않는다면 예외처리를 해주어야 하고 당연하지만 프로젝트 내에서 이러한 자동 수집된 설정등을 프로젝트 생성후 편집할 수 잇는 기능을 제공 해야 함.

```bash
Hello, this is FRRouting (version 8.2.2).
Copyright 1996-2005 Kunihiro Ishiguro, et al.

frr# show ip route
Codes: K - kernel route, C - connected, S - static, R - RIP,
       O - OSPF, I - IS-IS, B - BGP, E - EIGRP, N - NHRP,
       T - Table, v - VNC, V - VNC-Direct, A - Babel, F - PBR,
       f - OpenFabric,
       > - selected route, * - FIB route, q - queued, r - rejected, b - backup
       t - trapped, o - offload failure

O>* 0.0.0.0/0 [110/1] via 10.99.10.1, eth0, weight 1, 00:17:20
S>* 10.10.128.0/21 [1/0] via 10.99.143.2, eth1, weight 1, 00:18:17
O>* 10.20.111.0/24 [110/200] via 10.99.10.5, eth0, weight 1, 00:17:25
O>* 10.20.112.0/24 [110/200] via 10.99.10.5, eth0, weight 1, 00:17:25
O>* 10.30.141.0/24 [110/200] via 10.99.10.3, eth0, weight 1, 00:17:25
O>* 10.40.121.0/24 [110/200] via 10.99.10.6, eth0, weight 1, 00:17:31
O>* 10.40.122.0/24 [110/200] via 10.99.10.6, eth0, weight 1, 00:17:31
O   10.99.10.0/24 [110/100] is directly connected, eth0, weight 1, 00:17:35
C>* 10.99.10.0/24 is directly connected, eth0, 00:18:18
O   10.99.143.0/24 [110/100] is directly connected, eth1, weight 1, 00:18:17
C>* 10.99.143.0/24 is directly connected, eth1, 00:18:18
O>* 10.255.255.1/32 [110/100] via 10.99.10.1, eth0, weight 1, 00:17:21
O>* 10.255.255.3/32 [110/100] via 10.99.10.3, eth0, weight 1, 00:17:25
O   10.255.255.4/32 [110/0] is directly connected, lo, weight 1, 00:18:17
C>* 10.255.255.4/32 is directly connected, lo, 00:18:18
O>* 10.255.255.5/32 [110/100] via 10.99.10.5, eth0, weight 1, 00:17:25
O>* 10.255.255.6/32 [110/100] via 10.99.10.6, eth0, weight 1, 00:17:31
C>* 172.16.255.0/24 is directly connected, eth7, 00:18:18


```

- NIC 확인
```bash
rr# show interface eth1
Interface eth1 is up, line protocol is up
  Link ups:       0    last: (never)
  Link downs:     0    last: (never)
  vrf: default
  index 3 metric 0 mtu 1500 speed 1000 
  flags: <UP,BROADCAST,RUNNING,MULTICAST>
  Type: Ethernet
  HWaddr: 0c:97:0f:9f:00:01
  inet 10.99.143.1/24
  inet6 fe80::e97:fff:fe9f:1/64
  Interface Type Other
  Interface Slave Type None
  protodown: off 
frr# 

```


## 방화벽 (방화벽들이 VLan 정보를 갖고 있음)

- Router를 패킷을 전달하는 역할이지 내부 vlan등을 잘 알지 못하도록 설계해야 내부망 구조가 누출되는등 문제를 피할 수 있음

- agent가 alpine firewall로 보고하면 vlan 설정을 추적하는 기능을 넣어야겠음

```bash
/ # ip a
1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN qlen 1000
    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
    inet 127.0.0.1/8 scope host lo
       valid_lft forever preferred_lft forever
    inet6 ::1/128 scope host 
       valid_lft forever preferred_lft forever
2: eth1.131@eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP qlen 1000
    link/ether 02:42:7c:24:78:01 brd ff:ff:ff:ff:ff:ff
    inet 10.10.131.1/24 scope global eth1.131
       valid_lft forever preferred_lft forever
    inet6 fe80::42:7cff:fe24:7801/64 scope link 
       valid_lft forever preferred_lft forever
3: eth1.132@eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP qlen 1000
    link/ether 02:42:7c:24:78:01 brd ff:ff:ff:ff:ff:ff
    inet 10.10.132.1/24 scope global eth1.132
       valid_lft forever preferred_lft forever
    inet6 fe80::42:7cff:fe24:7801/64 scope link 
       valid_lft forever preferred_lft forever
4: eth1.133@eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP qlen 1000
    link/ether 02:42:7c:24:78:01 brd ff:ff:ff:ff:ff:ff
    inet 10.10.133.1/24 scope global eth1.133
       valid_lft forever preferred_lft forever
    inet6 fe80::42:7cff:fe24:7801/64 scope link 
       valid_lft forever preferred_lft forever
11: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UNKNOWN qlen 1000
    link/ether 02:42:7c:24:78:00 brd ff:ff:ff:ff:ff:ff
    inet 10.99.143.2/24 scope global eth0
       valid_lft forever preferred_lft forever
    inet6 fe80::42:7cff:fe24:7800/64 scope link 
       valid_lft forever preferred_lft forever
12: eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UNKNOWN qlen 1000
    link/ether 02:42:7c:24:78:01 brd ff:ff:ff:ff:ff:ff
    inet6 fe80::42:7cff:fe24:7801/64 scope link 
       valid_lft forever preferred_lft forever
13: eth2: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UNKNOWN qlen 1000
    link/ether 02:42:7c:24:78:02 brd ff:ff:ff:ff:ff:ff
    inet6 fe80::42:7cff:fe24:7802/64 scope link 
       valid_lft forever preferred_lft forever
14: eth3: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UNKNOWN qlen 1000
    link/ether 02:42:7c:24:78:03 brd ff:ff:ff:ff:ff:ff
    inet6 fe80::42:7cff:fe24:7803/64 scope link 
       valid_lft forever preferred_lft forever
15: eth4: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UNKNOWN qlen 1000
    link/ether 02:42:7c:24:78:04 brd ff:ff:ff:ff:ff:ff
    inet 172.16.255.2/24 scope global eth4
       valid_lft forever preferred_lft forever
    inet6 fe80::42:7cff:fe24:7804/64 scope link 
       valid_lft forever preferred_lft forever

```


- 방화벽 설정 (nftable)

```bash
~ # nft list ruleset
table ip filter {
	chain input {
		type filter hook input priority filter; policy accept;
	}

	chain forward {
		type filter hook forward priority filter; policy accept;
	}

	chain output {
		type filter hook output priority filter; policy accept;
	}
}
table ip nat {
	chain postrouting {
		type nat hook postrouting priority srcnat; policy accept;
		oifname "eth0" masquerade
	}
}
~ # 

```


## Linux VM (NIC 설정)

```bash
root@VDI-1:~# ip a
1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN group default qlen 1000
    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
    inet 127.0.0.1/8 scope host lo
       valid_lft forever preferred_lft forever
    inet6 ::1/128 scope host proto kernel_lo 
       valid_lft forever preferred_lft forever
114: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UNKNOWN group default qlen 1000
    link/ether 02:42:2f:7e:d6:00 brd ff:ff:ff:ff:ff:ff
    inet 10.40.121.10/24 scope global eth0
       valid_lft forever preferred_lft forever
    inet6 fe80::42:2fff:fe7e:d600/64 scope link proto kernel_ll 
       valid_lft forever preferred_lft forever
root@VDI-1:~# 


```



## Cisco 

- cisco 랑 arista는 명령어가 95프로 이상 유사함

- 전체 설정 확인 (매우 내용이 많기에 비추)
```bash
terface VirtualPortGroup0
 ip address 192.168.35.1 255.255.255.0
 ip nat inside
!         
interface GigabitEthernet1
 ip dhcp client client-id ascii 9X4SGFB55FM
 ip address 192.168.122.254 255.255.255.0
 ip nat outside
 negotiation auto
!         
interface GigabitEthernet2
 ip address 172.128.0.1 255.255.255.0
 ip nat inside
 negotiation auto
!         
interface GigabitEthernet3
 no ip address


```

- 인터페이스 확인

```bash
Router#show ip interface brief
Interface              IP-Address      OK? Method Status                Protocol
GigabitEthernet1       192.168.122.254 YES NVRAM  up                    up      
GigabitEthernet2       172.128.0.1     YES NVRAM  up                    up      
GigabitEthernet3       unassigned      YES NVRAM  down                  down    
GigabitEthernet4       10.20.0.1       YES NVRAM  up                    up      
VirtualPortGroup0      192.168.35.1    YES NVRAM  up                    up      
Router#


```

- Routing Table 확인

```bash
Router#show ip route
Codes: L - local, C - connected, S - static, R - RIP, M - mobile, B - BGP
       D - EIGRP, EX - EIGRP external, O - OSPF, IA - OSPF inter area 
       N1 - OSPF NSSA external type 1, N2 - OSPF NSSA external type 2
       E1 - OSPF external type 1, E2 - OSPF external type 2, m - OMP
       n - NAT, Ni - NAT inside, No - NAT outside, Nd - NAT DIA
       i - IS-IS, su - IS-IS summary, L1 - IS-IS level-1, L2 - IS-IS level-2
       ia - IS-IS inter area, * - candidate default, U - per-user static route
       H - NHRP, G - NHRP registered, g - NHRP registration summary
       o - ODR, P - periodic downloaded static route, l - LISP
       a - application route
       + - replicated route, % - next hop override, p - overrides from PfR
       & - replicated local route overrides by connected

Gateway of last resort is 192.168.122.1 to network 0.0.0.0

S*    0.0.0.0/0 [1/0] via 192.168.122.1
      10.0.0.0/8 is variably subnetted, 2 subnets, 2 masks
C        10.20.0.0/24 is directly connected, GigabitEthernet4
L        10.20.0.1/32 is directly connected, GigabitEthernet4
      172.128.0.0/16 is variably subnetted, 2 subnets, 2 masks
C        172.128.0.0/24 is directly connected, GigabitEthernet2
L        172.128.0.1/32 is directly connected, GigabitEthernet2
      192.168.35.0/24 is variably subnetted, 2 subnets, 2 masks
C        192.168.35.0/24 is directly connected, VirtualPortGroup0
L        192.168.35.1/32 is directly connected, VirtualPortGroup0
      192.168.122.0/24 is variably subnetted, 2 subnets, 2 masks
C        192.168.122.0/24 is directly connected, GigabitEthernet1
L        192.168.122.254/32 is directly connected, GigabitEthernet1

```

- Trunk 규칙 확인

```bash
show interfaces switchport
```

## Arista 

- show running-config

```bash
! Command: show running-config
! device: ARISTA (vEOS-lab, EOS-4.30.2F)
!
! boot system flash:/vEOS-lab.swi
!
no aaa root
!
username admin privilege 15 role network-admin secret sha512 $6$sWMorYuXVDzCzQuk$htkGlU3tGHzhgCm7NZtyaP46tGYtr1ulOb/Vr0AIBQ5P8xZfAdtzhYm1n0zTyxMPTAzs.9fu5m3DeVHQWYoxO0
!
dhcp server
   dns server ipv4 8.8.8.8 1.1.1.1
   !
   subnet 10.0.8.0/24
      range 10.0.8.100 10.0.8.250
      name VLAN8_CLIENTS
      default-gateway 10.0.8.1
   !
   subnet 10.0.9.0/24
      range 10.0.9.100 10.0.9.250
      name VLAN9_CLIENTS
      default-gateway 10.0.9.1
!
transceiver qsfp default-mode 4x10G
!
service routing protocols model multi-agent
!
hostname ARISTA
!
spanning-tree mode mstp
!
system l1
   unsupported speed action error
   unsupported error-correction action error
!
vlan 8
   name VLAN8
!
vlan 9
   name VLAN9
!
vlan 99
   name TRANSIT
!
vrf instance MGMT
!
management api http-commands
   no shutdown
   !
   vrf MGMT
      no shutdown
      ip access-group MGMT_ACL
!
interface Ethernet1
   description TO_FORTIGATE
   switchport access vlan 99
   no switchport
   ip address 172.18.10.2/24
!
interface Ethernet2
   description UBUNTU_VLAN8
   switchport access vlan 8
   spanning-tree portfast
!
interface Ethernet3
   description UBUNTU_VLAN9
   switchport access vlan 9
   spanning-tree portfast
!
interface Ethernet4
!
interface Ethernet5
!
interface Ethernet6
!
interface Ethernet7
!
interface Ethernet8
!
interface Ethernet9
!
interface Ethernet10
!
interface Ethernet11
!
interface Ethernet12
!
interface Management1
   description OOB_MANAGEMENT
   vrf MGMT
   ip address 10.20.0.4/24
!
interface Vlan8
   ip address 10.0.8.1/24
   dhcp server ipv4
!
interface Vlan9
   ip address 10.0.9.1/24
   dhcp server ipv4
!
ip access-list standard MGMT_ACL
   10 permit 10.20.0.0/24
   20 permit 10.0.8.0/24
   30 permit 10.0.9.0/24
   40 deny any
   50 permit host 10.20.0.2
!
ip routing
ip routing vrf MGMT
!
ip route 0.0.0.0/0 172.18.10.1
ip route vrf MGMT 0.0.0.0/0 10.20.0.3
!
```


- show vlan config

```bash
ARISTA#show vlan brief
VLAN  Name                             Status    Ports
----- -------------------------------- --------- -------------------------------
1     default                          active    Et4, Et5, Et6, Et7, Et8, Et9
                                                 Et10, Et11, Et12
8     VLAN8                            active    Cpu, Et2
9     VLAN9                            active    Cpu, Et3
99    TRANSIT                          active    

ARISTA#

```

- vlan 전체 확인

```bash
ARISTA#show ip interface brief
                                                                        Address
Interface       IP Address          Status      Protocol         MTU    Owner  
--------------- ------------------- ----------- ------------- --------- -------
Ethernet1       172.18.10.2/24      up          up              1500           
Management1     10.20.0.4/24        up          up              1500           
Vlan8           10.0.8.1/24         up          up              1500           
Vlan9           10.0.9.1/24         up          up              1500           

ARISTA#

```

# 제어 명령어

## 공통 규약

제어 명령어는 서버가 내려주는 정책 JSON(`docs/Agent/*_Policy_Design.md`)을 프로버가
벤더별 CLI로 변환해 실행하는 구조입니다. 따라서 이 문서의 명령어는 **정책 JSON ↔ 실제 CLI**
대응을 기준으로 정리합니다.

### command 필드

| command | 의미 | 벤더별 표현 |
|---|---|---|
| `create` | 생성 / 활성화 / 갱신 | `add`, `set`, `ip add` (멱등성 보장 필요) |
| `remove` | 삭제 / 비활성화 | `del`, `no ...`, `clear`, `delete` |
| `on` | 대상 활성 (인터페이스 up, trunk on) | `no shutdown`, `ip link set up` |
| `off` | 대상 비활성 (인터페이스 down, trunk off) | `shutdown`, `ip link set down` |
| `get` | 조회 (조회 명령어 섹션과 동일 결과를 회신) | `show ...`, `list ...` |

### 정책 → 명령 매핑 요약

| 정책 항목 | OVS | FRR | nftables | Ubuntu VM | Cisco | Arista |
|---|---|---|---|---|---|---|
| NIC 생성 | `ovs-vsctl add-port` | `ip link add` | `ip link add` | `ip link add` / netplan | `interface` | `interface` |
| NIC on/off | `ip link set up/down` | `no shutdown` | `ip link set up/down` | `ip link set up/down` | `no shutdown` | `no shutdown` |
| VLAN 생성 | `set port tag=` | `ip link add link ... type vlan` | `ip link add link ... type vlan` | netplan `vlans:` | `vlan N` / `interface VlanN` | `vlan N` / `interface VlanN` |
| Trunk 생성 | `set port trunks=` | — (라우터는 sub-if) | — (방화벽은 sub-if) | — | `switchport mode trunk` | `switchport mode trunk` |
| ACL | `ovs-ofctl add-flow` | `access-list` + `route-map` | `nft add rule` | `nft` (ufw) | `ip access-list` | `ip access-list` |
| 정적 라우트 | `ip route add` | `ip route` | `ip route add` | netplan `routes:` | `ip route` | `ip route` |
| 설정 저장 | (커널 즉시 반영) | `write memory` | `nft list ruleset > conf` | `netplan apply` | `copy run start` | `copy run start` |

### 공통 원칙

- **백업 먼저**: 제어 전 현재 설정을 반드시 확보합니다(조회 명령어 활용). 실패 시 롤백 근거가 됩니다.
- **멱등성**: 자동 수집된 설정을 프로젝트에서 재편집할 수 있어야 하므로(문서 상단 요구사항),
  생성 계열은 중복 실행해도 동일 결과가 되도록 `--if-exists` / `replace` / `no` 선행을 사용합니다.
- **Router에 VLAN 정보를 주지 않음**: VLAN은 스위치와 방화벽이 소유합니다. 라우터는 sub-interface만
  알고, agent가 alpine firewall로 보고하면 VLAN 추적 기능이 별도로 동작해야 합니다.
- **권한**: 모든 제어 명령은 root(또는 관리자 privilege)로 실행됩니다. 프로버는 systemd/rc-service로 상주합니다.

```bash
# 제어 전 스냅샷 (프로버가 정책 적용 전 자동 수행 권장)
ovs-vsctl show > /var/lib/sonar-validator/backup-$(date +%s)-ovs.txt
vtysh -c "show running-config" > /var/lib/sonar-validator/backup-$(date +%s)-frr.txt
nft list ruleset > /var/lib/sonar-validator/backup-$(date +%s)-nft.txt
```

---

## Open vSwitch

- Alpine + OpenVSwitch 기반 L2 스위치(Switch-0 ~ Switch-4).
- 모든 제어는 `ovs-vsctl`(DB 제어), `ovs-ofctl`(OpenFlow 데이터플레인), `ip`(커널 인터페이스) 조합입니다.

### 브리지 생성 / 삭제

```bash
# 생성 (멱등: 기존 브리지 제거 후 재생성)
ovs-vsctl --if-exists del-br br0
ovs-vsctl add-br br0
ovs-vsctl set bridge br0 stp_enable=false
ip link set br0 up

# 삭제
ovs-vsctl del-br br0            # 브리지의 모든 포트가 함께 제거됨

# 브리지 속성 편집
ovs-vsctl set bridge br0 other-config:datapath-id=0000000000000001
ovs-vsctl set bridge br0 fail_mode=secure
ovs-vsctl clear bridge br0 fail_mode
```

### NIC(포트) 생성 / 삭제

```bash
# 포트 추가
ip link set eth1 up
ovs-vsctl add-port br0 eth1              # 포트명 = 인터페이스명

# 이름을 분리해 포트 추가
ovs-vsctl add-port br0 vlan111 -- set interface eth1 type=system

# 포트 삭제 (멱등)
ovs-vsctl --if-exists del-port br0 eth1

# 포트 확인
ovs-vsctl list-ports br0
ovs-vsctl port-to-br eth1
```

### VLAN (access / tag)

```bash
# access VLAN 할당 (PoC 실사용: Switch-1 eth1 tag=111)
ovs-vsctl set port eth1 tag=111

# access VLAN 변경 (갱신)
ovs-vsctl set port eth1 tag=112

# access VLAN 제거 → untagged 로 원복
ovs-vsctl remove port eth1 tag 111
ovs-vsctl clear port eth1 tag

# VLAN 모드 명시
ovs-vsctl set port eth1 vlan_mode=access tag=111
ovs-vsctl set port eth0 vlan_mode=trunk trunks=111,112
ovs-vsctl set port eth0 vlan_mode=native-untagged tag=10 trunks=10,20
```

### Trunk 생성 / 삭제

```bash
# trunk 허용 VLAN 설정 (PoC 실사용: Switch-1 eth0 trunks=111,112)
ovs-vsctl set port eth0 vlan_mode=trunk trunks=111,112

# 허용 VLAN 추가 (기존 유지)
ovs-vsctl set port eth0 trunks=111,112,141

# trunk 해제 → 일반 포트로 원복
ovs-vsctl clear port eth0 trunks
ovs-vsctl set port eth0 vlan_mode=access tag=10
```

### ACL / 필터 (OpenFlow flow)

```bash
# 서브넷 간 차단 (정책 action=deny/drop 대응)
ovs-ofctl add-flow br0 priority=100,ip,nw_src=192.168.1.0/24,nw_dst=192.168.2.0/24,actions=drop

# 허용 (action=allow)
ovs-ofctl add-flow br0 priority=100,ip,nw_src=192.168.1.0/24,nw_dst=192.168.2.0/24,actions=normal

# flow 삭제 (해당 브리지 전체)
ovs-ofctl del-flows br0
# 조건부 삭제
ovs-ofctl del-flows br0 "ip,nw_src=192.168.1.0/24,nw_dst=192.168.2.0/24"

# flow 확인
ovs-ofctl dump-flows br0
ovs-ofctl show br0
```

### L3 / 서브넷 / 라우팅

```bash
# 브리지에 관리 IP 부여
ip addr replace 10.0.0.1/24 dev br0
ip link set br0 up

# 정적 라우트 추가 / 갱신 / 삭제
ip route replace 10.200.0.0/16 via 192.168.1.254
ip route del 10.200.0.0/16 via 192.168.1.254

# VLAN 하위 인터페이스(OS 레벨, 라우터 역할 시)
ip link add link eth1 name eth1.131 type vlan id 131 2>/dev/null || true
ip link set eth1.131 up
ip addr replace 10.10.131.1/24 dev eth1.131
ip link del eth1.131
```

### 설정 초기화

```bash
# 포트만 전부 제거 (브리지 유지)
for p in $(ovs-vsctl list-ports br0); do ovs-vsctl --if-exists del-port br0 "$p"; done

# 데이터베이스 백업 / 복원
ovsdb-tool show-log -m /etc/openvswitch/conf.db | tail -50
cp /etc/openvswitch/conf.db /var/lib/sonar-validator/conf.db.bak
```

---

## FRR Router

- Linux FRRouting 기반 라우터(Gateway / DMZ / C4I / Survillance / VDI).
- 제어는 `vtysh`(라우팅 제어평면)와 `ip`(커널/인터페이스) 두 경로를 씁니다.
- `vtysh -c` 원라이너로 비대화형 적용이 가능하며, 프로버는 pty 세션(`vtysh`)을 유지합니다.

### 인터페이스 on / off (정책 command=on/off)

```bash
# 비활성화
vtysh -c "configure terminal" -c "interface eth0" -c "shutdown"
# 활성화
vtysh -c "configure terminal" -c "interface eth0" -c "no shutdown"

# 커널 레벨 (FRR 밖)
ip link set eth0 up
ip link set eth0 down
```

### NIC 생성 / 서브인터페이스(Trunk 하위 VLAN)

```bash
# VLAN 하위 인터페이스 생성 (PoC 실사용: eth1.141 / eth0.111 등)
ip link add link eth1 name eth1.141 type vlan id 141 2>/dev/null || true
ip link set eth1.141 up
ip addr replace 10.30.141.1/24 dev eth1.141

# 삭제
ip link del eth1.141

# FRR 쪽 인터페이스 설정 (MTU, 설명)
vtysh -c "configure terminal" -c "interface eth1.141" -c "description DMZ-PWS-GW" -c "mtu 1500"
```

### OSPF 활성화 / 비활성화 / 갱신

```bash
# 활성화 (정책 protocol=ospf, router_id, networks[])
vtysh -c "configure terminal" \
      -c "router ospf" \
      -c "ospf router-id 10.255.255.5" \
      -c "network 10.99.10.0/24 area 0" \
      -c "network 10.20.111.0/24 area 0"

# 관리망(172.16.255.0/24)은 광고 제외
vtysh -c "configure terminal" -c "router ospf" -c "passive-interface eth7"

# area / network 갱신 (기존 제거 후 재등록)
vtysh -c "configure terminal" -c "router ospf" -c "no network 10.20.111.0/24 area 0"
vtysh -c "configure terminal" -c "router ospf" -c "network 10.20.111.0/24 area 0"

# OSPF 전체 삭제
vtysh -c "configure terminal" -c "no router ospf"
```

### 정적 라우트 (C4I 존 집계 예시)

```bash
# 추가 (PoC 실사용: C4I-R → Firewall 집계 경로)
vtysh -c "configure terminal" -c "ip route 10.10.128.0/21 10.99.143.2"

# OSPF로 재광고 (Type-5 LSA)
vtysh -c "configure terminal" -c "router ospf" -c "redistribute static"

# 삭제
vtysh -c "configure terminal" -c "no ip route 10.10.128.0/21 10.99.143.2"
```

### ACL / 경로 필터

```bash
# 표준 ACL
vtysh -c "configure terminal" -c "access-list BLOCK_SUBNETA seq 10 deny 192.168.1.0/24"
vtysh -c "configure terminal" -c "access-list BLOCK_SUBNETA seq 20 permit any"
# 삭제
vtysh -c "configure terminal" -c "no access-list BLOCK_SUBNETA"

# prefix-list
vtysh -c "configure terminal" -c "ip prefix-list DENY_B seq 5 deny 192.168.2.0/24"
vtysh -c "configure terminal" -c "no ip prefix-list DENY_B"

# route-map (재분배 필터링)
vtysh -c "configure terminal" -c "route-map RM_OSPF permit 10" -c "match ip address prefix-list DENY_B"
vtysh -c "configure terminal" -c "no route-map RM_OSPF"
```

### 로그

```bash
# syslog 서버로 전송
vtysh -c "configure terminal" -c "log syslog informational"
# 파일 로깅
vtysh -c "configure terminal" -c "log file /var/log/frr/frr.log notifications"
# 해제
vtysh -c "configure terminal" -c "no log syslog"
```

### 저장 / 원복 / 초기화

```bash
# 설정 저장
vtysh -c "write memory"
# 또는
vtysh -c "copy running-config startup-config"

# 인터페이스 기본값으로 되돌리기
vtysh -c "configure terminal" -c "default interface eth1.141"

# 특정 데몬 재시작
rc-service frr restart          # 또는 systemctl restart frr
```

---

## 방화벽 (nftables)

- Alpine 기반 C4I 내부 방화벽. VLAN(eth1.131/132/133)을 소유하는 주체입니다.
- `nft`는 규칙 순서·handle 기반 삭제가 핵심입니다. 편집은 `nft -f`(파일 단위 원자적 적용)를 권장합니다.

### VLAN / NIC 관리 (방화벽이 VLAN을 소유)

```bash
# VLAN 하위 인터페이스 생성 (PoC 실사용)
ip link add link eth1 name eth1.131 type vlan id 131 2>/dev/null || true
ip link set eth1.131 up
ip addr replace 10.10.131.1/24 dev eth1.131

# VLAN 삭제
ip link del eth1.131

# 포워딩 활성화 (게이트웨이 역할)
sysctl -w net.ipv4.ip_forward=1
echo "net.ipv4.ip_forward=1" >> /etc/sysctl.conf

# 인터페이스 on/off
ip link set eth1.131 down
ip link set eth1.131 up
```

### 테이블 / 체인 생성 · 편집 · 삭제

```bash
# 테이블 생성
nft add table inet filter

# 체인 생성 (기본 정책 drop)
nft add chain inet filter input  '{ type filter hook input priority 0 ; policy drop ; }'
nft add chain inet filter forward '{ type filter hook forward priority 0 ; policy drop ; }'
nft add chain inet filter output '{ type filter hook output priority 0 ; policy accept ; }'

# 체인 정책만 변경 (accept ↔ drop)
nft add chain inet filter forward '{ type filter hook forward priority 0 ; policy accept ; }'

# 체인 삭제
nft flush chain inet filter forward
nft delete chain inet filter forward

# 테이블 삭제 (프로버 remove 정책 = 테이블 단위 삭제)
nft delete table inet filter
```

### ACL 규칙 (서브넷 간 차단 / 허용)

```bash
# 차단 (정책 match_criteria + action=drop)
nft add rule inet filter forward ip saddr 192.168.1.0/24 ip daddr 192.168.2.0/24 drop

# 허용
nft add rule inet filter forward ip saddr 192.168.1.0/24 ip daddr 192.168.2.0/24 accept

# 카운터 + 로그 동시 적용
nft add rule inet filter forward ip saddr 192.168.1.0/24 ip daddr 192.168.2.0/24 \
    counter log prefix '"NFT_DROP: "' level info drop

# NAT (PoC 실사용: oifname eth0 masquerade)
nft add rule ip nat postrouting oifname "eth0" masquerade

# 규칙 삭제는 handle 지정 필수
nft -a list chain inet filter forward        # 주석의 # handle N 확인
nft delete rule inet filter forward handle 5
```

### 설정 편집 / 갱신 (원자적 적용)

```bash
# 현재 설정 백업 → 편집 → 검증 → 원자적 교체
nft list ruleset > /etc/nftables.conf.bak
vi /etc/nftables.conf

nft -c -f /etc/nftables.conf        # 문법 검사(dry-run)
nft -f /etc/nftables.conf           # 적용 (실패 시 전체 롤백)

# 프로버가 쓰는 대화형 적용 경로
nft -i                              # 이후 명령을 세션에 누적 입력

# 전체 초기화
nft flush ruleset
```

### 로그 수집 연동

```bash
# 커널 로그(nft log 문) → syslog 수집기로 전달
nft add rule inet filter forward ip saddr 192.168.1.0/24 ip daddr 192.168.2.0/24 \
    log prefix '"NFT_DROP: "' level info drop

# 원격 syslog 전송 (busybox syslogd)
echo "*.* @10.0.0.254:514" >> /etc/syslog.conf
rc-service syslog restart

# conntrack 상태 수집
conntrack -L
conntrack -F        # 전체 플러시 (주의)
```

### 저장

```bash
nft list ruleset > /etc/nftables.conf
rc-service nftables save
rc-service nftables restart
```

---

## Linux VM (NIC 설정)

- Ubuntu 기반 엔드포인트(VDI-1/2, TOD-Cam, UAV, ATICS/KNCCS/AFCCS, PWS 등).
- 커널 즉시 반영(`ip`)과 영속 설정(netplan)을 구분해서 씁니다. 재부팅 후 유지가 필요하면 netplan을 사용합니다.

### NIC on / off (정책 command=on/off)

```bash
ip link set eth0 up
ip link set eth0 down
ip addr show eth0          # get 정책 대응
```

### IP / 게이트웨이 / DNS 설정 (netplan)

```bash
# 1) 설정 파일 작성
cat > /etc/netplan/99-sonar.yaml <<'EOF'
network:
  version: 2
  renderer: networkd
  ethernets:
    eth0:
      dhcp4: false
      addresses: [192.168.1.100/24]
      routes:
        - to: 0.0.0.0/0
          via: 192.168.1.254
      nameservers:
        addresses: [8.8.8.8, 1.1.1.1]
EOF

chmod 600 /etc/netplan/99-sonar.yaml
netplan generate
netplan apply                # 실제 적용
netplan try                  # 120초 내 미확인 시 자동 롤백 (원격 작업 시 권장)
```

### 설정 삭제 (DHCP 원복)

```bash
# 정책 command=remove, fallback_dhcp4=true 대응
rm -f /etc/netplan/99-sonar.yaml
cat > /etc/netplan/01-dhcp.yaml <<'EOF'
network:
  version: 2
  renderer: networkd
  ethernets:
    eth0:
      dhcp4: true
EOF
netplan apply
```

### 커널 레벨 임시 제어 (즉시 반영, 재부팅 시 소실)

```bash
ip addr replace 10.40.121.10/24 dev eth0
ip route replace default via 10.40.121.1 dev eth0
ip addr del 10.40.121.10/24 dev eth0
ip route del default via 10.40.121.1
```

### NIC 생성 / 삭제 (가상 인터페이스)

```bash
# dummy / veth / VLAN 인터페이스 생성
ip link add dummy0 type dummy
ip link add link eth0 name eth0.121 type vlan id 121
ip link set eth0.121 up

ip link del eth0.121
ip link del dummy0
```

### 방화벽 (netfilter)

```bash
# 단일 규칙
nft add rule inet filter input ip saddr 192.168.1.0/24 drop
# ufw 사용 시
ufw allow from 192.168.1.0/24
ufw deny from 192.168.2.0/24
ufw status numbered
ufw delete 3
```

### 로그 (journald → 원격)

```bash
# journald를 원격 syslog로 전달
cat > /etc/systemd/journald.conf.d/remote.conf <<'EOF'
[Journal]
ForwardToSyslog=yes
EOF
systemctl restart systemd-journald

# rsyslog 원격 전송
echo "*.* @10.0.0.254:514" > /etc/rsyslog.d/50-remote.conf
systemctl restart rsyslog

# 상태 확인
journalctl -u systemd-networkd -n 50
```

---

## Cisco

- IOS-XE 기반(8000v). 프로버는 guestshell에서 실행되며 `dohost`(인증 불필요 IPC)로 IOS CLI를 실행합니다.
- Cisco와 Arista는 명령어가 95% 이상 유사하므로 차이점만 주의합니다.

### 인터페이스 on / off / 초기화

```bash
Router#configure terminal
Router(config)#interface GigabitEthernet4
Router(config-if)#no shutdown          ! on
Router(config-if)#shutdown             ! off
Router(config-if)#exit

! 인터페이스 설정 초기화
Router(config)#default interface GigabitEthernet4

! 프로버(guestshell) 경로에서 실행하는 형태
guestshell$ dohost "configure terminal" && dohost "interface GigabitEthernet4" && dohost "no shutdown"
```

### VLAN 생성 / 삭제 (스위치 모듈)

```bash
Router(config)#vlan 10
Router(config-vlan)#name VLAN10
Router(config-vlan)#exit
Router(config)#no vlan 10              ! 삭제

! SVI (L3 게이트웨이)
Router(config)#interface Vlan10
Router(config-if)#ip address 10.0.8.1 255.255.255.0
Router(config-if)#no shutdown
```

### Access / Trunk 설정

```bash
! Access 포트
Router(config)#interface GigabitEthernet3
Router(config-if)#switchport mode access
Router(config-if)#switchport access vlan 10

! Trunk 포트
Router(config)#interface GigabitEthernet1
Router(config-if)#switchport trunk encapsulation dot1q
Router(config-if)#switchport mode trunk
Router(config-if)#switchport trunk allowed vlan 10,20

! Trunk 허용 VLAN 갱신 / 제거
Router(config-if)#switchport trunk allowed vlan add 30
Router(config-if)#switchport trunk allowed vlan remove 20
Router(config-if)#switchport trunk allowed vlan none

! 확인
Router#show interfaces switchport
```

### 서브인터페이스 (라우터 측 VLAN 종단)

```bash
Router(config)#interface GigabitEthernet2.141
Router(config-subif)#encapsulation dot1Q 141
Router(config-subif)#ip address 10.30.141.1 255.255.255.0
Router(config-subif)#no shutdown
Router(config)#no interface GigabitEthernet2.141
```

### OSPF 활성화 / 갱신 / 삭제

```bash
Router(config)#router ospf 1
Router(config-router)#router-id 1.1.1.1
Router(config-router)#network 192.168.1.0 0.0.0.255 area 0
Router(config-router)#passive-interface GigabitEthernet4
Router(config-router)#exit

! network 갱신
Router(config)#router ospf 1
Router(config-router)#no network 192.168.1.0 0.0.0.255 area 0
Router(config-router)#network 192.168.1.0 0.0.0.255 area 1

! 삭제
Router(config)#no router ospf 1
```

### 정적 라우트

```bash
Router(config)#ip route 10.200.0.0 255.255.0.0 192.168.1.254
Router(config)#no ip route 10.200.0.0 255.255.0.0 192.168.1.254
```

### ACL 생성 / 적용 / 삭제

```bash
Router(config)#ip access-list extended BLOCK_SUBNETA_TO_SUBNETB
Router(config-ext-nacl)#deny ip 192.168.1.0 0.0.0.255 192.168.2.0 0.0.0.255
Router(config-ext-nacl)#permit ip any any
Router(config-ext-nacl)#exit

! 적용
Router(config)#interface GigabitEthernet2
Router(config-if)#ip access-group BLOCK_SUBNETA_TO_SUBNETB in

! 해제 / 삭제
Router(config-if)#no ip access-group BLOCK_SUBNETA_TO_SUBNETB in
Router(config)#no ip access-list extended BLOCK_SUBNETA_TO_SUBNETB
```

### NAT

```bash
Router(config)#ip nat inside source list 10 interface GigabitEthernet1 overload
Router(config)#interface GigabitEthernet2
Router(config-if)#ip nat inside
Router(config)#interface GigabitEthernet1
Router(config-if)#ip nat outside
Router(config)#no ip nat inside source list 10 interface GigabitEthernet1 overload
```

### 로그

```bash
Router(config)#logging host 10.0.0.254
Router(config)#logging trap informational
Router(config)#no logging host 10.0.0.254
```

### 저장 / 초기화

```bash
Router#copy running-config startup-config
Router#show running-config
Router#write erase          ! NVRAM 초기화
Router#reload
```

---

## Arista

- vEOS 기반. 위 Cisco 절과 대부분 동일하며, 아래는 Arista 고유/차이점입니다.

### VLAN 생성 / 삭제

```bash
ARISTA(config)#vlan 10
ARISTA(config-vlan)#name VLAN10
ARISTA(config-vlan)#exit
ARISTA(config)#no vlan 10
```

### Access / Trunk / SVI

```bash
ARISTA(config)#interface Ethernet2
ARISTA(config-if)#switchport mode access
ARISTA(config-if)#switchport access vlan 8
ARISTA(config-if)#spanning-tree portfast

ARISTA(config)#interface Ethernet1
ARISTA(config-if)#switchport mode trunk
ARISTA(config-if)#switchport trunk allowed vlan 111,112
! 갱신 / 제거
ARISTA(config-if)#switchport trunk allowed vlan add 141
ARISTA(config-if)#switchport trunk allowed vlan remove 112

! SVI (PoC 실사용 형태)
ARISTA(config)#interface Vlan8
ARISTA(config-if)#ip address 10.0.8.1/24
ARISTA(config-if)#no shutdown

! 확인
ARISTA#show vlan brief
ARISTA#show ip interface brief
```

### IP Routing (Inter-VLAN) 활성 / 비활성

```bash
ARISTA(config)#ip routing
ARISTA(config)#no ip routing

! VRF 단위
ARISTA(config)#ip routing vrf MGMT
ARISTA(config)#no ip routing vrf MGMT
```

### 정적 라우트

```bash
ARISTA(config)#ip route 0.0.0.0/0 172.18.10.1
ARISTA(config)#ip route vrf MGMT 0.0.0.0/0 10.20.0.3
ARISTA(config)#no ip route 0.0.0.0/0 172.18.10.1
```

### ACL 생성 / 적용 / 삭제

```bash
ARISTA(config)#ip access-list standard MGMT_ACL
ARISTA(config-std-acl)#10 permit 10.20.0.0/24
ARISTA(config-std-acl)#40 deny any

ARISTA(config)#management api http-commands
ARISTA(config-mgmt-api)#vrf MGMT
ARISTA(config-mgmt-api-vrf)#ip access-group MGMT_ACL
ARISTA(config-mgmt-api-vrf)#no ip access-group MGMT_ACL
ARISTA(config)#no ip access-list standard MGMT_ACL
```

### OSPF

```bash
ARISTA(config)#router ospf 1
ARISTA(config-router-ospf)#router-id 10.255.255.4
ARISTA(config-router-ospf)#network 10.99.10.0/24 area 0
ARISTA(config-router-ospf)#passive-interface Ethernet7
ARISTA(config-router-ospf)#no network 10.99.10.0/24 area 0
ARISTA(config)#no router ospf 1
```

### 로그 / 저장

```bash
ARISTA(config)#logging host 10.0.0.254
ARISTA(config)#logging trap informational

ARISTA#write memory                      ! 또는 copy running-config startup-config
ARISTA#show running-config
ARISTA#write erase
ARISTA#reload
```

---

## 구현 현황 및 주의사항

`SonarValidator_Prober/management_service.cpp`의 `Apply*Policy` 기준 현재 적용 범위입니다.

| 정책 함수 | 대상 | 구현 상태 | 비고 |
|---|---|---|---|
| `ApplyOpenVSwitchPolicy` | OVS | 부분 | `on/off/create/remove/get` — VLAN tag, ACL(`ovs-ofctl`), 라우트, IP |
| `ApplyAristaSwitchPolicy` | Arista vEOS | 부분 | `on/off/create/get` — `FastCli` 세션 사용, trunk/서브넷 미구현 |
| `ApplyCiscoSwitchPolicy` | Cisco 스위치 | 미지원 | 8000v(라우터)만 지원, Catalyst 계열은 추후 |
| `ApplyCiscoRouterPolicy` | Cisco IOS-XE | 부분 | `dohost` 경유, OSPF/인터페이스/로그/라우트 삭제 |
| `ApplyFrrRouterPolicy` | FRR | 부분 | `vtysh` 세션, OSPF/인터페이스/get/라우트 삭제 |
| `ApplyNftablesPolicy` | nftables | 부분 | 테이블·체인·규칙 생성, `remove`는 테이블 단위 삭제 |
| `ApplyVmPolicy` | Ubuntu VM | 부분 | `on/off/get`만 지원, netplan 영속 설정은 미연결 |

### 주의사항

- **`remove` 의 파괴 범위**: nftables `remove` 는 규칙이 아니라 **테이블 전체**를 삭제합니다.
  규칙 단위 삭제가 필요하면 handle 기반 삭제를 정책에 추가해야 합니다.
- **OVS ACL 삭제**: 현재 `remove`는 `del-flows <적용 인터페이스>` 로 해당 포트의 flow를 전부 지웁니다.
  조건부 삭제(`del-flows br0 "ip,nw_src=..."`)로 세분화가 필요합니다.
- **VM 영속성**: `ApplyVmPolicy` 는 커널 임시 설정만 수행하므로 재부팅 시 소실됩니다.
  netplan 백엔드(`config_backend`) 처리를 연결해야 합니다.
- **VLAN 소유권**: VLAN은 스위치(OVS)와 방화벽(nftables 노드)이 소유하며, FRR 라우터는 sub-interface만
  인지합니다. 라우터 쪽에 VLAN 상세를 내려보내지 않아야 내부망 구조 노출을 막을 수 있습니다.
- **자동 수집 설정의 재편집**: 초기 스캔으로 수집한 설정은 프로젝트 생성 후 편집 가능해야 하므로,
  모든 제어 명령은 "현재 값 조회 → 변경 → 검증" 순서로 수행하고 결과를 서버에 `ReportPolicyApplied` 로 보고합니다.