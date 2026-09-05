## Arista 스위치 제품군 Policy Design

* vlan 정책 디자인

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "vlan_id": ["vlan 99"],
    "port":["Ethernet 1"],
    "ip_address":["10.0.0.1"],
    "subnet_mask":["255.255.255.0"]
}

```

* port 정책 디자인 (포트 on // off)

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "enable":["true"],
    "port":["Ethernet 1"]
}

```

* vlan Trunk 정책 디자인 (포트 on // off)

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "vlan_id":"10",
    "command":["on"],
    "allow_trunk":["true"],
    "interface":["Ethernet 1"]
}

```

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "vlan_id":"10",
    "command":["off"],
    "allow_trunk":["true"],
    "interface":["Ethernet 1"]
}

```

* vlan 생성

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "command":["create"],
    "subnet_id": "subnetA",
    "ip_address":"192.168.1.1",
    "subnet_mask":"255.255.255.0",
    "vlan_id":"10",
    "interface":["Ethernet 1"]
}

```

* vlan 삭제

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "command":["remove"],
    "subnet_id": "subnetA",
    "vlan_id":"10"
}

```

* Subnet 생성 // 편집

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "command":["create"], 
    "subnet_id": "subnetA",
    "ip_address":"192.168.1.1",
    "subnet_mask":"255.255.255.0"
}

```

* Subnet 삭제

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "command":["remove"], 
    "subnet_id": "subnetA"
}

```

* 서브넷 간 라우팅(Inter-VLAN Routing) 활성화

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "command":["create"],
    "ip_routing":["true"]
}

```

* 정적 라우팅(Static Routing) 설정

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "command":["create"],
    "destination_prefix":["10.200.0.0"],
    "subnet_mask":["255.255.0.0"],
    "next_hop":["192.168.1.254"],
    "distance":["1"]
}

```

* 서브넷 간 통신 제어 (ACL - Access Control List) 정책 생성

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "command":["create"],
    "acl_name":["BLOCK_SUBNETA_TO_SUBNETB"],
    "direction":["in"],
    "source_subnet":["192.168.1.0/24"],
    "destination_subnet":["192.168.2.0/24"],
    "action":["deny"],
    "protocol":["ip"],
    "applied_interface":["Vlan10"]
}

```

* 서브넷 간 통신 제어 (ACL) 정책 삭제

```json
{
    "vendor": ["Arista"],
    "product": ["Arista vEOS"],
    "model" : ["Arista vEOS"],
    "command":["remove"],
    "acl_name":["BLOCK_SUBNETA_TO_SUBNETB"],
    "applied_interface":["Vlan10"]
}

```

---

* 정책의 예시

```markdown

- vlan 10, 20, 30 정책 생성
- vlan A를 10번 포트에 할당
- L3 스위칭을 위한 글로벌 IP Routing 활성화
- Subnet A(Vlan 10: 192.168.1.0/24)와 Subnet B(Vlan 20: 192.168.2.0/24) 생성
- Subnet A에서 Subnet B로 향하는 트래픽을 차단하는 ACL 정책(BLOCK_SUBNETA_TO_SUBNETB)을 Vlan 10 인터페이스에 적용
- 외부 대역 통신을 위한 정적 라우팅(Next-hop: 192.168.1.254) 추가

```

- json 변환

```json
{
    "vendor": "Arista",
    "product": "Arista vEOS",
    "model": "Arista vEOS",
    "policies": [
        {
            "policy_type": "vlan_creation",
            "command": "create",
            "vlan_ids": ["10", "20", "30"]
        },
        {
            "policy_type": "vlan_port_assignment",
            "vlan_id": "vlan 10",
            "port": "Ethernet 10"
        },
        {
            "policy_type": "ip_routing",
            "command": "create",
            "ip_routing": "true"
        },
        {
            "policy_type": "subnet_creation",
            "command": "create",
            "subnets": [
                {
                    "subnet_id": "subnetA",
                    "vlan_id": "10",
                    "ip_address": "192.168.1.1",
                    "subnet_mask": "255.255.255.0"
                },
                {
                    "subnet_id": "subnetB",
                    "vlan_id": "20",
                    "ip_address": "192.168.2.1",
                    "subnet_mask": "255.255.255.0"
                }
            ]
        },
        {
            "policy_type": "acl",
            "command": "create",
            "acl_name": "BLOCK_SUBNETA_TO_SUBNETB",
            "direction": "in",
            "source_subnet": "192.168.1.0/24",
            "destination_subnet": "192.168.2.0/24",
            "action": "deny",
            "protocol": "ip",
            "applied_interface": "Vlan10"
        },
        {
            "policy_type": "static_routing",
            "command": "create",
            "destination_prefix": "0.0.0.0",
            "subnet_mask": "0.0.0.0",
            "next_hop": "192.168.1.254",
            "distance": "1"
        }
    ]
}
```

## OpenVSwitch용

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "vlan_id": ["vlan 99"],
    "port": ["eth0"],
    "ip_address": ["10.0.0.1"],
    "subnet_mask": ["255.255.255.0"]
}

```

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "enable": ["true"],
    "port": ["eth0"]
}

