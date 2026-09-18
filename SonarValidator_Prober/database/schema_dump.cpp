#include <iostream>
#include <string>

#include "database/schema.hpp"

// 스키마 DDL 을 표준 출력으로 내보내는 아주 작은 도구입니다.
// 템플릿 DB 를 코드와 같은 내용으로 맞출 때 사용합니다.
//   ./build/schema_dump | sqlite3 Installer/default_template.sqlite
//
// DDL 은 한 문자열이라 그대로 찍으면 한 줄로 보입니다. 눈으로 확인하기 좋게
// ';' 단위로 줄을 나눠서 출력합니다(내용은 동일하므로 파이프 결과는 같음).
int main()
{
    const std::string& ddl = database_schema::CreateTablesSql();

    std::string statement;
    for (const char character : ddl)
    {
        statement.push_back(character);
        if (character == ';')
        {
            std::cout << statement << '\n';
            statement.clear();
        }
    }

    if (!statement.empty())
    {
        std::cout << statement << '\n';
    }
    return 0;
}
