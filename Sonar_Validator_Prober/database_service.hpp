#include <condition_variable>
#include <functional>
#include <memory>
#include <mutex>
#include <queue>
#include <sqlite3.h>
#include <string>
#include <thread>
#include <utility>


using DatabaseTask = std::function<void(sqlite3*)>;

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
    bool Push(DatabaseTask task);
    bool Pop(std::stop_token stop_token, DatabaseTask& task);
    void Close();

private:
    std::mutex mutex_;
    std::condition_variable_any condition_;
    std::queue<DatabaseTask> tasks_;
    bool closed_ = false;
};