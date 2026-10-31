#ifndef SONAR_VALIDATOR_PROBER_DATABASE_SERVICE_HPP_
#define SONAR_VALIDATOR_PROBER_DATABASE_SERVICE_HPP_

#include <functional>
#include <memory>
#include <mutex>
#include <sqlite3.h>
#include <string>
#include <vector>

struct DbHandler
{
    void operator()(sqlite3* database) const
    {
        if (database != nullptr) sqlite3_close(database);
    }
};
using DbHandle = std::unique_ptr<sqlite3, DbHandler>;

struct DatabaseResult
{
    std::vector<std::string> db_task_result;
    std::string sql_task;
};

class DatabaseService
{
public:
    explicit DatabaseService(DbHandle& database);
    DatabaseService(const DatabaseService&) = delete;
    DatabaseService& operator=(const DatabaseService&) = delete;
    DatabaseResult Execute(const std::function<DatabaseResult(DbHandle&)>& event);
    void Close();

private:
    DbHandle& database_;
    std::mutex mutex_;
    bool closed_ = false;
};

DatabaseResult RunDatabaseTask(DatabaseService& database,
    const std::function<DatabaseResult(DbHandle&)>& event);
bool RunDatabaseWrite(DatabaseService& database,
    const std::function<DatabaseResult(DbHandle&)>& event);
bool SaveSetting(DatabaseService& database, const std::string& key, const std::string& value);
std::string LoadSetting(DatabaseService& database, const std::string& key);
#endif
