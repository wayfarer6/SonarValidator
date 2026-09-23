#include <iostream>
#include "../switch.hpp"

class CiscoTopologyParser : public TopologyParser {
public:
    std::vector<BridgeInfo> parse(const std::string& raw_output) const override;
};

