#include "database_service.hpp"


namespace{
    
}

DatabaseQueue::DatabaseQueue() = default;

bool DatabaseQueue::Push(DatabaseTask task)
{
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (closed_)
        {
            return false;
        }

        tasks_.push(std::move(task));
    }

    condition_.notify_one();
    return true;
}

bool DatabaseQueue::Pop(std::stop_token stop_token, DatabaseTask& task)
{
    std::unique_lock<std::mutex> lock(mutex_);

    const bool awakened = condition_.wait(
        lock,
        stop_token,
        [this] { return closed_ || !tasks_.empty(); });

    if (!awakened || tasks_.empty())
    {
        return false;
    }

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

    condition_.notify_all();
}