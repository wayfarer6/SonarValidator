#!/usr/bin/env python3
"""Generate the markdown deliverables required by README.md.

Outputs (inside PoC용 네트워크 구성하기/):
  - 네트워크_구성_요약.md   : addressing plan, per-port IPs, trunks, plane split
  - <Device>.md             : exact commands applied to each device
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from plan import PLAN, PORTS

BASE = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

DEVICE_ORDER = [
    "Switch-0", "Switch-1", "Switch-2", "Switch-3", "Switch-4",
    "Gateway-Router", "C4I-Network-Router", "Survillance-Network-Router",
    "VDI-Router", "DMZ-Router", "Firewall",
    "Public-Web-Server", "ATICS", "KNCCS", "AFCCS",
    "TOD-Cam", "UAV", "VDI-1", "VDI-2",
]

DEVICE_DESC = {
    "Switch-0": "Core 스위치 (Alpine + OpenVSwitch) — 모든 라우터/방화벽 업링크, VLAN 10",
    "Switch-1": "Surveillance 존 액세스 스위치 (VLAN 111/112)",
    "Switch-2": "VDI 존 액세스 스위치 (VLAN 121/122)",
    "Switch-3": "Confidential(C4I) 존 액세스 스위치 (VLAN 131/132/133)",
    "Switch-4": "Open(DMZ) 존 액세스 스위치 (VLAN 141)",
    "Gateway-Router": "인터넷 게이트웨이 라우터 (Alpine + FRR, OSPF ASBR/default originate)",
    "C4I-Network-Router": "C4I 존 라우터 — Firewall과 OSPF 경계, static redistribute",
    "Survillance-Network-Router": "감시(Surveillance) 존 라우터",
    "VDI-Router": "VDI 존 라우터",
    "DMZ-Router": "DMZ(Open) 존 라우터",
    "Firewall": "C4I 내부 방화벽 (Alpine, 라우팅 게이트웨이 역할 — OSPF 미참여)",
    "Public-Web-Server": "DMZ 공개 웹 서버 (Ubuntu 24.10)",
    "ATICS": "C4I 서버 (Ubuntu 24.10)",
    "KNCCS": "C4I 서버 (Ubuntu 24.10)",
    "AFCCS": "C4I 서버 (Ubuntu 24.10)",
    "TOD-Cam": "감시 카메라 호스트 (docker)",
    "UAV": "무인기 호스트 (docker)",
    "VDI-1": "VDI 데스크톱 1 (docker)",
    "VDI-2": "VDI 데스크톱 2 (docker)",
}


def render_cmds(dev):
    kind, items = PLAN[dev]
    lines = []
    for it in items:
        if isinstance(it, str):
            lines.append(it)
        elif it[0] == "block":
            lines.extend(it[1])
    return "\n".join(lines)


# ------------------------------------------------------------------ per-device md
for dev in DEVICE_ORDER:
    body = render_cmds(dev)
    md = f"""# {dev} — 적용 명령어

- {DEVICE_DESC[dev]}
- 콘솔: `telnet localhost:{PORTS[dev]}`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

```sh
{body}
```
"""
    with open(os.path.join(BASE, f"{dev}.md"), "w", encoding="utf-8") as f:
        f.write(md)
    print("wrote", dev + ".md")

# ------------------------------------------------------------------ summary md
summary = """# PoC 네트워크 구성 요약

GNS3 프로젝트 `D-AI-PBL-PoC-Network`에 실제 적용·검증된 구성입니다.
기기별 적용 명령어는 이 폴더의 `<장비명>.md` 파일을 참고하세요.

## 1. 존(Zone) 구성

| 존 | 보안 등급 | 구성 요소 | 대역 |
|---|---|---|---|
| Confidential (C4I) | 기밀 | C4I-Network-Router → Firewall → Switch-3 → ATICS/KNCCS/AFCCS | 10.10.131~133.0/24 |
| Sensitive (감시/VDI) | 민감 | Survillance-Network-Router → Switch-1 → TOD-Cam/UAV, VDI-Router → Switch-2 → VDI-1/2 | 10.20.111~112.0/24, 10.40.121~122.0/24 |
| Open (DMZ) | 공개 | DMZ-Router → Switch-4 → Public-Web-Server | 10.30.141.0/24 |
| Core (백본) | - | Gateway-Router + Switch-0 (VLAN 10) | 10.99.10.0/24 |
| Management (제어평면) | - | Hub1 경유, OSPF 미참여 | 172.16.255.0/24 |

