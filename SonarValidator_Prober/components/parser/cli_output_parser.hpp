#ifndef SONAR_VALIDATOR_PROBER_PARSER_CLI_OUTPUT_PARSER_HPP_
#define SONAR_VALIDATOR_PROBER_PARSER_CLI_OUTPUT_PARSER_HPP_

#include <string>

#include <nlohmann/json.hpp>

#include "components/device/device_type.hpp"






















namespace cli_parser
{

// 대상 벤더. ProberConfig::GetProductName() 값을 그대로 쓴다.
enum class Vendor
{
    kOpenVSwitch,
    kFrr,
    kCisco,
    kArista,
    kNftables,
    kUbuntu,
    kUnknown
};

// ProberConfig::GetProductName() 문자열을 Vendor 로 변환합니다.
Vendor VendorFromProductName(const std::string& product_name);

// 문자열 이름(디버그/JSON 출력용)을 반환합니다.
std::string VendorName(Vendor vendor);

















nlohmann::json ParseNicStatus(const std::string& raw_output);

// 위와 동일하지만 `ip -br addr show` 출력만 파싱합니다.
nlohmann::json ParseNicBrief(const std::string& raw_output);








nlohmann::json ParseArpTable(const std::string& raw_output, Vendor vendor);












nlohmann::json ParseRouteStatus(const std::string& raw_output, Vendor vendor);







nlohmann::json ParseInterfaceStatus(const std::string& raw_output, Vendor vendor);





















nlohmann::json ParseOvsTopology(const std::string& raw_output);
nlohmann::json ParseSwitchVlan(const std::string& raw_output);
nlohmann::json ParseSwitchPorts(const std::string& raw_output);
nlohmann::json ParseRunningConfig(const std::string& raw_output);












nlohmann::json ParseFirewallRules(const std::string& raw_output);







nlohmann::json ParseQueryOutput(Vendor vendor,
                                const std::string& target,
                                const std::string& raw_output);

} // namespace cli_parser

#endif // SONAR_VALIDATOR_PROBER_PARSER_CLI_OUTPUT_PARSER_HPP_
