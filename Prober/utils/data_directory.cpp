#include "utils/path_manager.hpp"
// 기본 데이터 디렉터리(설치 시 systemd 로 root 권한으로 실행되는 것을 전제).

namespace
{
    const fs::path kDefaultDataDirectory = "/var/lib/sonar_validator_prober";
    const fs::path kDefaultSqliteTemplatePath = "/etc/sonar_validator_prober/sqlite_template.sqlite";
} // namespace


fs::path PathManager::ResolveDataDirectory()
{
    if (const char* from_env = std::getenv("SONAR_DATA_DIR"))
        {
            if (from_env[0] != '\0')
            {
                return fs::path(from_env);
            }
        }
        return kDefaultDataDirectory;
}

fs::path PathManager::ResolveTemplatePath()
    {
        if (const char* from_env = std::getenv("SONAR_TEMPLATE_PATH"))
        {
            if (from_env[0] != '\0')
            {
                return fs::path(from_env);
            }
        }

        std::error_code error;
        if (fs::exists(kDefaultSqliteTemplatePath, error))
        {
            return kDefaultSqliteTemplatePath;
        }

        // 실행 파일 옆에 두는 배포 형태(예: /mnt/flash/sonar_validator/)를 지원합니다.
        std::error_code self_error;
        const fs::path self = fs::read_symlink("/proc/self/exe", self_error);
        if (!self_error)
        {
            const fs::path candidate = self.parent_path() / "sqlite_template.sqlite";
            if (fs::exists(candidate, error))
            {
                return candidate;
            }
        }
        return kDefaultSqliteTemplatePath;
    }