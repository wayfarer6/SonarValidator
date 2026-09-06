#ifndef SONAR_VALIDATOR_PROBER_SWITCH_HPP_
#define SONAR_VALIDATOR_PROBER_SWITCH_HPP_

#include <map>
#include <string>
#include <vector>

#include "../router/routing_table.hpp"
#include "../network.hpp"

// 스위치 포트 하나의 정보를 담는 구조체입니다.
struct PortInfo {
    std::string name;               // 포트 이름
    std::string interface_name;     // 실제 인터페이스 이름
    std::vector<int> access_vlans;  // access 모드 VLAN 목록
    std::vector<int> trunk_vlans;   // trunk 허용 VLAN 목록
    bool is_internal = false;       // 내부 포트 여부
};

// 스위치 브리지(브리지 이름 + 포트 목록)를 나타냅니다.
struct BridgeInfo {
    std::string name;
    std::vector<PortInfo> ports;
};

// 지원하는 스위치 벤더를 구분합니다.
enum class SwitchVendor {
    CiscoCatalyst9000v,
    AristavEOS,
    OpenVSwitch
};

// 벤더별 토폴로지 출력을 파싱하는 인터페이스입니다.
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

class AristaTopologyParser : public TopologyParser {
    public:
        std::vector<BridgeInfo> parse(const std::string& raw_output) const override;
};


// 스위치 토폴로지/포트/VLAN/라우팅 정보를 보관하는 클래스입니다.
class Switch {
public:
    explicit Switch(const std::string& name = "");
    ~Switch();

    std::string getName() const { return name_; }
    void setName(const std::string& name);

    void loadTopology(const std::string& raw_output, SwitchVendor vendor);

    void addRoute(const std::string& destination, const std::string& next_hop);
    void addPort(const std::string& destination, const std::string& port);
    void addVlan(const int subnet_id, const std::string& port); // subnet
    void printRoutes() const;
    void printPorts() const;
    void updateRoutingTable(const std::string& destination, const std::string& next_hop);
    void updatePort(const std::string& destination, const std::string& port);

    void parseCiscoSwitchTopology(const std::string& raw_output);
    void parseOpenVSwitchTopology(const std::string& raw_output);
    void pasreAristaTopology(const std::string& raw_output);

    const std::vector<BridgeInfo>& getBridges() const { return bridges_; }

private:
    std::string name_;
    RoutingTable routing_table_;
    std::map<std::string, Subnet> ports_; // Map to store ports connected to each switch
    std::vector<BridgeInfo> bridges_;
};

#endif  // SONAR_VALIDATOR_PROBER_SWITCH_HPP_
