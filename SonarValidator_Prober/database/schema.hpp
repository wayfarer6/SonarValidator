#ifndef SONAR_VALIDATOR_PROBER_DATABASE_SCHEMA_HPP_
#define SONAR_VALIDATOR_PROBER_DATABASE_SCHEMA_HPP_

#include <string>

// 프로버 로컬 SQLite 스키마(DDL)의 단일 진실(single source of truth)입니다.
//
// 같은 DDL 을 여러 곳에서 중복 정의하면 반드시 어긋나므로, 아래 두 곳이
// 이 파일의 문자열 하나만 참조합니다.
//   1) AppInitializer::InitializeDatabase() — 런타임 DB(prober_db.sqlite)에 적용
//   2) database/schema_dump.cpp           — 템플릿 DB 재생성용 출력
//        예) ./build/schema_dump | sqlite3 Installer/default_template.sqlite
//
// 모든 문장이 CREATE ... IF NOT EXISTS 이므로 몇 번을 실행해도 안전합니다(멱등).
namespace database_schema
{

// 테이블 + 인덱스 DDL 전체를 ';' 로 이어 붙인 문자열로 돌려줍니다.
const std::string& CreateTablesSql();

} // namespace database_schema

#endif // SONAR_VALIDATOR_PROBER_DATABASE_SCHEMA_HPP_
