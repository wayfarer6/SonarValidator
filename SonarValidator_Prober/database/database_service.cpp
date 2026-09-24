#include "database/database_service.hpp"


namespace{
    
}

DatabaseQueue::DatabaseQueue() = default;

// 생산자(telemetry/management)가 태스크를 큐에 넣습니다.
bool DatabaseQueue::Push(DatabaseTask task)
{
    {
        // 큐는 여러 스레드가 동시에 건드릴 수 있으므로 뮤텍스로 보호합니다.
        std::lock_guard<std::mutex> lock(mutex_);
        if (closed_)
        {
            return false;
        }

        // 태스크 소유권을 큐로 넘깁니다. (복사가 아니라 이동이라 중복 실행이 없음)
        tasks_.push(std::move(task));
    } // 이 블록을 벗어나면 락이 자동 해제됩니다.

    // 대기 중인 소비자(DB 스레드) 하나를 깨웁니다.
    condition_.notify_one();
    return true;
}

bool DatabaseQueue::Pop(std::stop_token stop_token, DatabaseTask& task)
{
    // 조건 변수는 "잠들 때 락을 풀고, 깨어나면 다시 잠그는" 동작이 필요해서
    // unique_lock(락 해제/재획득 가능)을 사용합니다.
    std::unique_lock<std::mutex> lock(mutex_);

    // 아래 조건이 참이 될 때까지 잠듭니다.
    //   - 조건(람다): 큐가 닫혔거나(closed_) 태스크가 하나라도 있거나(!tasks_.empty())
    //   - stop_token: 셧다운 요청(request_stop)이 오면 즉시 깨어납니다.
    const bool awakened = condition_.wait(
        lock,
        stop_token,
        [this] { return closed_ || !tasks_.empty(); });

    // 깨어났지만 처리할 태스크가 없으면 종료(셧다운) 신호입니다.
    if (!awakened || tasks_.empty())
    {
        return false;
    }

    // 가장 앞의 태스크를 꺼내 호출자에게 소유권을 넘깁니다.
    task = std::move(tasks_.front());
    tasks_.pop();
    return true;
}

void DatabaseQueue::Close()
{
    {
        std::lock_guard<std::mutex> lock(mutex_);
        closed_ = true;
    }

    // 잠들어 있는 모든 소비자를 깨워 Pop이 false를 반환하고 루프를 종료하게 합니다.
    condition_.notify_all();
}

// 큐를 통해 SQL 작업을 실행하고, 완료될 때까지 기다렸다가 결과를 돌려줍니다. (읽기용)
DatabaseResult RunDatabaseTask(DatabaseQueue& queue,
                               std::function<DatabaseResult(DbHandle&)> execute)
{
    DatabaseTask task;
    // 실행할 일(람다)을 태스크에 담습니다.
    task.execute = std::move(execute);

    // promise에서 future를 미리 얻어 둡니다.
    // future.get()은 DB 스레드가 set_value()를 호출할 때까지 현재 스레드를 블로킹합니다.
    auto future = task.result.get_future();

    if (!queue.Push(std::move(task)))
    {
        return {};  // 큐가 닫혔으면 빈 결과를 반환합니다.
    }

    try
    {
        return future.get();  // DB 스레드의 실행 결과를 기다립니다.
    }
    catch (const std::exception&)
    {
        return {};  // 예외 시 빈 결과를 반환합니다.
    }
}

// 쓰기 작업을 큐에 넣기만 하고 결과는 기다리지 않습니다. (fire-and-forget)
bool RunDatabaseWrite(DatabaseQueue& queue,
                      std::function<DatabaseResult(DbHandle&)> execute)
{
    DatabaseTask task;
    task.execute = std::move(execute);
    return queue.Push(std::move(task));
}

bool SaveSetting(DatabaseQueue& queue, const std::string& key, const std::string& value)
{
    // [key, value]: 람다가 외부 변수 key/value를 "복사 캡처"해서,
    // 나중에 DB 스레드에서 실행될 때도 원본이 사라져도 안전하게 사용할 수 있게 합니다.
    // (DbHandle& handle): DB 스레드가 실제 sqlite3 핸들을 넘겨줍니다.
    // -> DatabaseResult: 람다의 반환 타입을 명시합니다.
    return RunDatabaseWrite(queue, [key, value](DbHandle& handle) -> DatabaseResult {
        sqlite3* database = handle.get();  // RAII 핸들에서 생 포인터를 꺼냅니다.
        DatabaseResult result;
        result.sql_task = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";

        if (database == nullptr)
        {
            return result;  // 핸들이 비어 있으면 아무것도 담지 않은 빈 결과를 반환합니다.
        }

        // SQL 문장을 준비합니다. '?'는 나중에 값을 바인딩할 자리표시자(placeholder)입니다.
        const char* sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?);";
        sqlite3_stmt* statement = nullptr;
        if (sqlite3_prepare_v2(database, sql, -1, &statement, nullptr) != SQLITE_OK)
        {
            return result;
        }

        // '?' 자리에 실제 값을 넣습니다. (문자열이라 bind_text)
        // SQLITE_TRANSIENT: SQLite가 값을 즉시 복사해 두므로 이 람다가 끝나도 안전합니다.
        sqlite3_bind_text(statement, 1, key.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(statement, 2, value.c_str(), -1, SQLITE_TRANSIENT);

        // 준비된 문장을 한 번 실행합니다. (INSERT 성공 시 SQLITE_DONE)
        if (sqlite3_step(statement) == SQLITE_DONE)
        {
            result.db_task_result.push_back("ok");
        }

        // 준비된 문장 자원을 해제합니다.
        sqlite3_finalize(statement);
        return result;
    });
}

std::string LoadSetting(DatabaseQueue& queue, const std::string& key)
{
    // RunDatabaseTask는 실행 결과를 블로킹으로 돌려줍니다.
    // [key]: key만 복사 캡처해서 DB 스레드에서 안전하게 읽습니다.
    const DatabaseResult result = RunDatabaseTask(queue, [key](DbHandle& handle) -> DatabaseResult {
        sqlite3* database = handle.get();
        DatabaseResult r;
        r.sql_task = "SELECT value FROM settings WHERE key = ?";

        if (database == nullptr)
        {
            return r;  // 핸들이 비어 있으면 빈 결과를 반환합니다.
        }

        const char* sql = "SELECT value FROM settings WHERE key = ?;";
        sqlite3_stmt* statement = nullptr;
        if (sqlite3_prepare_v2(database, sql, -1, &statement, nullptr) != SQLITE_OK)
        {
            return r;
        }

        // '?' 자리에 key 값을 바인딩합니다.
        sqlite3_bind_text(statement, 1, key.c_str(), -1, SQLITE_TRANSIENT);

        // SQLITE_ROW: 조회 결과 행이 하나 존재한다는 뜻입니다.
        if (sqlite3_step(statement) == SQLITE_ROW)
        {
            // 첫 번째 컬럼(0번) 값을 C 문자열로 읽어옵니다.
            const char* text = reinterpret_cast<const char*>(sqlite3_column_text(statement, 0));
            r.db_task_result.push_back(text != nullptr ? text : "");
        }

        sqlite3_finalize(statement);
        return r;
    });

    // 결과가 비어 있으면(키 없음 또는 오류) 빈 문자열을 돌려줍니다.
    if (result.db_task_result.empty())
    {
        return {};
    }
    return result.db_task_result.front();
}