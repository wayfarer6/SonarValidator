#include <cassert>
#include <filesystem>
#include <future>
#include <iostream>
#include <stop_token>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

#include <nlohmann/json.hpp>
#include <sqlite3.h>

#include "database/database_service.hpp"
#include "database/schema.hpp"
#include "database/telemetry_store.hpp"

// 수집 상태 저장 헬퍼(telemetry_store) 단위 테스트입니다.
//  - schema.cpp 의 DDL 로 메모리 DB 를 만들고
//  - 파서가 실제로 내놓는 모양의 JSON 을 저장한 뒤
//  - 행 수/값이 기대와 맞는지, 그리고 키가 없는 JSON 이 조용히 통과하는지 봅니다.
// 네트워크/장비가 전혀 필요 없습니다.
//
// 주의: 표준 assert() 를 쓰지 않습니다. 이 프로젝트의 기본 빌드가 Release(-DNDEBUG)
// 라서 assert 는 통째로 제거되고, 그러면 테스트가 아무것도 검사하지 않습니다.
// (repository memory 에 있는 database_service_test 함정과 같은 원인)
#undef assert
#define assert(condition)                                                  \
    do                                                                     \
    {                                                                      \
        if (!(condition))                                                  \
        {                                                                  \
            std::cerr << "  [FAIL] " << #condition << " at line "          \
                      << __LINE__ << '\n';                                 \
            ++g_failures;                                                  \
        }                                                                  \
    } while (false)

// 실패 누적 카운터(항상 평가되는 assert 대체 매크로가 사용).
int g_failures = 0;

namespace fs = std::filesystem;
using Json = nlohmann::json;

namespace
{

// DB 워커 스레드를 붙인 테스트용 큐입니다.
// (실제 프로버의 DatabaseService 와 같은 역할을 최소한으로 흉내 냅니다.)
class TestDatabaseWorker
{
public:
    TestDatabaseWorker(DbHandle& database, DatabaseQueue& queue)
        : worker_([&database, &queue](std::stop_token stop_token)
                  {
                      DatabaseTask task;
                      while (queue.Pop(stop_token, task))
                      {
                          try
                          {
                              task.result.set_value(task.execute(database));
                          }
                          catch (...)
                          {
                              task.result.set_exception(std::current_exception());
                          }
                      }
                  })
    {
    }

    void Stop()
    {
        worker_.request_stop();
    }

private:
    std::jthread worker_;
};

// 단일 정수 값을 읽습니다. (COUNT(*) 용)
long long ScalarLong(sqlite3* database, const char* sql)
{
    sqlite3_stmt* statement = nullptr;
    if (sqlite3_prepare_v2(database, sql, -1, &statement, nullptr) != SQLITE_OK)
    {
        return -1;
    }

    long long value = -1;
    if (sqlite3_step(statement) == SQLITE_ROW)
    {
        value = sqlite3_column_int64(statement, 0);
    }
    sqlite3_finalize(statement);
    return value;
}

// 큐에 넣은 태스크가 실제로 반영될 때까지 기다립니다.
// (Push 는 비동기라서 바로 조회하면 아직 INSERT 전일 수 있음)
// 같은 큐에 태스크를 하나 더 넣고 결과를 기다리면, FIFO 이므로 앞선
// 저장 태스크가 모두 끝난 뒤에 실행됩니다.
void DrainQueue(DatabaseQueue& queue)
{
    const DatabaseResult result = RunDatabaseTask(queue, [](DbHandle& handle) -> DatabaseResult
                                                  {
                                                      DatabaseResult ping;
                                                      ping.sql_task = "SELECT 1";
                                                      if (handle.get() != nullptr)
                                                      {
                                                          ping.db_task_result.push_back("ok");
                                                      }
                                                      return ping;
                                                  });
    assert(!result.db_task_result.empty());
}

} // namespace

