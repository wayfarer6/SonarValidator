#include "module/offline_export_module/offline_export.hpp"
#include <chrono>
#include <ctime>
#include <iomanip>
#include <sstream>

#include <cerrno>
#include <fcntl.h>
#include <system_error>
#include <unistd.h>

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
    std::string temporary = target.string() + ".tmp.XXXXXX";
    const int descriptor = ::mkstemp(temporary.data());
    if (descriptor < 0) { result.message = "cannot create snapshot file"; return result; }
    bool ok = true;
    try {
        const std::string data = document.dump(2) + "\n";
        std::size_t written = 0;
        while (written < data.size()) {
            const ssize_t count = ::write(descriptor, data.data() + written, data.size() - written);
            if (count < 0 && errno == EINTR) continue;
            if (count <= 0) { ok = false; break; }
            written += static_cast<std::size_t>(count);
        }
        if (ok && ::fsync(descriptor) != 0) ok = false;
    } catch (...) { ok = false; }
    if (::close(descriptor) != 0) ok = false;
    if (ok) {
        std::filesystem::rename(temporary, target, error);
        ok = !error;
    }
    if (!ok) {
        std::filesystem::remove(temporary, error);
        result.message = "snapshot write or atomic rename failed";
        return result;
    }
    result.saved = true;
    result.path = target.string();
    return result;
}
}
