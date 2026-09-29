# -*- coding: utf-8 -*-
"""PoC network configuration plan (applied via telnet consoles).

Topology (from GNS3 project D-AI-PBL-PoC-Network):
  Switch-0 = core switch : GW:eth1, SURV:eth1, VDI-R:eth0, C4I-R:eth0, DMZ-R:eth0
  Switch-1 = surveillance access : SURV:eth0(trunk), TOD-Cam:eth1, UAV:eth2
  Switch-2 = VDI access          : VDI-R:eth1(trunk), VDI-1:eth1, VDI-2:eth2
  Switch-3 = C4I access          : FW:eth1(trunk), ATICS:eth1, KNCCS:eth2, AFCCS:eth3
  Switch-4 = DMZ access          : DMZ-R:eth1(trunk), PWS:eth1
  Firewall : eth0<->C4I-R:eth1, eth1<->Switch-3, eth4<->Hub1(mgmt)
  Gateway  : eth0<->Internet(virbr0), eth1<->Switch-0, eth7<->Hub1(mgmt)

Addressing:
  mgmt  172.16.255.0/24 (Hub1, not in OSPF)
  core  10.99.10.0/24   (VLAN 10 on Switch-0, OSPF area 0)
  fw-c4i 10.99.143.0/24 (OSPF area 0)
  lo    10.255.255.x/32 (OSPF router-id)
  zones: 10.10.x (C4I/Confidential), 10.20.x (Surveillance),
         10.30.x (DMZ/Public), 10.40.x (VDI)
  internet-side 192.168.122.0/24 (host virbr0)
"""

PORTS = {
    "Public-Web-Server": 5000, "Survillance-Network-Router": 5013,
    "Gateway-Router": 5015, "VDI-Router": 5017, "C4I-Network-Router": 5019,
    "ATICS": 5022, "KNCCS": 5024, "AFCCS": 5026, "TOD-Cam": 5028, "UAV": 5030,
    "VDI-1": 5032, "VDI-2": 5034, "Firewall": 5037, "DMZ-Router": 5040,
    "Switch-0": 5042, "Switch-1": 5044, "Switch-2": 5046, "Switch-3": 5048,
    "Switch-4": 5050,
}
LOGIN = {"Public-Web-Server": ("ubuntu", "ubuntu"), "ATICS": ("ubuntu", "ubuntu"),
         "KNCCS": ("ubuntu", "ubuntu"), "AFCCS": ("ubuntu", "ubuntu")}


def ovs_switch(access, trunk_port, trunks):
    """access: list of (port, vlan); trunk_port: port; trunks: 'a,b,c'"""
    cmds = ["ovs-vsctl --if-exists del-br br0", "ovs-vsctl add-br br0"]
    for p, v in access:
        cmds.append(f"ip link set {p} up")
        cmds.append(f"ovs-vsctl add-port br0 {p} tag={v}")
    cmds.append(f"ip link set {trunk_port} up")
    cmds.append(f"ovs-vsctl add-port br0 {trunk_port} trunks={trunks}")
    cmds.append("ip link set br0 up")
    cmds.append("ovs-vsctl show")
    return cmds


def vlan_if(parent, vid, addr):
    ifn = f"{parent}.{vid}"
    return [
        f"ip link add link {parent} name {ifn} type vlan id {vid} 2>/dev/null || true",
        f"ip link set {ifn} up",
        f"ip addr replace {addr} dev {ifn}",
    ]


def ospf_block(router_id, networks, passive=(), extra=(), statics=()):
    """FRR 8.2: passive is set per-interface with 'ip ospf passive'.

    statics: FRR static routes (e.g. '10.10.128.0/21 10.99.143.2'). They must
    live in FRR (not just the kernel) or 'redistribute static' ignores them.
    """
    lines = ["vtysh <<'VEOF'", "configure terminal"]
    for s in statics:
        lines.append(f"ip route {s}")
    for p in passive:
        lines.append(f"interface {p}")
        lines.append(" ip ospf passive")
    lines += ["router ospf", f" ospf router-id {router_id}"]
    for n in networks:
        lines.append(f" network {n} area 0.0.0.0")
    for e in extra:
        lines.append(f" {e}")
    lines += ["end", "write memory", "VEOF"]
    return ("block", lines)


