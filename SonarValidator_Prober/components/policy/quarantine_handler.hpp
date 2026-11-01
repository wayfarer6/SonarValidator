#ifndef SONAR_VALIDATOR_PROBER_QUARANTINE_HANDLER_HPP_
#define SONAR_VALIDATOR_PROBER_QUARANTINE_HANDLER_HPP_
#include <functional>
#include <string>
#include <vector>
#include <nlohmann/json.hpp>
class ManagementService;
class ProberConfig;
namespace quarantine {
using SubnetExecutor = std::function<bool(const std::string&)>;
using SubnetReader = std::function<std::string(const std::string&)>;
inline constexpr const char* kIsolate = "quarantine";
inline constexpr const char* kRelease = "release";
inline constexpr const char* kDefaultManagementPrefix = "172.16.255.0/24";
bool ApplySubnetTargets(const nlohmann::json& targets, const std::string& management,
                        const std::string& server, const SubnetExecutor& execute,
                        const SubnetReader& read, std::string& detail);
bool ReconcilePlan(const ProberConfig&, ManagementService&, const nlohmann::json&);
bool IsQuarantineCommand(const nlohmann::json&);
bool HandleCommand(const ProberConfig&, ManagementService&, const nlohmann::json&);
}
#endif