int main()
{
    // --- 1) 메모리 DB 준비 + 스키마 적용 (파일을 만들지 않아 부작용이 없음) ---
    sqlite3* raw_database = nullptr;
    assert(sqlite3_open(":memory:", &raw_database) == SQLITE_OK);
    DbHandle database(raw_database);

    // 템플릿(Installer/default_template.sqlite)에만 있는 "설계용" 테이블을 흉내 냅니다.
    // 우리 DDL 이 이 테이블을 건드리지 않는지(삭제/변경/오염 없음) 확인하기 위함입니다.
    const char* kDesignTimeTable =
        "CREATE TABLE vlan_table (subnet_id INTEGER, vlan_id INTEGER NOT NULL PRIMARY KEY);"
        "INSERT INTO vlan_table (subnet_id, vlan_id) VALUES (1, 8);";
    if (sqlite3_exec(database.get(), kDesignTimeTable, nullptr, nullptr, nullptr) != SQLITE_OK)
    {
        std::cerr << "  [FAIL] could not prepare design-time vlan_table\n";
        return 1;
    }

    char* error_message = nullptr;
    const std::string& ddl = database_schema::CreateTablesSql();
    const int schema_result =
        sqlite3_exec(database.get(), ddl.c_str(), nullptr, nullptr, &error_message);
    if (schema_result != SQLITE_OK)
    {
        std::cerr << "  [FAIL] schema apply: "
                  << (error_message != nullptr ? error_message : sqlite3_errmsg(database.get()))
                  << '\n';
        if (error_message != nullptr)
        {
            sqlite3_free(error_message);
        }
        return 1;  // 스키마가 없으면 아래 검사는 의미가 없습니다.
    }

    // 멱등성: 같은 DDL 을 두 번 실행해도 실패하지 않아야 합니다.
    assert(sqlite3_exec(database.get(), ddl.c_str(), nullptr, nullptr, nullptr) == SQLITE_OK);

    DatabaseQueue database_queue;
    TestDatabaseWorker worker(database, database_queue);

    const std::string agent = "test-agent";
    const std::string collected_at = "2026-09-18T04:11:22Z";


    // --- 2) 라우팅 테이블 ---
    // "metric":"110/200" 처럼 문자열로 오는 값, connected 는 불리언인 경우를 넣습니다.
    const Json route_status = Json::parse(R"({
        "routes": [
            {"protocol":"connected","selected":true,"fib":true,"prefix":"10.20.111.0/24",
             "metric":"110/200","next_hop":"","interface_name":"eth0","connected":true},
            {"protocol":"ospf","selected":true,"fib":true,"prefix":"10.99.10.0/24",
             "metric":"110/200","next_hop":"10.99.10.5","interface_name":"eth1","connected":false},
            {"protocol":"kernel"}
        ]})");
    assert(telemetry_store::EnqueueRouteStatusSave(database_queue, agent, route_status, collected_at));
    DrainQueue(database_queue);

    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM route_table;") != 3)
    {
        std::cerr << "  [FAIL] route_table row count\n";
        ++g_failures;
    }
    if (ScalarLong(database.get(),
                   "SELECT COUNT(*) FROM route_table WHERE connected = 1 AND selected = 1;") != 1)
    {
        std::cerr << "  [FAIL] route_table flags\n";
        ++g_failures;
    }

    // --- 3) NIC + 주소 ---
    // 주소 객체에 "interface" 가 있는 경우/없는 경우를 모두 넣습니다.
    const Json nic_status = Json::parse(R"({
        "interfaces": [
            {"index":2,"name":"eth1.131","parent":"eth1","mac":"02:42:7c:24:78:01",
             "mtu":"1500","state":"UP","flags":["BROADCAST","MULTICAST","UP"],
             "link_type":"ether",
             "addresses":[
                {"family":"inet","address":"10.10.131.1","prefix_len":24,"scope":"global",
                 "interface":"eth1.131"},
                {"family":"inet6","address":"fe80::1","prefix_len":64,"scope":"link"}
             ]},
            {"index":3,"name":"eth0","mac":"02:42:00:00:00:01","mtu":"1500","state":"DOWN",
             "flags":["BROADCAST"],"link_type":"ether"}
        ]})");
    assert(telemetry_store::EnqueueNicInfoSave(database_queue, agent, nic_status, collected_at));
    DrainQueue(database_queue);

    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM nic_info;") != 2)
    {
        std::cerr << "  [FAIL] nic_info row count\n";
        ++g_failures;
    }
    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM nic_address;") != 2)
    {
        std::cerr << "  [FAIL] nic_address row count\n";
        ++g_failures;
    }
    // 부모 인터페이스 이름이 fallback 으로 들어가야 합니다.
    if (ScalarLong(database.get(),
                   "SELECT COUNT(*) FROM nic_address "
                   "WHERE interface_name = 'eth1.131';") != 2)
    {
        std::cerr << "  [FAIL] nic_address interface fallback\n";
        ++g_failures;
    }
    // flags 는 JSON 배열 문자열로 남아야 합니다.
    if (ScalarLong(database.get(),
                   "SELECT COUNT(*) FROM nic_info WHERE flags LIKE '%BROADCAST%';") != 2)
    {
        std::cerr << "  [FAIL] nic_info flags serialization\n";
        ++g_failures;
    }

    // --- 4) VLAN ---
    const Json vlan_status = Json::parse(R"({
        "vlans": [
            {"vlan_id":8,"name":"VLAN8","status":"active","ports":["Cpu","Et2"]},
            {"vlan_id":9,"name":"VLAN9","status":"active"}
        ]})");
    assert(telemetry_store::EnqueueVlanStatusSave(database_queue, agent, vlan_status, collected_at));
    DrainQueue(database_queue);

    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM vlan_status;") != 2)
    {
        std::cerr << "  [FAIL] vlan_status row count\n";
        ++g_failures;
    }
    // 기존 설계용 vlan_table 은 그대로 남아 있어야 합니다. (행 1개, 값 8)
    // 런타임 수집 결과는 vlan_status 에만 쌓입니다.
    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM vlan_table;") != 1 ||
        ScalarLong(database.get(), "SELECT vlan_id FROM vlan_table LIMIT 1;") != 8)
    {
        std::cerr << "  [FAIL] design-time vlan_table was modified\n";
        ++g_failures;
    }

    // --- 5) 트렁크/스위치 포트 ---
    // trunk_vlans 에 범위 객체가 섞여 와도 텍스트로만 저장되어야 합니다.
    const Json trunk_status = Json::parse(R"({
        "ports": [
            {"name":"Ethernet1","mode":"trunk","access_vlan":99,
             "trunk_vlans":[111,112,{"start":200,"end":210}],"vlan_mode":"trunk","admin_enabled":true},
            {"name":"Ethernet2","mode":"access","access_vlan":8,"vlan_mode":"access","admin_enabled":false}
        ]})");
    assert(telemetry_store::EnqueueTrunkStatusSave(database_queue, agent, trunk_status, collected_at));
    DrainQueue(database_queue);

    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM trunk_status;") != 2)
    {
        std::cerr << "  [FAIL] trunk_status row count\n";
        ++g_failures;
    }
    if (ScalarLong(database.get(),
                   "SELECT COUNT(*) FROM trunk_status WHERE trunk_vlans LIKE '%200%';") != 1)
    {
        std::cerr << "  [FAIL] trunk_vlans serialization\n";
        ++g_failures;
    }

    // --- 6) ARP ---
    const Json arp_table = Json::parse(R"({
        "entries": [
            {"address":"10.0.9.1","mac":"0c:2d:07:65:99:f3","interface":"ens3",
             "state":"REACHABLE","age":"2:31:51","type":"ARPA","interfaces":["Vlan9","Ethernet3"]},
            {"address":"10.0.9.2","mac":"0c:2d:07:65:99:f4","interface":"ens3","state":"STALE"}
        ]})");
    assert(telemetry_store::EnqueueArpTableSave(database_queue, agent, arp_table, collected_at));
    DrainQueue(database_queue);

    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM arp_table;") != 2)
    {
        std::cerr << "  [FAIL] arp_table row count\n";
        ++g_failures;
    }

    // --- 7) 누락/빈입력 내성: 예외 없이 통과해야 합니다 ---
    assert(telemetry_store::EnqueueRouteStatusSave(database_queue, agent, Json::object()));
    assert(telemetry_store::EnqueueNicInfoSave(database_queue, agent, Json::parse(R"({"parsed":false})")));
    assert(telemetry_store::EnqueueVlanStatusSave(database_queue, agent, Json::parse(R"({"vlans":[]})")));
    assert(telemetry_store::EnqueueTrunkStatusSave(database_queue, agent, Json(nullptr)));
    assert(telemetry_store::EnqueueArpTableSave(
        database_queue, agent, Json::parse(R"({"entries":[{"address":"1.1.1.1"},null,42]})")));
    DrainQueue(database_queue);

    // null/숫자 원소는 건너뛰고 실제 object 행만 저장되어야 합니다.
    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM arp_table;") != 3)
    {
        std::cerr << "  [FAIL] arp_table tolerated-row handling\n";
        ++g_failures;
    }
    // 위 세 번의 빈 호출로 행이 더 늘지 않았는지 확인합니다.
    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM route_table;") != 3 ||
        ScalarLong(database.get(), "SELECT COUNT(*) FROM vlan_status;") != 2)
    {
        std::cerr << "  [FAIL] empty payloads must not insert rows\n";
        ++g_failures;
    }

    // --- 8) collected_at 자동 채움 ---
    const std::string stamp = telemetry_store::CurrentUtcTimestamp();
    if (stamp.size() != 20 || stamp.back() != 'Z')
    {
        std::cerr << "  [FAIL] CurrentUtcTimestamp format: " << stamp << '\n';
        ++g_failures;
    }

    database_queue.Close();
    worker.Stop();

    if (g_failures != 0)
    {
        std::cerr << "telemetry_store_test: " << g_failures << " failure(s)\n";
        return 1;
    }

    std::cout << "telemetry_store_test: all checks passed\n";
    return 0;
}
