#ifndef SONAR_VALIDATOR_PROBER_OFFLINE_EXPORT_HPP_
#define SONAR_VALIDATOR_PROBER_OFFLINE_EXPORT_HPP_

#include <filesystem>
#include <string>
#include <nlohmann/json.hpp>

namespace offline {
using Json = nlohmann::json;
inline constexpr const char* kSchemaName = "sonar.offline.snapshot";
inline constexpr int kSchemaVersion = 1;
struct ExportResult {
    bool saved = false;
    std::string path;
    std::string message;
};
Json BuildSnapshotDocument(const std::string& agent_id,
                           const std::string& agent_name,
                           const std::string& device_type,
                           const std::string& product,
                           const std::string& vendor,
                           const std::string& kernel,
                           const std::string& collected_at,
                           const std::string& reason,
                           const Json& payload);
ExportResult ExportSnapshot(const std::filesystem::path& target, const Json& document);
}
#endif
