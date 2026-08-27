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

using DbHandle = std::unique_ptr<sqlite3, DbHandler>;

struct DatabaseResult
{
    std::vector<std::string> db_task_result;
    std::string sql_task;
};

struct DatabaseTask
{
    std::function<DatabaseResult(sqlite3 *)> execute;
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
    std::mutex mutex_;
    std::condition_variable_any condition_;
    std::queue<DatabaseTask> tasks_;
    bool closed_ = false;
};