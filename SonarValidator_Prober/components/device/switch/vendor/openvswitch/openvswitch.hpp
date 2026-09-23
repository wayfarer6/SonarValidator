#include <vector>

class OpenVSwitchTopologyParser : public TopologyParser {
public:
    std::vector<BridgeInfo> parse(const std::string& raw_output) const override;
};