```

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "vlan_id": "10",
    "command": ["on"],
    "allow_trunk": ["true"],
    "interface": ["eth0"]
}

```

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "vlan_id": "10",
    "command": ["off"],
    "allow_trunk": ["true"],
    "interface": ["eth0"]
}

```

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "command": ["create"],
    "subnet_id": "subnetA",
    "ip_address": "192.168.1.1",
    "subnet_mask": "255.255.255.0",
    "vlan_id": "10",
    "interface": ["eth0"]
}

```

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "command": ["remove"],
    "subnet_id": "subnetA",
    "vlan_id": "10"
}

```

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "command": ["create"],
    "subnet_id": "subnetA",
    "ip_address": "192.168.1.1",
    "subnet_mask": "255.255.255.0"
}

```

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "command": ["remove"],
    "subnet_id": "subnetA"
}

```

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "command": ["create"],
    "ip_routing": ["true"]
}

```

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "command": ["create"],
    "destination_prefix": ["10.200.0.0"],
    "subnet_mask": ["255.255.0.0"],
    "next_hop": ["192.168.1.254"],
    "distance": ["1"]
}

```

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "command": ["create"],
    "acl_name": ["BLOCK_SUBNETA_TO_SUBNETB"],
    "direction": ["in"],
    "source_subnet": ["192.168.1.0/24"],
    "destination_subnet": ["192.168.2.0/24"],
    "action": ["drop"],
    "protocol": ["ip"],
    "applied_interface": ["br0"]
}

```

```json
{
    "vendor": ["Open vSwitch"],
    "product": ["OVS"],
    "model": ["Open vSwitch"],
    "command": ["remove"],
    "acl_name": ["BLOCK_SUBNETA_TO_SUBNETB"],
    "applied_interface": ["br0"]
}

```

```json 정책
{
    "vendor": "Open vSwitch",
    "product": "OVS",
    "model": "Open vSwitch",
    "policies": [
        {
            "policy_type": "bridge_creation",
            "command": "create",
            "bridge_name": "br0"
        },
        {
            "policy_type": "port_vlan_assignment",
            "command": "create",
            "bridge_name": "br0",
            "port": "eth0",
            "vlan_id": "10"
        },
        {
            "policy_type": "vlan_creation",
            "command": "create",
            "vlan_ids": ["10", "20", "30"]
        },
        {
            "policy_type": "ip_routing",
            "command": "create",
            "ip_routing": "true"
        },
        {
            "policy_type": "subnet_creation",
            "command": "create",
            "subnets": [
                {
                    "subnet_id": "subnetA",
                    "vlan_id": "10",
                    "ip_address": "192.168.1.1",
                    "subnet_mask": "255.255.255.0"
                },
                {
                    "subnet_id": "subnetB",
                    "vlan_id": "20",
                    "ip_address": "192.168.2.1",
                    "subnet_mask": "255.255.255.0"
                }
            ]
        },
        {
            "policy_type": "openflow_acl",
            "command": "create",
            "table": "0",
            "priority": "100",
            "match": {
                "dl_type": "0x0800",
                "nw_src": "192.168.1.0/24",
                "nw_dst": "192.168.2.0/24"
            },
            "action": "drop"
        },
        {
            "policy_type": "static_routing",
            "command": "create",
            "destination_prefix": "0.0.0.0",
            "subnet_mask": "0.0.0.0",
            "next_hop": "192.168.1.254"
        }
    ]
}

```