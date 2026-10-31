#include <iostream>
#include <string>

#include "database/schema.hpp"

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
