#ifndef SONAR_VALIDATOR_PROBER_DATABASE_SERVICE_HPP_
#define SONAR_VALIDATOR_PROBER_DATABASE_SERVICE_HPP_

#include <condition_variable>
#include <exception>
#include <future>
#include <functional>
#include <memory>
#include <mutex>
#include <queue>
#include <sqlite3.h>
#include <string>
#include <thread>
#include <utility>
#include <vector>

// unique_ptr이 sqlite3*를 해제할 때 호출할 커스텀 삭제자입니다.
struct DbHandler
{
    void operator()(sqlite3 *database) const
    {
        if (database != nullptr)
        {
            sqlite3_close(database);
        }
    }
};

// sqlite3 연결의 소유권을 자동 관리하는 RAII 핸들입니다.
// unique_ptr처럼 스코프를 벗어나면 자동으로 sqlite3_close가 호출됩니다.
using DbHandle = std::unique_ptr<sqlite3, DbHandler>;

// DB 작업 결과입니다.
struct DatabaseResult
{
    std::vector<std::string> db_task_result;  // 결과 행/상태 문자열 목록 (비어 있으면 실패 또는 결과 없음)
    std::string sql_task;                     // 실행한 SQL (디버깅용)
};

// 큐로 전달되는 작업 단위입니다.
struct DatabaseTask
{
    // 실제 SQL 실행 로직(람다). DbHandle을 받아 내부에서 .get()으로 sqlite3*에 접근합니다.
    std::function<DatabaseResult(DbHandle &)> execute;
    // 실행 결과를 비동기로 전달받기 위한 약속(promise)입니다.
    std::promise<DatabaseResult> result;
};


class DatabaseService
{
public:
    explicit DatabaseService(const std::string& database_path);
    ~DatabaseService();

    void Start();
    void UpdateStatus(std::string value);
    void SaveConfig(std::string key, std::string value);

private:
    void Worker(std::stop_token stop_token);

    struct Impl;
    std::unique_ptr<Impl> implementation_;



};

class DatabaseQueue {
public:
    DatabaseQueue();
    bool Push(DatabaseTask task);
    bool Pop(std::stop_token stop_token, DatabaseTask& task);
    void Close();

private:
    std::mutex mutex_;                      // 큐 자료구조 접근을 보호하는 뮤텍스
    std::condition_variable_any condition_; // "태스크 도착/종료"를 알리는 조건 변수
    std::queue<DatabaseTask> tasks_;        // 대기 중인 태스크 큐
    bool closed_ = false;                   // 종료 플래그 (Push 거부 + Pop 종료 유도)
};

// 큐를 통해 SQLite 작업을 실행하고 결과를 동기 대기합니다. (읽기용)
DatabaseResult RunDatabaseTask(DatabaseQueue& queue,
                               std::function<DatabaseResult(DbHandle&)> execute);

// 큐를 통해 SQLite 쓰기(INSERT/UPDATE/DELETE)를 실행합니다. (fire-and-forget)
bool RunDatabaseWrite(DatabaseQueue& queue,
                      std::function<DatabaseResult(DbHandle&)> execute);

// key-value 설정 저장/조회 헬퍼 (settings 테이블 사용)
bool SaveSetting(DatabaseQueue& queue, const std::string& key, const std::string& value);
std::string LoadSetting(DatabaseQueue& queue, const std::string& key);

#endif  // SONAR_VALIDATOR_PROBER_DATABASE_SERVICE_HPP_