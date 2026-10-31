#include "database/database_service.hpp"
#include <iostream>

DatabaseService::DatabaseService(DbHandle& database) : database_(database)
{
    if (database_) sqlite3_busy_timeout(database_.get(), 2000);
}

DatabaseResult DatabaseService::Execute(
    const std::function<DatabaseResult(DbHandle&)>& event)
{
    std::lock_guard lock(mutex_);
    if (closed_ || !database_) return {};
    return event(database_);
}

void DatabaseService::Close()
{
    std::lock_guard lock(mutex_);
    closed_ = true;
}

DatabaseResult RunDatabaseTask(DatabaseService& database,
    const std::function<DatabaseResult(DbHandle&)>& event)
{
    return database.Execute(event);
}

bool RunDatabaseWrite(DatabaseService& database,
    const std::function<DatabaseResult(DbHandle&)>& event)
{
    try
    {
        return !database.Execute(event).db_task_result.empty();
    }
    catch (const std::exception& error)
    {
        std::cerr << "[DATABASE] write failed: " << error.what() << '\n';
        return false;
    }
}

bool SaveSetting(DatabaseService& queue, const std::string& key, const std::string& value)
{
    return RunDatabaseWrite(queue, [key, value](DbHandle& handle) -> DatabaseResult {
        sqlite3* database = handle.get();
        DatabaseResult result;
        result.sql_task = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";

        if (database == nullptr)
        {
            return result;
        }

        const char* sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?);";
        sqlite3_stmt* statement = nullptr;
        if (sqlite3_prepare_v2(database, sql, -1, &statement, nullptr) != SQLITE_OK)
        {
            return result;
        }

        sqlite3_bind_text(statement, 1, key.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(statement, 2, value.c_str(), -1, SQLITE_TRANSIENT);

        if (sqlite3_step(statement) == SQLITE_DONE)
        {
            result.db_task_result.push_back("ok");
        }

        sqlite3_finalize(statement);
        return result;
    });
}

std::string LoadSetting(DatabaseService& queue, const std::string& key)
{

    const DatabaseResult result = RunDatabaseTask(queue, [key](DbHandle& handle) -> DatabaseResult {
        sqlite3* database = handle.get();
        DatabaseResult r;
        r.sql_task = "SELECT value FROM settings WHERE key = ?";

        if (database == nullptr)
        {
            return r;
        }

        const char* sql = "SELECT value FROM settings WHERE key = ?;";
        sqlite3_stmt* statement = nullptr;
        if (sqlite3_prepare_v2(database, sql, -1, &statement, nullptr) != SQLITE_OK)
        {
            return r;
        }

        sqlite3_bind_text(statement, 1, key.c_str(), -1, SQLITE_TRANSIENT);

        if (sqlite3_step(statement) == SQLITE_ROW)
        {

            const char* text = reinterpret_cast<const char*>(sqlite3_column_text(statement, 0));
            r.db_task_result.push_back(text != nullptr ? text : "");
        }

        sqlite3_finalize(statement);
        return r;
    });

    if (result.db_task_result.empty())
    {
        return {};
    }
    return result.db_task_result.front();
}
