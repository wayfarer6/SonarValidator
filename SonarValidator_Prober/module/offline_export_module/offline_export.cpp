#include "module/offline_export_module/offline_export.hpp"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <ctime>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <system_error>

namespace fs = std::filesystem;

namespace offline
{
    namespace
    {

        // nlohmann::json 의 null 표현입니다. (빈 Json{} 은 null 이다)
        const Json kNull;

        // UTC 현재 시각을 "YYYY-MM-DDTHH:MM:SSZ" 로 만듭니다.
        //
        // database/telemetry_store 의 같은 이름 함수를 쓰지 않는 이유:
        // 그 헤더는 sqlite3 헤더까지 끌어와서, 이 모듈만 단독으로
        // 컴파일/테스트하는 것을 어렵게 만듭니다. 여기서는 표준 라이브러리만 씁니다.
        std::string CurrentUtcTimestamp()
        {
            const auto now = std::chrono::system_clock::now();
            const std::time_t seconds = std::chrono::system_clock::to_time_t(now);

            std::tm utc{};
#if defined(_WIN32)
            gmtime_s(&utc, &seconds);
#else
            gmtime_r(&seconds, &utc);
#endif

            std::ostringstream stream;
            stream << std::put_time(&utc, "%Y-%m-%dT%H:%M:%SZ");
            return stream.str();
        }

    } // namespace

    int ExportOnce(const fs::path &offline_directory,
                   const std::string &agent_id,
                   const Json &snapshot,
                   bool export_stdout)
    {
        if (export_stdout)
        {
            // 표준출력으로만 인쇄합니다. (파일 없이 복사/붙여넣기 → 프론트엔드 업로드)
            std::cout << snapshot.dump(2) << '\n';
            return 0;
        }

        std::error_code directory_error;
        fs::create_directories(offline_directory, directory_error);

        const ExportResult export_result = ExportSnapshot(
            offline_directory.string(),
            agent_id,
            snapshot.value("collected_at", std::string{}),
            snapshot);
        if (!export_result.saved)
        {
            std::cerr << "[ERROR] 스냅샷을 저장하지 못했습니다: " << export_result.message << '\n';
            std::cerr << "        (--export-dir 또는 SONAR_OFFLINE_DIR 로 경로를 지정하세요)\n";
            return 1;
        }

        std::cout << "[INFO] 스냅샷을 저장했습니다: " << export_result.path << '\n';
        std::cout << "       이 파일을 SonarValidator 프론트엔드의 "
                     "'Import Offline Prober Data' 카드에 끌어다 놓으세요.\n";
        return 0;
    }

    std::string SanitizeForFileName(const std::string &text)
    {
        std::string safe;
        safe.reserve(text.size());
        for (const char ch : text)
        {
            const unsigned char value = static_cast<unsigned char>(ch);
            // 영숫자와 일부 기호만 남깁니다. 나머지(공백/':'/'/'/'\')는 '_' 로.
            if (std::isalnum(value) != 0 || ch == '-' || ch == '_' || ch == '.')
            {
                safe.push_back(ch);
            }
            else
            {
                safe.push_back('_');
            }
        }
        if (safe.empty())
        {
            safe = "agent";
        }
        return safe;
    }

    std::string ResolveExportDirectory(const std::string &data_directory,
                                       const std::string &override_dir)
    {
        if (!override_dir.empty())
        {
            return override_dir;
        }

        if (const char *from_env = std::getenv("SONAR_OFFLINE_DIR"))
        {
            if (from_env[0] != '\0')
            {
                return from_env;
            }
        }

        if (!data_directory.empty())
        {
            return (fs::path(data_directory) / "offline").string();
        }

        // 데이터 디렉터리조차 없을 때의 최후 폴백입니다.
        // (실행 중인 프로세스의 현재 디렉터리를 쓰면 쓰기 권한 문제가 흔하므로 /tmp 를 쓴다.)
        return "/tmp/sonar_validator_offline";
    }

