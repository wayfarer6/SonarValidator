#ifndef SONAR_VALIDATOR_PROBER_SWITCH_HPP_
#define SONAR_VALIDATOR_PROBER_SWITCH_HPP_

#include <string>
#include <vector>
#include "components/device/switch/switch_interface/port_info.hpp"

// 스위치 브리지(브리지 이름 + 포트 목록)를 나타냅니다.
struct BridgeInfo {
    std::string name;
    std::vector<PortInfo> ports;
};

// 스위치 브리지/포트 토폴로지를 지원하는 벤더입니다.
enum class SwitchVendor {
    AristavEOS,
    OpenVSwitch
};

// 벤더별 토폴로지 출력을 파싱하는 인터페이스입니다.
class TopologyParser {
public:
    virtual ~TopologyParser() = default;
    virtual std::vector<BridgeInfo> parse(const std::string& raw_output) const = 0;
};







// 파싱된 스위치 브리지/포트 토폴로지를 보관하는 클래스입니다.
class Switch {
public:
    explicit Switch(const std::string& name = "");
    ~Switch();

    std::string getName() const { return name_; }
    void setName(const std::string& name);

    void loadTopology(const std::string& raw_output, SwitchVendor vendor);

    void parseOpenVSwitchTopology(const std::string& raw_output);
    void parseAristaTopology(const std::string& raw_output);

    const std::vector<BridgeInfo>& getBridges() const { return bridges_; }

private:
    std::string name_;
    std::vector<BridgeInfo> bridges_;
};

#endif  // SONAR_VALIDATOR_PROBER_SWITCH_HPP_
