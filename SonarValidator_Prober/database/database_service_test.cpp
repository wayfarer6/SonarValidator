#include "database/database_service.hpp"
#include <atomic>
#include <filesystem>
#include <future>
#include <iostream>
#include <stdexcept>
#include <thread>
#include <unistd.h>

void Check(bool value, const char* message)
{
    if (!value) throw std::runtime_error(message);
}

int main()
{
    char path[] = "/tmp/sonar-db-events-XXXXXX";
    const int file = mkstemp(path);
    Check(file >= 0, "temporary database");
    close(file);
    try
    {
        sqlite3* raw = nullptr;
        Check(sqlite3_open(path, &raw) == SQLITE_OK, "open database");
        DbHandle handle(raw);
        Check(sqlite3_exec(raw, "CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT)",
            nullptr,nullptr,nullptr) == SQLITE_OK, "create schema");
        const auto count_threads = [] {
            return std::distance(std::filesystem::directory_iterator("/proc/self/task"),
                                 std::filesystem::directory_iterator{});
        };
        const auto before = count_threads();
        DatabaseService database(handle);
        Check(count_threads() == before, "database created a worker thread");
        const auto caller = std::this_thread::get_id();
        database.Execute([&](DbHandle&) {
            Check(std::this_thread::get_id() == caller, "event ran on another thread");
            return DatabaseResult{};
        });
        std::atomic<int> failures{0};
        {
            std::vector<std::jthread> producers;
            for (int i=0;i<4;++i) producers.emplace_back([&,i] {
                for(int j=0;j<20;++j)
                {
                    const std::string key=std::to_string(i)+":"+std::to_string(j);
                    if (!SaveSetting(database,key,"committed")) ++failures;
                }
            });
        }
        Check(failures == 0, "concurrent events lost writes");
        Check(LoadSetting(database,"3:19") == "committed", "last write not visible");
        std::promise<void> entered, release;
        auto released = release.get_future().share();
        auto writer = std::async(std::launch::async, [&] {
            return database.Execute([&](DbHandle& db) {
                Check(sqlite3_exec(db.get(), "BEGIN; INSERT INTO settings VALUES('inflight','committed');",
                    nullptr,nullptr,nullptr) == SQLITE_OK,"inflight begin");
                entered.set_value();
                released.wait();
                Check(sqlite3_exec(db.get(),"COMMIT",nullptr,nullptr,nullptr)==SQLITE_OK,"inflight commit");
                return DatabaseResult{{"ok"},"COMMIT"};
            });
        });
        entered.get_future().wait();
        auto closer = std::async(std::launch::async, [&] { database.Close(); });
        const bool waited = closer.wait_for(std::chrono::milliseconds(50)) == std::future_status::timeout;
        release.set_value();
        writer.get(); closer.get();
        Check(waited,"close skipped an active transaction");
        Check(!SaveSetting(database,"after-close","rejected"), "write accepted after close");
        handle.reset();
        Check(sqlite3_open(path,&raw) == SQLITE_OK, "reopen database");
        handle.reset(raw);
        sqlite3_stmt* stmt=nullptr;
        Check(sqlite3_prepare_v2(raw,"SELECT count(*) FROM settings",-1,&stmt,nullptr)==SQLITE_OK,"count prepare");
        Check(sqlite3_step(stmt)==SQLITE_ROW && sqlite3_column_int(stmt,0)==81,"commits not durable after reopen");
        sqlite3_finalize(stmt);
        handle.reset();
        unlink(path);
        std::cout << "[ok] no DB thread; 80 concurrent writes + in-flight commit durable; close waits and rejects writes\n";
        return 0;
    }
    catch(const std::exception& error)
    {
        unlink(path);
        std::cerr << "[FAIL] " << error.what() << '\n';
        return 1;
    }
}