## 2. IP 대역 / 서브넷 계획

| 용도 | 네트워크 | 마스크 | 게이트웨이 | 비고 |
|---|---|---|---|---|
| Core 백본 (VLAN 10) | 10.99.10.0/24 | 255.255.255.0 | - | OSPF area 0 브로드캐스트 세그먼트 |
| Loopback (router-id) | 10.255.255.1/3/4/5/6 /32 | 255.255.255.255 | - | GW/DMZ/C4I/Surv/VDI |
| C4I↔Firewall P2P | 10.99.143.0/24 | 255.255.255.0 | - | OSPF area 0 |
| VLAN 111 TOD-Cam | 10.20.111.0/24 | 255.255.255.0 | 10.20.111.1 (Surv eth0.111) | |
| VLAN 112 UAV | 10.20.112.0/24 | 255.255.255.0 | 10.20.112.1 (Surv eth0.112) | |
| VLAN 121 VDI-1 | 10.40.121.0/24 | 255.255.255.0 | 10.40.121.1 (VDI-R eth1.121) | |
| VLAN 122 VDI-2 | 10.40.122.0/24 | 255.255.255.0 | 10.40.122.1 (VDI-R eth1.122) | |
| VLAN 131 ATICS | 10.10.131.0/24 | 255.255.255.0 | 10.10.131.1 (FW eth1.131) | |
| VLAN 132 KNCCS | 10.10.132.0/24 | 255.255.255.0 | 10.10.132.1 (FW eth1.132) | |
| VLAN 133 AFCCS | 10.10.133.0/24 | 255.255.255.0 | 10.10.133.1 (FW eth1.133) | |
| VLAN 141 PWS | 10.30.141.0/24 | 255.255.255.0 | 10.30.141.1 (DMZ eth1.141) | |
| Management (Hub1) | 172.16.255.0/24 | 255.255.255.0 | - | 제어평면 전용, OSPF passive |
| Internet (NAT) | 192.168.122.0/24 | 255.255.255.0 | 192.168.122.1 (host virbr0) | GW eth0 = .10 |

C4I 존 집계: Firewall 뒤 3개 VLAN(131/132/133)은 C4I-R에서
`ip route 10.10.128.0/21 10.99.143.2` (FRR static) + `redistribute static`으로
OSPF에 Type-5 LSA로 광고됩니다.

## 3. 포트별 IP 할당

### 라우터 / 방화벽

