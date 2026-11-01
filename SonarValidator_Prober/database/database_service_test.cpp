#include "database/database_service.hpp"
#include <atomic>
#include <filesystem>
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
        std::atomic<bool> entered{false}, release{false}, close_started{false}, closed{false};
        bool committed = false;
        std::jthread writer([&](std::stop_token stop) {
            database.Execute([&](DbHandle& db) {
                const bool begun = sqlite3_exec(db.get(),
                    "BEGIN; INSERT INTO settings VALUES('inflight','committed');",
                    nullptr, nullptr, nullptr) == SQLITE_OK;
                entered = true;
                while (!release && !stop.stop_requested())
                    std::this_thread::sleep_for(std::chrono::milliseconds(1));
                committed = begun && sqlite3_exec(db.get(), "COMMIT", nullptr, nullptr, nullptr) == SQLITE_OK;
                return DatabaseResult{};
            });
        });
        const auto wait_for = [](const std::atomic<bool>& flag) {
            const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(2);
            while (!flag && std::chrono::steady_clock::now() < deadline)
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
            return flag.load();
        };
        const bool began = wait_for(entered);
        if (!began) { release = true; writer.request_stop(); }
        Check(began, "writer did not start");
        std::jthread closer([&] { close_started = true; database.Close(); closed = true; });
        const bool closing = wait_for(close_started);
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
        const bool waited = !closed;
        release = true;
        writer.join();
        closer.join();
        Check(closing && waited, "close skipped an active transaction");
        Check(committed, "in-flight transaction was not committed");
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
