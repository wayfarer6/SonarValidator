#ifndef SONAR_VALIDATOR_PROBER_OFFLINE_OFFLINE_EXPORT_HPP_
#define SONAR_VALIDATOR_PROBER_OFFLINE_OFFLINE_EXPORT_HPP_

#include <string>
#include <vector>
#include <filesystem>
#include <nlohmann/json.hpp>

// =============================================================================
//  OfflineExport — 서버에 연결할 수 없을 때 수집 결과를 JSON 파일로 남긴다
//
//  왜 필요한가
//    프로버를 배포했는데 중앙 서버까지 네트워크가 닿지 않는 경우가 있다.
//    (망분리 환경, 관리망 미개통, 방화벽 정책 미반영 등)
//    이때 수집 자체는 로컬에서 가능하므로, 결과를 JSON 으로 남겨 두었다가
//    나중에 운영자가 프론트엔드에 드래그앤드롭으로 업로드하면 된다.
//
//  파일 형식 (schema = "sonar.offline.snapshot")
//    {
//      "schema": "sonar.offline.snapshot",
//      "schema_version": 1,
//      "agent_id": "c8000v-1",
//      "agent_name": "c8000v-1",
//      "device_type": "ROUTER",
//      "product": "Cisco 8000v",
//      "vendor": "Cisco",
//      "kernel": "6.5.0",
//      "collected_at": "2026-09-19T04:00:00Z",
//      "exported_at": "2026-09-19T04:00:01Z",
//      "reason": "server-unreachable",
//      "payload": { ... 텔레메트리 body 와 동일한 구조 ... }
//    }
//
//  서버(Spring)의 OfflineConfigController 가 이 문서를 받아
//  payload 를 기존 벤더별 파서에 그대로 흘려보낸다.
//  즉 오프라인 경로와 온라인 경로가 "같은 payload" 를 공유하므로
//  파서가 하나만 있어도 두 경로가 모두 동작한다.
// =============================================================================

namespace offline
{

using Json = nlohmann::json;

// 오프라인 스냅샷 문서의 스키마 식별자입니다. 서버와 문자열이 일치해야 합니다.
inline constexpr const char* kSchemaName = "sonar.offline.snapshot";

// 스키마 버전입니다. 구조가 바뀌면 올리고, 서버는 낮은 버전도 계속 받습니다.
inline constexpr int kSchemaVersion = 1;

// 파일 이름 접두사입니다. (예: sonar_snapshot_c8000v-1_20260919T040000Z.json)
inline constexpr const char* kFilePrefix = "sonar_snapshot_";

// 파일 확장자입니다.
inline constexpr const char* kFileExtension = ".json";

// export 실패 사유를 사람이 읽을 수 있게 남기기 위한 문자열입니다.
namespace reason
{
inline constexpr const char* kServerUnreachable = "server-unreachable";
inline constexpr const char* kSendFailed = "send-failed";
inline constexpr const char* kForcedOffline = "offline-mode";
inline constexpr const char* kManualExport = "manual-export";
} // namespace reason

// 한 번의 export 결과입니다.
struct ExportResult
{
    bool saved = false;      // 파일을 실제로 만들었는지
    bool skipped = false;    // 저장할 내용이 없어 건너뛰었는지
    std::string path;        // 저장된 파일 경로(성공 시)
    std::string message;     // 실패/건너뜀 사유(사람이 읽는 문장)
};


// export 기능을 실행하는 함수 입니다.
//
//   수집 자체는 호출자(main)가 TelemetryMonitor::CollectSnapshotDocument() 로
//   끝낸 뒤, 완성된 스냅샷 문서를 여기에 넘긴다.
//   이렇게 하면 이 모듈은 장치/서버/DB 를 전혀 몰라도 되어
//   단위 테스트(offline_export_test)가 네트워크 없이 돌아간다.
//
//   export_stdout 이 true 면 파일을 만들지 않고 JSON 을 표준출력으로 인쇄한다.
// 반환값은 프로세스 종료 코드(0=성공, 1=실패)다.
int ExportOnce(const std::filesystem::path& offline_directory,
               const std::string& agent_id,
               const Json& snapshot,
               bool export_stdout);

// 파일 이름에 쓸 수 없는 문자를 '_' 로 바꿉니다.
// (에이전트 이름에 ':' '/' 등이 들어가도 파일을 만들 수 있게 한다.)
std::string SanitizeForFileName(const std::string& text);

// export 디렉터리를 결정합니다.
//  1) 함수 인자 override_dir (CLI --export-dir)
//  2) 환경변수 SONAR_OFFLINE_DIR
//  3) <data_directory>/offline
// 빈 디렉터리가 나오지 않도록 마지막 폴백까지 사용합니다.
std::string ResolveExportDirectory(const std::string& data_directory,
                                   const std::string& override_dir);

// 스냅샷 파일 이름을 만듭니다. (collected_at 의 ':' 는 '-' 로 치환)
std::string BuildSnapshotFileName(const std::string& agent_id,
                                  const std::string& collected_at);

// 오프라인 스냅샷 문서를 조립합니다.
// payload 는 서버로 보낼 텔레메트리 body 와 완전히 같은 구조여야 합니다.
Json BuildSnapshotDocument(const std::string& agent_id,
                           const std::string& agent_name,
                           const std::string& device_type,
                           const std::string& product,
                           const std::string& vendor,
                           const std::string& kernel,
                           const std::string& collected_at,
                           const std::string& reason,
                           const Json& payload);

// 문서를 디렉터리에 저장합니다.
//  - 디렉터리가 없으면 만듭니다.
//  - 임시 파일에 쓴 뒤 rename 하므로, 도중에 죽어도 반쪽 파일이 남지 않습니다.
//  - 예외를 던지지 않고 ExportResult 로 실패를 알립니다.
ExportResult WriteSnapshot(const std::string& directory,
                           const std::string& file_name,
                           const Json& document);

// 위 두 단계를 묶은 편의 함수입니다.
ExportResult ExportSnapshot(const std::string& directory,
                            const std::string& agent_id,
                            const std::string& collected_at,
                            const Json& document);

// 디렉터리에 남아 있는 스냅샷 파일 목록을 이름순으로 돌려줍니다.
// (재전송 대기열로 쓴다. 오래된 것부터 보내야 시간 순서가 유지된다.)
std::vector<std::string> ListPendingSnapshots(const std::string& directory);

// 스냅샷 파일 하나를 읽어 JSON 으로 파싱합니다. 실패하면 null 을 돌려줍니다.
Json ReadSnapshot(const std::string& path);

// 전송에 성공한 스냅샷 파일을 지웁니다.
bool RemoveSnapshot(const std::string& path);

} // namespace offline

#endif // SONAR_VALIDATOR_PROBER_OFFLINE_OFFLINE_EXPORT_HPP_
