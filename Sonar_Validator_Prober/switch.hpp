#include <iostream>
#include "routing_table.hpp"


struct VLan {
public:
    VLan(int vlan_id) : vlan_id(vlan_id) {}
    int getVLANID() const { return vlan_id; }
private:
    int vlan_id;
};

struct Subnet {
public:
    Subnet(int subnet_id);

private:
    int subnet_id;

};

class Switch
{
public:
    Switch(const std::string& name) : name(name), routing_table();
    void addRoute(const std::string& destination, const std::string& next_hop);
    void addPort(const std::string& destination, const std::string& port);
    void printRoutes() const;
    void printPorts() const;
    void updateRoutingTable(const std::string& destination, const std::string& next_hop);
    void updatePort(const std::string& destination, const std::string& port);

private:
    std::string name;
    RoutingTable routing_table;
    std::map<std::string, Subnet> ports; // Map to store ports connected to each switch
};
