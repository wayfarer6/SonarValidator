#include <SQLiteCpp/SQLiteCpp.h>
#include <iostream>

int main() {
    try {
        // DB 연결 및 테이블 생성
        SQLite::Database db("test.db", SQLite::OPEN_READWRITE | SQLite::OPEN_CREATE);
        db.exec("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT)");

        // Parameter Binding & Insert
        SQLite::Statement query(db, "INSERT INTO users VALUES (?, ?)");
        query.bind(1, 101);
        query.bind(2, "Alice");
        query.exec();

        // Select & Fetch
        SQLite::Statement select(db, "SELECT id, name FROM users WHERE id = ?");
        select.bind(1, 101);
        
        while (select.executeStep()) {
            int id = select.getColumn(0);
            std::string name = select.getColumn(1);
            std::cout << id << ", " << name << "\n";
        }
    } catch (std::exception& e) {
        std::cout << "SQLite exception: " << e.what() << std::endl;
    }
}