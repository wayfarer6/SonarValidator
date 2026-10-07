#include <iostream>
#include <string>

#include "database/schema.hpp"

// 스키마 DDL 을 표준 출력으로 내보내는 아주 작은 도구입니다.
// 생성된 CREATE TABLE IF NOT EXISTS 문은 기존 테이블의 컬럼/타입을 마이그레이션하지 않습니다.
// 따라서 기존 템플릿 DB에 파이프하는 것만으로는 오래된 테이블 정의가 갱신되지 않습니다.
// 템플릿 변경 시에는 별도 마이그레이션을 적용하거나 새 DB에서 템플릿을 재생성해야 합니다.
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
