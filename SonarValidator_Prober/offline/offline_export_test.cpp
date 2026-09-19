// offline_export 모듈 단위 테스트.
//
// 검증 범위
//  - 파일 이름 생성이 안전한가 (에이전트 이름에 특수문자가 있어도)
//  - 스냅샷 문서가 서버 계약(schema/schema_version/payload)을 지키는가
//  - 원자적 쓰기(임시 파일 → rename)가 실제로 동작하고 반쪽 파일이 남지 않는가
//  - 재전송 대기열(ListPendingSnapshots)이 시간순으로 정렬되는가
//  - 손상된 파일을 만나도 예외 없이 null 을 돌려주는가
// 네트워크가 필요 없으므로 CI 에서 그대로 실행됩니다.

#include <cassert>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>

#include "offline/offline_export.hpp"

namespace fs = std::filesystem;

namespace
{

int g_failures = 0;

void Check(bool condition, const std::string& message)
{
    if (condition)
    {
        std::cout << "[ ok ] " << message << '\n';
    }
    else
    {
        std::cout << "[FAIL] " << message << '\n';
        ++g_failures;
    }
}

// 테스트마다 쓰는 임시 디렉터리를 만들고 지웁니다.
struct TempDirectory
{
    fs::path path;

    explicit TempDirectory(const std::string& name)
    {
        path = fs::temp_directory_path() / ("sonar_offline_test_" + name);
        std::error_code error;
        fs::remove_all(path, error);
        fs::create_directories(path, error);
    }