PLAN = {}

# ---------------------------------------------------------------- switches
PLAN["Switch-0"] = ("ovs", ovs_switch(
    [("eth0", 10), ("eth1", 10), ("eth2", 10), ("eth3", 10), ("eth4", 10)],
    None, None)[:-2] + ["ip link set br0 up", "ovs-vsctl show"])
# Switch-0 has no trunk; rebuild cleanly:
PLAN["Switch-0"] = ("ovs", [
    "ovs-vsctl --if-exists del-br br0",
    "ovs-vsctl add-br br0",
    "ip link set eth0 up", "ovs-vsctl add-port br0 eth0 tag=10",
    "ip link set eth1 up", "ovs-vsctl add-port br0 eth1 tag=10",
    "ip link set eth2 up", "ovs-vsctl add-port br0 eth2 tag=10",
    "ip link set eth3 up", "ovs-vsctl add-port br0 eth3 tag=10",
    "ip link set eth4 up", "ovs-vsctl add-port br0 eth4 tag=10",
    "ip link set br0 up",
    "ovs-vsctl show",
])
PLAN["Switch-1"] = ("ovs", ovs_switch(
    [("eth1", 111), ("eth2", 112)], "eth0", "111,112"))
PLAN["Switch-2"] = ("ovs", ovs_switch(
    [("eth1", 121), ("eth2", 122)], "eth0", "121,122"))
PLAN["Switch-3"] = ("ovs", ovs_switch(
    [("eth1", 131), ("eth2", 132), ("eth3", 133)], "eth0", "131,132,133"))
PLAN["Switch-4"] = ("ovs", ovs_switch(
    [("eth1", 141)], "eth0", "141"))

# ---------------------------------------------------------------- routers
PLAN["Gateway-Router"] = ("frr", [
    "sysctl -w net.ipv4.ip_forward=1",
    "ip link set lo up", "ip addr replace 10.255.255.1/32 dev lo",
    "ip link set eth0 up", "ip addr replace 192.168.122.10/24 dev eth0",
    "ip link set eth1 up", "ip addr replace 10.99.10.1/24 dev eth1",
    "ip link set eth7 up", "ip addr replace 172.16.255.1/24 dev eth7",
    "ip route replace default via 192.168.122.1 dev eth0",
    "iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE 2>/dev/null || true",
    ospf_block("10.255.255.1",
               ["10.255.255.1/32", "10.99.10.0/24"],
               passive=["eth0", "eth7"],
               extra=["default-information originate always"]),
])

PLAN["Survillance-Network-Router"] = ("frr", [
    "sysctl -w net.ipv4.ip_forward=1",
    "ip link set lo up", "ip addr replace 10.255.255.5/32 dev lo",
    "ip link set eth2 up", "ip addr replace 172.16.255.5/24 dev eth2",
    "ip link set eth1 up", "ip addr replace 10.99.10.5/24 dev eth1",
    "ip link set eth0 up",
] + vlan_if("eth0", 111, "10.20.111.1/24") + vlan_if("eth0", 112, "10.20.112.1/24") + [
    ospf_block("10.255.255.5",
               ["10.255.255.5/32", "10.99.10.0/24",
                "10.20.111.0/24", "10.20.112.0/24"],
               passive=["eth0.111", "eth0.112", "eth2"]),
])

PLAN["VDI-Router"] = ("frr", [
    "sysctl -w net.ipv4.ip_forward=1",
    "ip link set lo up", "ip addr replace 10.255.255.6/32 dev lo",
    "ip link set eth2 up", "ip addr replace 172.16.255.6/24 dev eth2",
    "ip link set eth0 up", "ip addr replace 10.99.10.6/24 dev eth0",
    "ip link set eth1 up",
] + vlan_if("eth1", 121, "10.40.121.1/24") + vlan_if("eth1", 122, "10.40.122.1/24") + [
    ospf_block("10.255.255.6",
               ["10.255.255.6/32", "10.99.10.0/24",
                "10.40.121.0/24", "10.40.122.0/24"],
               passive=["eth1.121", "eth1.122", "eth2"]),
])