    std::string BuildSnapshotFileName(const std::string &agent_id,
                                      const std::string &collected_at)
    {
        // collected_at 은 "2026-09-19T04:00:00Z" 형태이므로 ':' 만 '-' 로 바꾸면
        // 윈도우/리눅스 어디서나 안전한 이름이 된다.
        std::string stamp = SanitizeForFileName(collected_at);
        std::replace(stamp.begin(), stamp.end(), '.', '-');

        return std::string(kFilePrefix) + SanitizeForFileName(agent_id) + "_" + stamp + kFileExtension;
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
        // export 시각은 서버가 신뢰하지 않아도 되도록 별도 필드로 둡니다.
        document["exported_at"] = CurrentUtcTimestamp();
        document["reason"] = reason;
        // payload 가 null 이면 빈 객체로 대체합니다. (서버 파서가 null 을 만나지 않게)
        document["payload"] = payload.is_null() ? Json::object() : payload;
        return document;
    }

    ExportResult WriteSnapshot(const std::string &directory,
                               const std::string &file_name,
                               const Json &document)
    {
        ExportResult result;

        if (directory.empty() || file_name.empty())
        {
            result.message = "empty directory or file name";
            result.skipped = true;
            return result;
        }

        std::error_code error;
        fs::create_directories(directory, error);
        if (error && !fs::is_directory(directory, error))
        {
            result.message = "cannot create directory: " + directory;
            return result;
        }

        const fs::path target = fs::path(directory) / file_name;
        const fs::path temporary = fs::path(directory) / (file_name + ".tmp");

        {
            std::ofstream output(temporary, std::ios::trunc);
            if (!output)
            {
                result.message = "cannot open file for writing: " + temporary.string();
                return result;
            }
            // 한글 등 비ASCII 를 그대로 보존합니다. (서버가 UTF-8 로 읽는다)
            output << document.dump(2) << '\n';
            output.flush();
            if (!output)
            {
                output.close();
                fs::remove(temporary, error);
                result.message = "write failed: " + temporary.string();
                return result;
            }
        }

        // rename 은 같은 파일시스템에서 원자적이므로 반쪽 파일이 노출되지 않습니다.
        fs::rename(temporary, target, error);
        if (error)
        {
            // rename 이 막히는 환경(예: 일부 컨테이너 볼륨)에서는 직접 복사 후 지웁니다.
            std::error_code copy_error;
            fs::copy_file(temporary, target, fs::copy_options::overwrite_existing, copy_error);
            fs::remove(temporary, copy_error);
            if (copy_error)
            {
                result.message = "cannot move snapshot into place: " + target.string();
                return result;
            }
        }

        result.saved = true;
        result.path = target.string();
        result.message = "saved";
        return result;
    }

    ExportResult ExportSnapshot(const std::string &directory,
                                const std::string &agent_id,
                                const std::string &collected_at,
                                const Json &document)
    {
        return WriteSnapshot(directory,
                             BuildSnapshotFileName(agent_id, collected_at),
                             document);
    }

    std::vector<std::string> ListPendingSnapshots(const std::string &directory)
    {
        std::vector<std::string> files;

        std::error_code error;
        if (!fs::is_directory(directory, error))
        {
            return files;
        }

        for (const auto &entry : fs::directory_iterator(directory, error))
        {
            if (error)
            {
                break;
            }
            if (!entry.is_regular_file(error))
            {
                continue;
            }

            const std::string name = entry.path().filename().string();
            if (name.size() <= 5 || name.compare(name.size() - 5, 5, ".json") != 0)
            {
                continue;
            }
            // 임시 파일(.json.tmp)은 애초에 확장자가 .tmp 라 위에서 걸러진다.
            if (name.find(kFilePrefix) != 0)
            {
                continue;
            }
            files.push_back(entry.path().string());
        }

        // 파일 이름에 타임스탬프가 들어 있으므로 사전순 = 시간순이다.
        std::sort(files.begin(), files.end());
        return files;
    }

    Json ReadSnapshot(const std::string &path)
    {
        std::ifstream input(path);
        if (!input)
        {
            return kNull;
        }

        try
        {
            Json document;
            input >> document;
            return document;
        }
        catch (const std::exception &)
        {
            // 손상된 파일 하나가 재전송 루프 전체를 멈추면 안 됩니다.
            return kNull;
        }
    }

    bool RemoveSnapshot(const std::string &path)
    {
        std::error_code error;
        return fs::remove(path, error) && !error;
    }

} // namespace offline