| 장비 | 인터페이스 | IP/마스크 | 연결 대상 | 역할 |
|---|---|---|---|---|
| Gateway-Router | eth0 | 192.168.122.10/24 | Internet(NAT) | WAN, default via .1, MASQUERADE |
| Gateway-Router | eth1 | 10.99.10.1/24 | Switch-0 eth0 | Core VLAN 10, OSPF DR |
| Gateway-Router | eth7 | 172.16.255.1/24 | Hub1 e1 | Management (passive) |
| Gateway-Router | lo | 10.255.255.1/32 | - | router-id |
| DMZ-Router | eth0 | 10.99.10.3/24 | Switch-0 eth4 | Core VLAN 10 |
| DMZ-Router | eth1 | - (trunk uplink) | Switch-4 eth0 | VLAN 141 trunk |
| DMZ-Router | eth1.141 | 10.30.141.1/24 | PWS 존 GW | |
| DMZ-Router | eth7 | 172.16.255.3/24 | Hub1 e11 | Management (passive) |
| DMZ-Router | lo | 10.255.255.3/32 | - | router-id |
| C4I-Network-Router | eth0 | 10.99.10.4/24 | Switch-0 eth3 | Core VLAN 10 |
| C4I-Network-Router | eth1 | 10.99.143.1/24 | Firewall eth0 | FW 경계, ASBR |
| C4I-Network-Router | eth7 | 172.16.255.4/24 | Hub1 e7 | Management (passive) |
| C4I-Network-Router | lo | 10.255.255.4/32 | - | router-id |
| Survillance-Network-Router | eth1 | 10.99.10.5/24 | Switch-0 eth1 | Core VLAN 10 |
| Survillance-Network-Router | eth0 | - (trunk uplink) | Switch-1 eth0 | VLAN 111,112 trunk |
| Survillance-Network-Router | eth0.111 | 10.20.111.1/24 | TOD-Cam 존 GW | |
| Survillance-Network-Router | eth0.112 | 10.20.112.1/24 | UAV 존 GW | |
| Survillance-Network-Router | eth2 | 172.16.255.5/24 | Hub1 e3 | Management (passive) |
| Survillance-Network-Router | lo | 10.255.255.5/32 | - | router-id |
| VDI-Router | eth0 | 10.99.10.6/24 | Switch-0 eth2 | Core VLAN 10 |
| VDI-Router | eth1 | - (trunk uplink) | Switch-2 eth0 | VLAN 121,122 trunk |
| VDI-Router | eth1.121 | 10.40.121.1/24 | VDI-1 존 GW | |
| VDI-Router | eth1.122 | 10.40.122.1/24 | VDI-2 존 GW | |
| VDI-Router | eth2 | 172.16.255.6/24 | Hub1 e5 | Management (passive) |
| VDI-Router | lo | 10.255.255.6/32 | - | router-id |
| Firewall | eth0 | 10.99.143.2/24 | C4I-R eth1 | 업링크, default via 10.99.143.1 |
| Firewall | eth1 | - (trunk) | Switch-3 eth0 | VLAN 131,132,133 trunk |
| Firewall | eth1.131 | 10.10.131.1/24 | ATICS 존 GW | |
| Firewall | eth1.132 | 10.10.132.1/24 | KNCCS 존 GW | |
| Firewall | eth1.133 | 10.10.133.1/24 | AFCCS 존 GW | |
| Firewall | eth4 | 172.16.255.2/24 | Hub1 e8 | Management |

### 스위치 (OpenVSwitch br0) — L2 전용, 포트별 VLAN

| 스위치 | 포트 | VLAN | 연결 대상 |
|---|---|---|---|
| Switch-0 | eth0 | tag=10 | Gateway-Router eth1 |
| Switch-0 | eth1 | tag=10 | Survillance-R eth1 |
| Switch-0 | eth2 | tag=10 | VDI-Router eth0 |
| Switch-0 | eth3 | tag=10 | C4I-R eth0 |
| Switch-0 | eth4 | tag=10 | DMZ-Router eth0 |
| Switch-0 | eth11 | (untagged mgmt) | Hub1 e2 — 제어평면 |
| Switch-1 | eth0 | trunk 111,112 | Survillance-R eth0 |
| Switch-1 | eth1 | tag=111 | TOD-Cam |
| Switch-1 | eth2 | tag=112 | UAV |
| Switch-1 | eth11 | (untagged mgmt) | Hub1 e4 |
| Switch-2 | eth0 | trunk 121,122 | VDI-Router eth1 |
| Switch-2 | eth1 | tag=121 | VDI-1 |
| Switch-2 | eth2 | tag=122 | VDI-2 |
| Switch-3 | eth0 | trunk 131,132,133 | Firewall eth1 |
| Switch-3 | eth1 | tag=131 | ATICS |
| Switch-3 | eth2 | tag=132 | KNCCS |
| Switch-3 | eth3 | tag=133 | AFCCS |
| Switch-3 | eth11 | (untagged mgmt) | Hub1 e10 |
| Switch-4 | eth0 | trunk 141 | DMZ-Router eth1 |
| Switch-4 | eth1 | tag=141 | Public-Web-Server |
| Switch-4 | eth11 | (untagged mgmt) | Hub1 e12 |

## 4. 서버(엔드포인트) NIC 현황

