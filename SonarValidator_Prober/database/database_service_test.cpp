#include <cassert>
#include <exception>
#include <filesystem>
#include <future>
#include <stdexcept>
#include <string>
#include <thread>

#include <sqlite3.h>

#include "database/database_service.hpp"

namespace fs = std::filesystem;

int main()
{
    const fs::path installer_path =
        fs::path(SONAR_VALIDATOR_PROBER_SOURCE_DIR) / "Installer";
    const fs::path template_path = installer_path / "default_template.sqlite";
    const fs::path test_database_path =
        installer_path / "default_template_test.sqlite";

    std::error_code copy_error;
    fs::copy_file(
        template_path,
        test_database_path,
        fs::copy_options::overwrite_existing,
        copy_error);
    assert(!copy_error);

    sqlite3 *raw_database = nullptr;
    const int db_result =
        sqlite3_open(test_database_path.c_str(), &raw_database);
    assert(db_result == SQLITE_OK);
    DbHandle database(raw_database);

    DatabaseQueue database_queue;
    std::jthread database_worker(
        [&database, &database_queue](std::stop_token stop_token)
        {
            DatabaseTask task;
            while (database_queue.Pop(stop_token, task))
            {
                try
                {
                    task.result.set_value(task.execute(database));
                }
                catch (...)
                {
                    task.result.set_exception(std::current_exception());
                }
            }
        });

    DatabaseTask task;
    task.execute = [](DbHandle& handle)
    {
        sqlite3* database = handle.get();
        if (database == nullptr)
        {
            return DatabaseResult{};
        }
        /*
        참고로 포인터 변수 자체만 고정하고 싶을 때는 constexpr char*를 쓰면 
        포인터 변수 자체가 상수(char* const)가 되지만, 문자열 리터럴의 
        내용 변경을 막기 위해 대다수는 가리키는 대상까지 const를 붙여 
        constexpr const char* 형태로 사용합니다
        
        */
        constexpr const char *agent_id = "test-agent-id";
        constexpr const char *agent_name = "test-agent";
        constexpr const char *os_name = "Debian GNU/Linux";
        constexpr const char *sql =
            "INSERT INTO Agent_info (agent_id, agent_name, os_name) "
            "VALUES (?, ?, ?);";

        sqlite3_stmt *statement = nullptr;
        if (sqlite3_prepare_v2(database, sql, -1, &statement, nullptr) != SQLITE_OK)
        {
            throw std::runtime_error(sqlite3_errmsg(database));
        }

        int bind_result = sqlite3_bind_text(
            statement, 1, agent_id, -1, SQLITE_STATIC);
        if (bind_result == SQLITE_OK)
        {
            bind_result = sqlite3_bind_text(
                statement, 2, agent_name, -1, SQLITE_STATIC);
        }
        if (bind_result == SQLITE_OK)
        {
            bind_result = sqlite3_bind_text(
                statement, 3, os_name, -1, SQLITE_STATIC);
        }
        const int step_result =
            bind_result == SQLITE_OK ? sqlite3_step(statement) : bind_result;
        sqlite3_finalize(statement);

        if (step_result != SQLITE_DONE)
        {
            throw std::runtime_error(sqlite3_errmsg(database));
        }

        DatabaseResult result;
        result.sql_task = sql;
        result.db_task_result.emplace_back(os_name);
        return result;
    };

    std::future<DatabaseResult> result = task.result.get_future();

    // 주의: assert(...) 안에 부작용이 있는 호출을 넣으면 안 된다.
    // Release 빌드(-DNDEBUG)에서 assert 가 통째로 제거되어 Push 가 실행되지 않고,
    // result.get() 이 영원히 대기한다(테스트가 타임아웃까지 멈춤).
    const bool pushed = database_queue.Push(std::move(task));
    assert(pushed);
    if (!pushed)
    {
        throw std::runtime_error("failed to push task into database queue");
    }

    const DatabaseResult insert_result = result.get();
    assert(insert_result.db_task_result.size() == 1);
    assert(insert_result.db_task_result.front() == "Debian GNU/Linux");

    database_queue.Close();
    database_worker.request_stop();
    return 0;
}