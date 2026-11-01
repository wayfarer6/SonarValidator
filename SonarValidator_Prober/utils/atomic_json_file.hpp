#ifndef SONAR_ATOMIC_JSON_FILE_HPP_
#define SONAR_ATOMIC_JSON_FILE_HPP_

#include <cerrno>
#include <cstdlib>
#include <fcntl.h>
#include <filesystem>
#include <string>
#include <unistd.h>
#include <nlohmann/json.hpp>

namespace file_storage {
inline bool Sync(int descriptor) {
    int result;
    do { result = ::fsync(descriptor); } while (result != 0 && errno == EINTR);
    return result == 0;
}

inline bool WriteJsonAtomically(const std::filesystem::path& target, const nlohmann::json& document) {
    if (target.empty() || !document.is_object()) return false;
    const auto parent = target.parent_path().empty() ? std::filesystem::path(".") : target.parent_path();
    const int directory = ::open(parent.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    if (directory < 0) return false;
    std::string temporary = target.string() + ".tmp.XXXXXX";
    const int descriptor = ::mkstemp(temporary.data());
    if (descriptor < 0) { ::close(directory); return false; }
    bool ok = ::fcntl(descriptor, F_SETFD, FD_CLOEXEC) != -1;
    try {
        const std::string data = document.dump(2) + "\n";
        std::size_t written = 0;
        while (ok && written < data.size()) {
            const ssize_t count = ::write(descriptor, data.data() + written, data.size() - written);
            if (count < 0 && errno == EINTR) continue;
            if (count <= 0) { ok = false; break; }
            written += static_cast<std::size_t>(count);
        }
        if (ok) ok = Sync(descriptor);
    } catch (...) { ok = false; }
    if (::close(descriptor) != 0) ok = false;
    std::error_code error;
    if (ok) {
        std::filesystem::rename(temporary, target, error);
        ok = !error;
    }
    // The rename must reach disk too, before an applied-state acknowledgement.
    if (ok) ok = Sync(directory);
    if (::close(directory) != 0) ok = false;
    if (!ok) std::filesystem::remove(temporary, error);
    return ok;
}
}
#endif
