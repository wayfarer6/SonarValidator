#ifndef SONAR_VALIDATOR_PROBER_INIT_HPP_
#define SONAR_VALIDATOR_PROBER_INIT_HPP_

#include <filesystem>

#include "database/database_service.hpp"
#include "prober_config.hpp"

class AppInitializer
{
public:
    static bool EnsureDataDirectory(const std::filesystem::path &path);
    static bool InitializeConfig(const std::filesystem::path &path, ProberConfig &config);
    static bool InitializeDatabase(
        const std::filesystem::path &database_path,
        const ProberConfig &config,
        DbHandle &database_handle);
    static bool PrepareRuntime(
        const std::filesystem::path &data_directory,
        const std::filesystem::path &config_file_path,
        const std::filesystem::path &sqlite_db_path,
        const std::filesystem::path &sqlite_template_path,
        ProberConfig &config,
        DbHandle &database_handle);
};

#endif  // SONAR_VALIDATOR_PROBER_INIT_HPP_
