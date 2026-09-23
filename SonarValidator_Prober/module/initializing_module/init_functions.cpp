
namespace fs = std::filesystem;

namespace
{
    // 기본 데이터 디렉터리(설치 시 systemd 로 root 권한으로 실행되는 것을 전제).
    const fs::path kDefaultDataDirectory = "/var/lib/sonar_validator_prober";

    // CLI 로 넘어온 실행 옵션입니다.
    //
    // 프로버는 기본이 "상시 실행" 이지만, 서버에 닿지 않는 장비에서는
    // 설정만 뽑아 파일로 가져가야 합니다. 그 경로를 CLI 로 노출합니다.
    //   --export-offline        서버 전송을 시도하지 않고 스냅샷 파일만 남김
    //   --export-dir <경로>      스냅샷 저장 위치 (기본: <데이터>/offline)
    //   --export-once           한 번만 수집하고 종료
    //   --export-stdout         스냅샷을 표준출력으로 인쇄 (파일 없이 복사/붙여넣기용)
    struct CliOptions
    {
        bool offline_only = false;
        bool export_once = false;
        bool export_stdout = false;
        std::string export_dir{};
        bool show_help = false;
    };

    // 기본 SQLite 템플릿 경로.
    const fs::path kDefaultTemplatePath =
        "/etc/sonar_validator_prober/sqlite_template.sqlite";

    // 데이터 디렉터리를 결정합니다.
    //  1) SONAR_DATA_DIR 환경변수
    //  2) 시스템 기본 경로(/var/lib/...)
    //  root 가 아닌 환경(예: vEOS bash, 사용자 홈 실행)에서는
    //  시스템 경로를 만들 수 없으므로 실패 시 실행 파일 옆으로 폴백합니다.
    fs::path ResolveDataDirectory()
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

    // SQLite 템플릿 경로를 결정합니다.
    //  SONAR_TEMPLATE_PATH 가 있으면 우선 사용하고, 없으면
    //    /etc/... → 실행 파일 디렉터리 순으로 찾습니다.
    fs::path ResolveTemplatePath()
    {
        if (const char* from_env = std::getenv("SONAR_TEMPLATE_PATH"))
        {
            if (from_env[0] != '\0')
            {
                return fs::path(from_env);
            }
        }

        std::error_code error;
        if (fs::exists(kDefaultTemplatePath, error))
        {
            return kDefaultTemplatePath;
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
        return kDefaultTemplatePath;
    }

    // 사용법을 출력합니다. (오프라인 export 옵션이 추가되어 도움말이 필요해졌습니다)
    void PrintUsage(const char *program)
    {
        std::cout
            << "SonarValidator Prober\n\n"
            << "사용법: " << (program == nullptr ? "sonar_validator_prober" : program) << " [옵션]\n\n"
            << "옵션:\n"
            << "  --export-offline       서버로 보내지 않고 스냅샷 JSON 파일만 남깁니다.\n"
            << "                         (관리 서버에 연결할 수 없는 장비용)\n"
            << "  --export-dir <경로>    스냅샷 저장 위치. 기본값은 <데이터 디렉터리>/offline\n"
            << "  --export-once          한 번만 수집하고 종료합니다. (--export-offline 과 함께 쓰면\n"
            << "                         즉시 파일 하나를 만들고 끝납니다)\n"
            << "  --export-stdout        스냅샷 JSON 을 표준출력으로 인쇄합니다. (파일 없이 복사용)\n"
            << "  --help, -h             이 도움말을 출력합니다.\n\n"
            << "환경변수:\n"
            << "  SONAR_DATA_DIR         데이터(DB/설정) 디렉터리\n"
            << "  SONAR_TEMPLATE_PATH    SQLite 템플릿 경로\n"
            << "  SONAR_OFFLINE_DIR      스냅샷 저장 디렉터리 (--export-dir 보다 우선순위 낮음)\n";
    }

    // 명령줄 인자를 해석합니다. 모르는 인자는 무시하고 경고만 남깁니다.
    // (설치 스크립트가 옵션을 덧붙여 실행해도 프로버가 죽지 않게 하기 위함)
    CliOptions ParseArgs(int argc, char **argv)
    {
        CliOptions options;
        for (int index = 1; index < argc; ++index)
        {
            const std::string argument = argv[index] == nullptr ? "" : argv[index];

            if (argument == "--export-offline")
            {
                options.offline_only = true;
            }
            else if (argument == "--export-once")
            {
                options.export_once = true;
            }
            else if (argument == "--export-stdout")
            {
                options.export_stdout = true;
            }
            else if (argument == "--export-dir")
            {
                if (index + 1 < argc && argv[index + 1] != nullptr)
                {
                    options.export_dir = argv[++index];
                }
                else
                {
                    std::cerr << "[WARN] --export-dir 에 경로가 없습니다. 기본 경로를 사용합니다.\n";
                }
            }
            else if (argument == "--help" || argument == "-h")
            {
                options.show_help = true;
            }
            else
            {
                std::cerr << "[WARN] 알 수 없는 인자: " << argument << '\n';
            }
        }
        return options;
    }
}