PLAN["C4I-Network-Router"] = ("frr", [
    "sysctl -w net.ipv4.ip_forward=1",
    "ip link set lo up", "ip addr replace 10.255.255.4/32 dev lo",
    "ip link set eth7 up", "ip addr replace 172.16.255.4/24 dev eth7",
    "ip link set eth0 up", "ip addr replace 10.99.10.4/24 dev eth0",
    "ip link set eth1 up", "ip addr replace 10.99.143.1/24 dev eth1",
    ospf_block("10.255.255.4",
               ["10.255.255.4/32", "10.99.10.0/24", "10.99.143.0/24"],
               passive=["eth7"],
               extra=["redistribute static"],
               statics=["10.10.128.0/21 10.99.143.2"]),
])

PLAN["DMZ-Router"] = ("frr", [
    "sysctl -w net.ipv4.ip_forward=1",
    "ip link set lo up", "ip addr replace 10.255.255.3/32 dev lo",
    "ip link set eth7 up", "ip addr replace 172.16.255.3/24 dev eth7",
    "ip link set eth0 up", "ip addr replace 10.99.10.3/24 dev eth0",
    "ip link set eth1 up",
] + vlan_if("eth1", 141, "10.30.141.1/24") + [
    ospf_block("10.255.255.3",
               ["10.255.255.3/32", "10.99.10.0/24", "10.30.141.0/24"],
               passive=["eth1.141", "eth7"]),
])

# ---------------------------------------------------------------- firewall
PLAN["Firewall"] = ("alpine", [
    "sysctl -w net.ipv4.ip_forward=1",
    "ip link set lo up",
    "ip link set eth4 up", "ip addr replace 172.16.255.2/24 dev eth4",
    "ip link set eth0 up", "ip addr replace 10.99.143.2/24 dev eth0",
    "ip link set eth1 up",
] + vlan_if("eth1", 131, "10.10.131.1/24") \
    + vlan_if("eth1", 132, "10.10.132.1/24") \
    + vlan_if("eth1", 133, "10.10.133.1/24") + [
    "ip route replace default via 10.99.143.1 dev eth0",
])

# ---------------------------------------------------------------- hosts
def netplan(addr, gw):
    return ("block", [
        "echo ubuntu | sudo -S -k true",
        "sudo sh -c 'cat > /etc/netplan/99-poc.yaml' <<'NEOF'",
        "network:",
        "  version: 2",
        "  renderer: networkd",
        "  ethernets:",
        "    ens3:",
        "      dhcp4: false",
        f"      addresses: [{addr}]",
        "      routes:",
        "        - to: default",
        f"          via: {gw}",
        "NEOF",
        "sudo chmod 600 /etc/netplan/99-poc.yaml",
        "sudo netplan apply",
        "ip -br addr show ens3",
        "ip route",
    ])

PLAN["AFCCS"] = ("ubuntu", [netplan("10.10.133.10/24", "10.10.133.1")])
PLAN["KNCCS"] = ("ubuntu", [netplan("10.10.132.10/24", "10.10.132.1")])
PLAN["ATICS"] = ("ubuntu", [netplan("10.10.131.10/24", "10.10.131.1")])
PLAN["Public-Web-Server"] = ("ubuntu", [netplan("10.30.141.10/24", "10.30.141.1")])

def host_ip(addr, gw):
    return [
        f"ip addr replace {addr} dev eth0",
        "ip link set eth0 up",
        f"ip route replace default via {gw} dev eth0",
        "ip -br addr show eth0",
    ]

PLAN["TOD-Cam"] = ("docker", host_ip("10.20.111.10/24", "10.20.111.1"))
PLAN["UAV"] = ("docker", host_ip("10.20.112.10/24", "10.20.112.1"))
PLAN["VDI-1"] = ("docker", host_ip("10.40.121.10/24", "10.40.121.1"))
PLAN["VDI-2"] = ("docker", host_ip("10.40.122.10/24", "10.40.122.1"))