    ~TempDirectory()
    {
        std::error_code error;
        fs::remove_all(path, error);
    }
};

void TestSanitizeForFileName()
{
    std::cout << "\n--- SanitizeForFileName ---\n";

    // 영숫자/허용 기호는 그대로 남아야 합니다.
    Check(offline::SanitizeForFileName("c8000v-1") == "c8000v-1",
          "허용 문자는 그대로 유지");

    // 경로 구분자와 콜론은 파일 이름을 망가뜨리므로 치환되어야 합니다.
    Check(offline::SanitizeForFileName("a/b:c") == "a_b_c",
          "경로/콜론 문자는 밑줄로 치환");

    // 전부 특수문자여도 빈 이름이 나오면 안 됩니다.
    Check(!offline::SanitizeForFileName("///").empty(),
          "특수문자만 있어도 빈 이름이 되지 않음");

    // 빈 입력도 안전한 기본값을 돌려줘야 합니다.
    Check(offline::SanitizeForFileName("") == "agent",
          "빈 입력은 기본 이름으로 대체");
}

void TestBuildSnapshotFileName()
{
    std::cout << "\n--- BuildSnapshotFileName ---\n";

    const std::string name =
        offline::BuildSnapshotFileName("c8000v-1", "2026-09-19T04:00:00Z");

    Check(name.rfind("sonar_snapshot_", 0) == 0, "파일 접두사가 붙음");
    Check(name.size() > 5 && name.compare(name.size() - 5, 5, ".json") == 0,
          "확장자가 .json");
    // 콜론이 남아 있으면 윈도우에서 파일을 만들 수 없습니다.
    Check(name.find(':') == std::string::npos, "콜론이 파일 이름에 남지 않음");

    std::cout << "       생성된 이름: " << name << '\n';
}

void TestBuildSnapshotDocument()
{
    std::cout << "\n--- BuildSnapshotDocument ---\n";

    nlohmann::json payload = nlohmann::json::object();
    payload["product"] = "Cisco 8000v";
    payload["route_status"] = {{"routes", nlohmann::json::array()}};

    const nlohmann::json document = offline::BuildSnapshotDocument(
        "c8000v-1", "c8000v-1", "ROUTER", "Cisco 8000v", "Cisco 8000v",
        "17.9.4", "2026-09-19T04:00:00Z", offline::reason::kServerUnreachable, payload);

    Check(document["schema"] == "sonar.offline.snapshot",
          "schema 식별자가 서버 계약과 일치");
    Check(document["schema_version"] == 1, "schema_version 이 1");
    Check(document["agent_id"] == "c8000v-1", "agent_id 가 보존됨");
    Check(document["reason"] == "server-unreachable", "실패 사유가 기록됨");
    Check(document.contains("exported_at") && document["exported_at"].is_string(),
          "exported_at 이 채워짐");
    // payload 가 그대로 실려야 서버 파서가 온라인과 동일하게 동작합니다.
    Check(document["payload"]["product"] == "Cisco 8000v",
          "payload 가 손실 없이 보존됨");

    // payload 가 null 이어도 서버가 null 을 만나지 않아야 합니다.
    const nlohmann::json empty_payload =
        offline::BuildSnapshotDocument("a", "a", "VM", "Ubuntu", "Ubuntu", "",
                                      "2026-09-19T04:00:00Z", "test", nlohmann::json());
    Check(empty_payload["payload"].is_object(),
          "null payload 는 빈 객체로 대체됨");
}

void TestWriteAndReadSnapshot()
{
    std::cout << "\n--- WriteSnapshot / ReadSnapshot ---\n";

    TempDirectory temp("write");
    const std::string directory = temp.path.string();

    nlohmann::json document = offline::BuildSnapshotDocument(
        "sw-01", "sw-01", "SWITCH", "Arista", "Arista", "4.29",
        "2026-09-19T04:00:00Z", offline::reason::kSendFailed, nlohmann::json::object());

    const offline::ExportResult result =
        offline::ExportSnapshot(directory, "sw-01", "2026-09-19T04:00:00Z", document);

    Check(result.saved, "스냅샷 저장 성공");
    Check(fs::exists(result.path), "파일이 실제로 존재");
    Check(result.path.find("sw-01") != std::string::npos, "파일 이름에 에이전트 포함");

    // 읽어서 왕복이 되는지 확인합니다.
    const nlohmann::json loaded = offline::ReadSnapshot(result.path);
    Check(!loaded.is_null(), "저장한 파일을 다시 읽을 수 있음");
    Check(loaded["agent_id"] == "sw-01", "왕복 후 agent_id 보존");

    // 임시 파일(.tmp)이 남아 있으면 안 됩니다.
    bool leftover = false;
    for (const auto& entry : fs::directory_iterator(directory))
    {
        if (entry.path().extension() == ".tmp")
        {
            leftover = true;
        }
    }
    Check(!leftover, "임시 파일이 남지 않음 (원자적 rename)");

    // 파일이 하나뿐이므로 대기열도 1건이어야 합니다.
    const std::vector<std::string> pending = offline::ListPendingSnapshots(directory);
    Check(pending.size() == 1, "재전송 대기열에 1건");

    Check(offline::RemoveSnapshot(result.path), "전송 후 파일 삭제 성공");
    Check(!fs::exists(result.path), "삭제 후 파일이 사라짐");
    Check(offline::ListPendingSnapshots(directory).empty(), "삭제 후 대기열이 빔");
}

void TestListPendingSnapshotsOrder()
{
    std::cout << "\n--- ListPendingSnapshots 정렬 ---\n";

    TempDirectory temp("order");
    const std::string directory = temp.path.string();

    // 일부러 뒤죽박죽된 순서로 만듭니다. (파일 이름의 타임스탬프로 정렬되어야 함)
    const std::vector<std::string> stamps = {
        "2026-09-19T04:00:30Z",
        "2026-09-19T04:00:10Z",
        "2026-09-19T04:00:20Z"};

    for (const std::string& stamp : stamps)
    {
        const nlohmann::json document = offline::BuildSnapshotDocument(
            "a1", "a1", "ROUTER", "FRR", "FRR", "", stamp, "test", nlohmann::json::object());
        offline::ExportSnapshot(directory, "a1", stamp, document);
    }

    // 스냅샷이 아닌 파일은 대기열에 들어가면 안 됩니다.
    {
        std::ofstream stray(fs::path(directory) / "notes.txt");
        stray << "ignore me\n";
    }
    {
        std::ofstream stray(fs::path(directory) / "unrelated.json");
        stray << "{}\n";
    }

    const std::vector<std::string> pending = offline::ListPendingSnapshots(directory);
    Check(pending.size() == 3, "sonar_snapshot_*.json 만 대기열에 포함");

    // 이름이 곧 시간순이므로 10초 → 20초 → 30초 순서여야 합니다.
    bool ordered = true;
    for (std::size_t i = 1; i < pending.size(); ++i)
    {
        if (pending[i - 1] > pending[i])
        {
            ordered = false;
        }
    }
    Check(ordered, "대기열이 오래된 것부터 정렬됨 (재전송 순서 보장)");

    if (!pending.empty())
    {
        std::cout << "       첫 항목: " << fs::path(pending.front()).filename().string() << '\n';
    }
}

void TestCorruptedSnapshotIsSafe()
{
    std::cout << "\n--- 손상된 파일 처리 ---\n";

    TempDirectory temp("corrupt");
    const std::string directory = temp.path.string();

    // JSON 이 아닌 내용을 스냅샷 이름으로 만듭니다.
    const fs::path broken = fs::path(directory) / "sonar_snapshot_broken.json";
    {
        std::ofstream output(broken);
        output << "{ this is not json";
    }

    // 예외를 던지면 재전송 루프 전체가 멈춥니다.
    const nlohmann::json loaded = offline::ReadSnapshot(broken.string());
    Check(loaded.is_null(), "손상된 파일은 예외 없이 null 반환");

    // 없는 파일도 마찬가지입니다.
    Check(offline::ReadSnapshot((fs::path(directory) / "nope.json").string()).is_null(),
          "없는 파일도 예외 없이 null 반환");

    // 손상된 파일도 목록에는 남아 있어 운영자가 인지할 수 있어야 합니다.
    Check(offline::ListPendingSnapshots(directory).size() == 1,
          "손상된 파일도 대기열에 남아 인지 가능");
}

void TestWriteToUnwritableDirectory()
{
    std::cout << "\n--- 쓰기 불가 경로 ---\n";

    const nlohmann::json document = offline::BuildSnapshotDocument(
        "a", "a", "VM", "Ubuntu", "Ubuntu", "", "t", "test", nlohmann::json::object());

    // 빈 디렉터리는 저장할 수 없으므로 실패를 알려야 합니다. (예외 아님)
    const offline::ExportResult result = offline::WriteSnapshot("", "x.json", document);
    Check(!result.saved, "빈 디렉터리는 저장 실패로 보고");
    Check(!result.message.empty(), "실패 사유가 채워짐");
}

void TestResolveExportDirectory()
{
    std::cout << "\n--- ResolveExportDirectory ---\n";

    // 인자가 최우선입니다.
    Check(offline::ResolveExportDirectory("/data", "/custom") == "/custom",
          "명시적 override 가 최우선");

    // 인자가 없으면 데이터 디렉터리 하위 offline 을 씁니다.
    const std::string from_data = offline::ResolveExportDirectory("/data", "");
    Check(from_data.find("/data") != std::string::npos, "데이터 디렉터리를 기준으로 함");
    Check(from_data.find("offline") != std::string::npos, "offline 하위 폴더를 씀");

    // 둘 다 없어도 빈 문자열이 나오면 안 됩니다. (쓰기 실패로 이어짐)
    Check(!offline::ResolveExportDirectory("", "").empty(),
          "폴백 경로가 항상 채워짐 (빈 경로 금지)");
}

} // namespace

int main()
{
    std::cout << "=== offline_export 테스트 ===\n";

    TestSanitizeForFileName();
    TestBuildSnapshotFileName();
    TestBuildSnapshotDocument();
    TestWriteAndReadSnapshot();
    TestListPendingSnapshotsOrder();
    TestCorruptedSnapshotIsSafe();
    TestWriteToUnwritableDirectory();
    TestResolveExportDirectory();

    std::cout << "\n=== 결과: " << (g_failures == 0 ? "전부 통과" : "실패 있음")
              << " (실패 " << g_failures << "건) ===\n";
    return g_failures == 0 ? 0 : 1;
}
