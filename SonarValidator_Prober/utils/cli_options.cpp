#include "utils/cli_options.hpp"

CliOptions CliOptions::ParseArgs(int argc, char **argv)
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

void CliOptions::printUsage(const char *program) const
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