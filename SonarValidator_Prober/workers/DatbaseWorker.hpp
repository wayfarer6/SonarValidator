// 데이터베이스 스레드: 큐에서 태스크를 하나씩 꺼내 순차 실행합니다.
// sqlite3 핸들을 오직 이 스레드만 만지므로 동시 접근 문제가 없습니다.
void DatabaseWorker(
    std::stop_token stop_token,
    DbHandle &db_handle,
    DatabaseQueue &database_queue)
{
    if (db_handle == nullptr)
    {
        return;
    }

    DatabaseTask task;
    while (database_queue.Pop(stop_token, task))
    {
        try
        {
            // 태스크의 람다를 실행하고 결과를 promise에 담아 호출자에게 돌려줍니다.
            task.result.set_value(task.execute(db_handle));
        }
        catch (...)
        {
            // 실행 중 예외가 나면 future.get() 쪽에 예외로 전달합니다.
            task.result.set_exception(std::current_exception());
        }
    }
}
