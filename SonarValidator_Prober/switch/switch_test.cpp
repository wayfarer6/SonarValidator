#include "switch.hpp"

#include <cassert>
#include <string>

const std::string Trim(const std::string& input)
{
    int start; 
    while(start < input.size() && std::isspace(static_cast<unsigned char>(input[start])) !=0) { ++start;} 

    int end = input.size();

    while(start <= end  && std::isspace(static_cast<unsigned char>(input[end])) !=0))
    {
        end--;
    }

    return input.substr(start,end);
}


int main()
{
    Switch demoSwitch;
    demoSwitch.setName("DMZ-Network-Switch");

    const std::string ovs_topology = R"(
5858432f-d303-47d6-8c38-762489c828e3
    Bridge br2
        datapath_type: netdev
        Port br2
            Interface br2
                type: internal
    Bridge br-dmz
        Port br-dmz
            Interface br-dmz
                type: internal
    Bridge br0
        datapath_type: netdev
        Port br0
            Interface br0
                type: internal
        Port eth1
            tag: 100
            Interface eth1
        Port eth10
            Interface eth10
        Port eth3
            tag: 120
            Interface eth3
        Port eth14
            Interface eth14
        Port eth7
            Interface eth7
        Port eth9
            Interface eth9
        Port eth4
            tag: 130
            Interface eth4
        Port eth6
            Interface eth6
        Port eth11
            Interface eth11
        Port eth2
            tag: 110
            Interface eth2
        Port eth15
            Interface eth15
        Port eth8
            Interface eth8
        Port eth5
            tag: 50
            Interface eth5
        Port eth0
            trunks: [100, 110, 120, 130]
            Interface eth0
        Port eth12
            Interface eth12
        Port eth13
            Interface eth13
    Bridge br3
        datapath_type: netdev
        Port br3
            Interface br3
                type: internal
    Bridge br1
        datapath_type: netdev
        Port br1
            Interface br1
                type: internal
)";

    demoSwitch.loadTopology(ovs_topology, SwitchVendor::OpenVSwitch);
    assert(demoSwitch.getBridges().size() == 5);

    const auto& ovs_bridges = demoSwitch.getBridges();
    bool found_br0 = false;
    bool found_eth0 = false;
    for (const auto& bridge : ovs_bridges) {
        if (bridge.name == "br0") {
            found_br0 = true;
            for (const auto& port : bridge.ports) {
                if (port.name == "eth0") {
                    found_eth0 = true;
                    assert(port.trunk_vlans.size() == 4);
                    assert(port.interface_name == "eth0");
                }
            }
        }
    }
    assert(found_br0);
    assert(found_eth0);


/*
ARISTA(config)#show running-config | grep Ethernet
*/

const std::string arista_ethernet_list = R"(
interface Ethernet1
interface Ethernet2
interface Ethernet3
interface Ethernet4
interface Ethernet5
interface Ethernet6
interface Ethernet7
interface Ethernet8
interface Ethernet9
interface Ethernet10
interface Ethernet11
interface Ethernet12 )";

/*
ARISTA(config)#show running-config | grep '^vlan [0-9]'
*/ 
const std::string vlan_list = R"(
vlan 8
vlan 9
vlan 99 // 한개가 공백자니 숫자나오기전에 잘라주면
)";


/*
ARISTA(config)#show ip interface brief
*/

const std::string ip_interface_brief = R"(
                                                                        Address
Interface       IP Address          Status      Protocol         MTU    Owner  
--------------- ------------------- ----------- ------------- --------- -------
Management1     unassigned          up          up              1500           
Vlan8           10.0.8.1/24         up          up              1500           
Vlan9           10.0.9.1/24         up          up              1500           
Vlan99          172.18.10.2/24      up          up              1500           
);


Switch aristavEosSwitch("ARISTA");
aristavEosSwitch.addPort("Ethernet1","Ethernet1");
aristavEosSwitch.addPort("Ethernet2","Ethernet2");
aristavEosSwitch.addPort("Ethernet3","Ethernet3");
aristavEosSwitch.addVlan(" "," ")






    /*
    const std::string cisco_topology = R"(
    interface GigabitEthernet1/0/1
        switchport mode trunk
        switchport trunk allowed vlan 100,110,120,130
    interface GigabitEthernet1/0/2
        switchport access vlan 50)";
    */

    //Switch ciscoSwitch("CAT9000V");
    //ciscoSwitch.loadTopology(cisco_topology, SwitchVendor::CiscoCatalyst9000v);
    //assert(ciscoSwitch.getBridges().size() == 1);
    //assert(ciscoSwitch.getBridges()[0].ports.size() == 2);


    


    return 0;
}