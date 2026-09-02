#ifndef SONAR_VALIDATOR_PROBER_FIREWALL_HPP_
#define SONAR_VALIDATOR_PROBER_FIREWALL_HPP_

#include <cstddef>
#include <string>
#include <vector>

struct NftRule {
    std::string raw;
    std::string statement;
    std::string match;
    std::string action;
    std::string connection_state;
};

struct NftChain {
    std::string family;
    std::string name;
    std::string type;
    std::string hook;
    std::string priority;
    std::string policy;
    std::vector<NftRule> rules;
};

struct NftTable {
    std::string family;
    std::string name;
    std::vector<NftChain> chains;
};

class Firewall {
public:
    Firewall() = default;
    ~Firewall() = default;

    void parseNftablesRuleset(const std::string& raw_output);

    const std::vector<NftTable>& tables() const { return tables_; }
    std::size_t tableCount() const { return tables_.size(); }

private:
    std::vector<NftTable> tables_;
};

#endif  // SONAR_VALIDATOR_PROBER_FIREWALL_HPP_
