#ifndef SONAR_VALIDATOR_PROBER_FIREWALL_HPP_
#define SONAR_VALIDATOR_PROBER_FIREWALL_HPP_

#include <cstddef>
#include <string>
#include <vector>

// nftables 규칙 하나를 나타냅니다.
struct NftRule {
    std::string raw;               // 원본 문자열
    std::string statement;         // 매칭 이전 문장
    std::string match;             // 매칭 조건
    std::string action;            // accept/drop/reject/set
    std::string connection_state;  // ct state 값
};

// nftables 체인을 나타냅니다.
struct NftChain {
    std::string family;             // 테이블 패밀리(ip/ip6/inet 등)
    std::string name;
    std::string type;
    std::string hook;
    std::string priority;
    std::string policy;
    std::vector<NftRule> rules;
};

// nftables 테이블을 나타냅니다.
struct NftTable {
    std::string family;
    std::string name;
    std::vector<NftChain> chains;
};

// nftables 룰셋 출력을 파싱해 보관하는 클래스입니다.
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