| 서버 | NIC | IP/마스크 | 기본 게이트웨이 | VLAN | 존 |
|---|---|---|---|---|---|
| Public-Web-Server | ens3 | 10.30.141.10/24 | 10.30.141.1 | 141 | Open (DMZ) |
| ATICS | ens3 | 10.10.131.10/24 | 10.10.131.1 | 131 | Confidential |
| KNCCS | ens3 | 10.10.132.10/24 | 10.10.132.1 | 132 | Confidential |
| AFCCS | ens3 | 10.10.133.10/24 | 10.10.133.1 | 133 | Confidential |
| TOD-Cam | eth0 | 10.20.111.10/24 | 10.20.111.1 | 111 | Sensitive |
| UAV | eth0 | 10.20.112.10/24 | 10.20.112.1 | 112 | Sensitive |
| VDI-1 | eth0 | 10.40.121.10/24 | 10.40.121.1 | 121 | Sensitive |
| VDI-2 | eth0 | 10.40.122.10/24 | 10.40.122.1 | 122 | Sensitive |
| Management-Console | eth0 | 172.16.255.0/24 대역 (Hub1) | - | - | Management |
| Management-Console | eth1 | DHCP (NAT1 = host virbr0) | 192.168.122.1 | - | Internet |

## 5. VLAN Trunk 구성

- **Switch-1 eth0 ↔ Surv-R eth0**: trunk, 허용 VLAN `111,112` (OVS `trunks=111,112`)
- **Switch-2 eth0 ↔ VDI-R eth1**: trunk, 허용 VLAN `121,122`
- **Switch-3 eth0 ↔ Firewall eth1**: trunk, 허용 VLAN `131,132,133`
- **Switch-4 eth0 ↔ DMZ-R eth1**: trunk, 허용 VLAN `141`
- 라우터/방화벽 측에서는 802.1Q 서브인터페이스(`ethX.<vid>`)로 종단(terminates)
- Switch-0은 모든 포트가 단일 VLAN 10(Core)이므로 trunk 불필요 — 전부 `tag=10` 액세스

## 6. 제어평면 / 데이터평면 분리

- **데이터평면**: 위 VLAN/OSPF 구성 전체 (10.x 대역). Hub1과 무관하게 스위치 br0·라우터 eth0/1/2로만 흐름.
- **제어평면(Management)**: `172.16.255.0/24` — Hub1에만 연결된 전용 인터페이스
  (GW eth7=.1, FW eth4=.2, DMZ eth7=.3, C4I eth7=.4, Surv eth2=.5, VDI eth2=.6,
   각 스위치 eth11, Management-Console eth0).
  - 모든 라우터에서 mgmt 인터페이스는 OSPF `ip ospf passive` 처리 → 관리망이 라우팅 프로토콜/데이터 경로에 노출되지 않음.
  - Management-Console은 eth1(NAT1)로 인터넷/호스트 접근, eth0으로 전 장비 제어.

## 7. 라우팅 (OSPF)

- 전 라우터 area 0.0.0.0, router-id = loopback(10.255.255.x).
- Gateway-Router: `default-information originate always` + eth0 MASQUERADE → 전 존 인터넷 진출.
- C4I-Network-Router: ASBR — FRR static `10.10.128.0/21 → 10.99.143.2`를 `redistribute static`으로 광고.
- Firewall은 OSPF 미참여 (default route만 C4I-R 방향).
- 검증 결과: OSPF 이웃 5대 전부 Full, 전 구간 ping 0% 손실
  (intra-VLAN, inter-VLAN, inter-zone, Confidential↔Open/Sensitive, 인터넷 경로).

## 8. 콘솔 접속 정보

| Node | Console |
|---|---|
"""
for dev in DEVICE_ORDER:
    summary += f"| {dev} | `telnet localhost:{PORTS[dev]}` |\n"
summary += "| Hub1 / Internet / NAT1 | none (GNS3 내부 노드) |\n| Management-Console | `vnc localhost:5901` |\n"

with open(os.path.join(BASE, "네트워크_구성_요약.md"), "w", encoding="utf-8") as f:
    f.write(summary)
print("wrote 네트워크_구성_요약.md")
