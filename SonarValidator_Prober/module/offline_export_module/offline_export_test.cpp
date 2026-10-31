#include "module/offline_export_module/offline_export.hpp"
#include "utils/cli_options.hpp"
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <thread>
#include <vector>
#include <sys/stat.h>
#include <unistd.h>

void Require(bool value, const char* message) { if (!value) throw std::runtime_error(message); }
CliOptions Options(std::vector<std::string> values) {
    std::vector<char*> args;
    for (auto& value : values) args.push_back(value.data());
    return CliOptions::ParseArgs(static_cast<int>(args.size()), args.data());
}
int main() {
    char directory[] = "/tmp/sonar-export-test.XXXXXX";
    Require(mkdtemp(directory) != nullptr, "temporary directory");
    const std::filesystem::path root(directory);
    try {
        auto document = offline::BuildSnapshotDocument("router", "라우터", "ROUTER", "Cisco", "Cisco", "Linux",
                "2026-11-01T00:00:00Z", "manual-export", {{"routes", nlohmann::json::array()}});
        Require(document["schema"] == "sonar.offline.snapshot", "import schema");
        Require(document["schema_version"] == 1, "import version");
        const auto target = root / "nested/snapshot.json";
        auto result = offline::ExportSnapshot(target, document);
        Require(result.saved && result.path == target.string(), "one file export");
        nlohmann::json actual;
        std::ifstream(target) >> actual;
        Require(actual == document, "complete UTF-8 snapshot round trip");
        struct stat info{};
        Require(stat(target.c_str(), &info) == 0 && (info.st_mode & 0777) == 0600, "snapshot is private");
        std::vector<std::jthread> writers;
        for (int i = 0; i < 6; ++i) writers.emplace_back([&, i] {
            auto next = document; next["writer"] = i;
            for (int j = 0; j < 5; ++j) Require(offline::ExportSnapshot(target, next).saved, "concurrent export");
        });
        writers.clear();
        std::ifstream(target) >> actual;
        Require(actual["writer"].is_number_integer(), "atomic complete replacement");
        for (const auto& entry : std::filesystem::directory_iterator(target.parent_path()))
            Require(entry.path() == target, "no temporary files remain");
        Require(!offline::ExportSnapshot({}, document).saved, "empty file path rejected");
        Require(!offline::ExportSnapshot(root, document).saved, "directory cannot be overwritten");
        Require(!offline::ExportSnapshot(target, nullptr).saved, "non-snapshot rejected");
        Require(Options({"prober", "--export", "one.json"}).export_file == "one.json", "single CLI path");
        Require(!Options({"prober", "--export"}).error.empty(), "missing output path rejected");
        Require(!Options({"prober", "--export", "--help"}).error.empty(), "option is not a filename");
        Require(!Options({"prober", "--export-stdout"}).error.empty(), "removed modes rejected");
        Require(!Options({"prober", "--export", "a", "--export", "b"}).error.empty(), "repeated export rejected");
        std::filesystem::remove_all(root);
        std::cout << "Export file contract PASS\n";
    } catch (...) { std::filesystem::remove_all(root); throw; }
}
