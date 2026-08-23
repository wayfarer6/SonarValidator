#include <iostream>
#include <memory>
#include <sqlite3.h>

struct ClosePDB
{
    void operator()(sqlite3 *db) const
    {
        if (db)
            sqlite3_close(db);
    }
};

struct CloseStmt
{
    void operator()(sqlite3_stmt *stmt) const
    {
        if (stmt)
            sqlite3_finalize(stmt);
    }
};

using DBPointer = std::unique_ptr<sqlite3, ClosePDB>;
using StmtPointer = std::unique_ptr<sqlite3_stmt, CloseStmt>;
// 예기치못한 이유로 stmt 실행중 함수 나가도 소멸 안전하게 하게
int main()
{
    DBPointer db; // 빈 스마트 포인터 생성
    StmtPointer stmt;
    char *err_msg = 0;

    // C++23 마법: std::out_ptr이 스마트 포인터를 C스타일
    // 이중 포인터로 안전하게 변환해 줌!
    int rc = sqlite3_open("test.db", std::out_ptr(db));

    if (rc != SQLITE_OK)
    {
        std::cerr << "Error\n";
        return 1;
    }

    sqlite3_prepare_v2(db.get(), "SELECT * FROM users", -1, std::out_ptr(stmt), nullptr);

    // db.get() sqlite3 * (원시포인터)
    // std::out_ptr  -> sqlite3** (이중 포인터)
    char *sql = "DROP TABLE IF EXISTS Cars;"
                "CREATE TABLE Cars(Id INT, Name TEXT, Price INT);"
                "INSERT INTO Cars VALUES(1, 'Audi', 52642);"
                "INSERT INTO Cars VALUES(2, 'Mercedes', 57127);"
                "INSERT INTO Cars VALUES(3, 'Skoda', 9000);"
                "INSERT INTO Cars VALUES(4, 'Volvo', 29000);"
                "INSERT INTO Cars VALUES(5, 'Bentley', 350000);"
                "INSERT INTO Cars VALUES(6, 'Citroen', 21000);"
                "INSERT INTO Cars VALUES(7, 'Hummer', 41400);"
                "INSERT INTO Cars VALUES(8, 'Volkswagen', 21600);";
    rc = sqlite3_exec(db.get(), sql, 0, 0, &err_msg);

    std::cout << "DB opened without raw pointers!\n";
    return 0;
}