#include "switch.hpp"
#include <cassert>

int main()
{

Switch demoSwitch;
// switch name is hostname

demoSwitch.setName("DMZ-Network-Switch");




// result of "DMZ-Network-Switch:/etc/switch# ovs-vsctl show"
    std::string Test_topology = R"(

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


}