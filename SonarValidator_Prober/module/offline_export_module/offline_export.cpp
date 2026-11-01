#include "module/offline_export_module/offline_export.hpp"
#include <chrono>
#include <ctime>
#include <iomanip>
#include <sstream>

#include "utils/atomic_json_file.hpp"

namespace offline {
namespace {
std::string CurrentUtcTimestamp() {
    const std::time_t now = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
    std::tm utc{};
    gmtime_r(&now, &utc);
    std::ostringstream output;
    output << std::put_time(&utc, "%Y-%m-%dT%H:%M:%SZ");
    return output.str();
}
}

    Json BuildSnapshotDocument(const std::string &agent_id,
                               const std::string &agent_name,
                               const std::string &device_type,
                               const std::string &product,
                               const std::string &vendor,
                               const std::string &kernel,
                               const std::string &collected_at,
                               const std::string &reason,
                               const Json &payload)
    {
        Json document;
        document["schema"] = kSchemaName;
        document["schema_version"] = kSchemaVersion;
        document["agent_id"] = agent_id;
        document["agent_name"] = agent_name;
        document["device_type"] = device_type;
        document["product"] = product;
        document["vendor"] = vendor;
        document["kernel"] = kernel;
        document["collected_at"] = collected_at;
        document["exported_at"] = CurrentUtcTimestamp();
        document["reason"] = reason;
        document["payload"] = payload.is_null() ? Json::object() : payload;
        return document;
    }


ExportResult ExportSnapshot(const std::filesystem::path& target, const Json& document) {
    ExportResult result;
    if (target.empty() || target.filename().empty() || !document.is_object()) {
        result.message = "a file path and snapshot object are required";
        return result;
    }
    std::error_code error;
    if (!target.parent_path().empty()) std::filesystem::create_directories(target.parent_path(), error);
    if (error) { result.message = "cannot create output directory: " + error.message(); return result; }
    if (!file_storage::WriteJsonAtomically(target, document)) {
        result.message = "snapshot write, rename or durable sync failed";
        return result;
    }
    result.saved = true;
    result.path = target.string();
    return result;
}
}
