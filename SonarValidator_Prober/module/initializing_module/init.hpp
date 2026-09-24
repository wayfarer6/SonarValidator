#ifndef SONAR_VALIDATOR_PROBER_INIT_HPP_
#define SONAR_VALIDATOR_PROBER_INIT_HPP_

#include <filesystem>
#include "database/database_service.hpp"
#include "module/configuration_module/prober_config.hpp"

// 런타임 준비(디렉터리/설정/DB)를 담당하는 초기화 클래스입니다.
class AppInitializer
{
public:
    static bool EnsureDataDirectory(const std::filesystem::path &path);     // 데이터 디렉터리 생성
    static bool InitializeConfig(const std::filesystem::path &path, ProberConfig &config);  // 설정 로드/생성
    static bool InitializeDatabase(                                      // SQLite 열기 + 스키마 준비
        const std::filesystem::path &database_path,
        const ProberConfig &config,
        DbHandle &database_handle);
    static bool PrepareRuntime(                                          // 전체 런타임 준비(순차 호출)
        const std::filesystem::path &data_directory,
        const std::filesystem::path &config_file_path,
        const std::filesystem::path &sqlite_db_path,
        const std::filesystem::path &sqlite_template_path,
        ProberConfig &config,
        DbHandle &database_handle);
};

#endif // SONAR_VALIDATOR_PROBER_INIT_HPP_
