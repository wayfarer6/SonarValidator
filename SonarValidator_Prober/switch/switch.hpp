#ifndef SONAR_VALIDATOR_PROBER_SWITCH_HPP_
#define SONAR_VALIDATOR_PROBER_SWITCH_HPP_

#include <map>
#include <string>
#include <vector>

#include "routing_table.hpp"
#include "network.hpp"

struct PortInfo {
    std::string name;
    std::string interface_name;
    std::vector<int> access_vlans;
    std::vector<int> trunk_vlans;
    bool is_internal = false;
};

struct BridgeInfo {
    std::string name;
    std::vector<PortInfo> ports;
};

enum class SwitchVendor {
    OpenVSwitch,
    CiscoCatalyst8000v,
};

class TopologyParser {
public:
    virtual ~TopologyParser() = default;
    virtual std::vector<BridgeInfo> parse(const std::string& raw_output) const = 0;
};

class OpenVSwitchTopologyParser : public TopologyParser {
public:
    std::vector<BridgeInfo> parse(const std::string& raw_output) const override;
};

class CiscoTopologyParser : public TopologyParser {
public:
    std::vector<BridgeInfo> parse(const std::string& raw_output) const override;
};

class Switch {
public:
    explicit Switch(const std::string& name = "");
    ~Switch();

    std::string getName() const { return name_; }
    void setName(const std::string& name);

    void loadTopology(const std::string& raw_output, SwitchVendor vendor);

    void addRoute(const std::string& destination, const std::string& next_hop);
    void addPort(const std::string& destination, const std::string& port);
    void printRoutes() const;
    void printPorts() const;
    void updateRoutingTable(const std::string& destination, const std::string& next_hop);
    void updatePort(const std::string& destination, const std::string& port);

    void parseCiscoSwitchTopology(const std::string& raw_output);
    void parseOpenVSwitchTopology(const std::string& raw_output);

    const std::vector<BridgeInfo>& getBridges() const { return bridges_; }

private:
    std::string name_;
    RoutingTable routing_table_;
    std::map<std::string, Subnet> ports_; // Map to store ports connected to each switch
    std::vector<BridgeInfo> bridges_;
};

#endif  // SONAR_VALIDATOR_PROBER_SWITCH_HPP_
