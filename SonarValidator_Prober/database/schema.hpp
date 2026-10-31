#ifndef SONAR_VALIDATOR_PROBER_DATABASE_SCHEMA_HPP_
#define SONAR_VALIDATOR_PROBER_DATABASE_SCHEMA_HPP_

#include <string>










namespace database_schema
{

// 테이블 + 인덱스 DDL 전체를 ';' 로 이어 붙인 문자열로 돌려줍니다.
const std::string& CreateTablesSql();

} // namespace database_schema

#endif // SONAR_VALIDATOR_PROBER_DATABASE_SCHEMA_HPP_
