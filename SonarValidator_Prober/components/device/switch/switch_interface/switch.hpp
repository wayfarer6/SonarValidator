#ifndef SONAR_VALIDATOR_PROBER_SWITCH_HPP_
#define SONAR_VALIDATOR_PROBER_SWITCH_HPP_

#include <map>
#include <string>
#include <vector>
#include "components/device/router/routing_table/routing_table.hpp"
#include "components/backend_communication/network.hpp"
#include "components/device/switch/switch_interface/port_info.hpp"

// 스위치 포트 하나의 정보를 담는 구조체입니다.
//
// 공통 네트워크 요소는 components/network_object/ 로 분리했습니다.
//   NetworkInterface(추상) ← Port(L2 스위치 포트) / Nic(호스트·VM NIC)
// 포트 정보는 이제 port_info.hpp 의 PortInfo(Port 상속) 를 사용합니다.

// 스위치 브리지(브리지 이름 + 포트 목록)를 나타냅니다.
struct BridgeInfo {
    std::string name;
    std::vector<PortInfo> ports;
};

// 지원하는 스위치 벤더를 구분합니다.
//
// Cisco 는 이 랩에서 **8000v(IOS-XE 라우터)** 만 씁니다.
enum class SwitchVendor {
    CiscoIosXe,
    AristavEOS,
    OpenVSwitch
};

// 벤더별 토폴로지 출력을 파싱하는 인터페이스입니다.
class TopologyParser {
public:
    virtual ~TopologyParser() = default;
    virtual std::vector<BridgeInfo> parse(const std::string& raw_output) const = 0;
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
    void parseAristaTopology(const std::string& raw_output);

    const std::vector<BridgeInfo>& getBridges() const { return bridges_; }

private:
    std::string name_;
    RoutingTable routing_table_;
    std::map<std::string, Subnet> ports_; // Map to store ports connected to each switch
    std::vector<BridgeInfo> bridges_;
};

#endif  // SONAR_VALIDATOR_PROBER_SWITCH_HPP_
