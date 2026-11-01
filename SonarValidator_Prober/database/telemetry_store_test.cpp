#include <cassert>
#include <filesystem>
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

int g_failures = 0;

namespace fs = std::filesystem;
using Json = nlohmann::json;

namespace
{

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

}

int main()
{
    sqlite3* raw_database = nullptr;
    assert(sqlite3_open(":memory:", &raw_database) == SQLITE_OK);
    DbHandle database(raw_database);

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
        return 1;
    }

    assert(sqlite3_exec(database.get(), ddl.c_str(), nullptr, nullptr, nullptr) == SQLITE_OK);

    DatabaseService database_service(database);

    const std::string agent = "test-agent";
    const std::string collected_at = "2026-09-18T04:11:22Z";

    const Json route_status = Json::parse(R"({
        "routes": [
            {"protocol":"connected","selected":true,"fib":true,"prefix":"10.20.111.0/24",
             "metric":"110/200","next_hop":"","interface_name":"eth0","connected":true},
            {"protocol":"ospf","selected":true,"fib":true,"prefix":"10.99.10.0/24",
             "metric":"110/200","next_hop":"10.99.10.5","interface_name":"eth1","connected":false},
            {"protocol":"kernel"}
        ]})");
    assert(telemetry_store::SaveRouteStatus(database_service, agent, route_status, collected_at));

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
    assert(telemetry_store::SaveNicInfo(database_service, agent, nic_status, collected_at));

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

    if (ScalarLong(database.get(),
                   "SELECT COUNT(*) FROM nic_address "
                   "WHERE interface_name = 'eth1.131';") != 2)
    {
        std::cerr << "  [FAIL] nic_address interface fallback\n";
        ++g_failures;
    }

    if (ScalarLong(database.get(),
                   "SELECT COUNT(*) FROM nic_info WHERE flags LIKE '%BROADCAST%';") != 2)
    {
        std::cerr << "  [FAIL] nic_info flags serialization\n";
        ++g_failures;
    }

    const Json vlan_status = Json::parse(R"({
        "vlans": [
            {"vlan_id":8,"name":"VLAN8","status":"active","ports":["Cpu","Et2"]},
            {"vlan_id":9,"name":"VLAN9","status":"active"}
        ]})");
    assert(telemetry_store::SaveVlanStatus(database_service, agent, vlan_status, collected_at));

    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM vlan_status;") != 2)
    {
        std::cerr << "  [FAIL] vlan_status row count\n";
        ++g_failures;
    }

    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM vlan_table;") != 1 ||
        ScalarLong(database.get(), "SELECT vlan_id FROM vlan_table LIMIT 1;") != 8)
    {
        std::cerr << "  [FAIL] design-time vlan_table was modified\n";
        ++g_failures;
    }

    const Json trunk_status = Json::parse(R"({
        "ports": [
            {"name":"Ethernet1","mode":"trunk","access_vlan":99,
             "trunk_vlans":[111,112,{"start":200,"end":210}],"vlan_mode":"trunk","admin_enabled":true},
            {"name":"Ethernet2","mode":"access","access_vlan":8,"vlan_mode":"access","admin_enabled":false}
        ]})");
    assert(telemetry_store::SaveTrunkStatus(database_service, agent, trunk_status, collected_at));

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

    const Json arp_table = Json::parse(R"({
        "entries": [
            {"address":"10.0.9.1","mac":"0c:2d:07:65:99:f3","interface":"ens3",
             "state":"REACHABLE","age":"2:31:51","type":"ARPA","interfaces":["Vlan9","Ethernet3"]},
            {"address":"10.0.9.2","mac":"0c:2d:07:65:99:f4","interface":"ens3","state":"STALE"}
        ]})");
    assert(telemetry_store::SaveArpTable(database_service, agent, arp_table, collected_at));

    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM arp_table;") != 2)
    {
        std::cerr << "  [FAIL] arp_table row count\n";
        ++g_failures;
    }

    assert(telemetry_store::SaveRouteStatus(database_service, agent, Json::object()));
    assert(telemetry_store::SaveNicInfo(database_service, agent, Json::parse(R"({"parsed":false})")));
    assert(telemetry_store::SaveVlanStatus(database_service, agent, Json::parse(R"({"vlans":[]})")));
    assert(telemetry_store::SaveTrunkStatus(database_service, agent, Json(nullptr)));
    assert(telemetry_store::SaveArpTable(
        database_service, agent, Json::parse(R"({"entries":[{"address":"1.1.1.1"},null,42]})")));

    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM arp_table;") != 3)
    {
        std::cerr << "  [FAIL] arp_table tolerated-row handling\n";
        ++g_failures;
    }

    if (ScalarLong(database.get(), "SELECT COUNT(*) FROM route_table;") != 3 ||
        ScalarLong(database.get(), "SELECT COUNT(*) FROM vlan_status;") != 2)
    {
        std::cerr << "  [FAIL] empty payloads must not insert rows\n";
        ++g_failures;
    }

    const std::string stamp = telemetry_store::CurrentUtcTimestamp();
    if (stamp.size() != 20 || stamp.back() != 'Z')
    {
        std::cerr << "  [FAIL] CurrentUtcTimestamp format: " << stamp << '\n';
        ++g_failures;
    }

    const auto before_failed_batch = ScalarLong(database.get(), "SELECT COUNT(*) FROM route_table");
    assert(sqlite3_exec(database.get(),
        "CREATE TRIGGER reject_bad_route BEFORE INSERT ON route_table "
        "WHEN NEW.prefix='bad' BEGIN SELECT RAISE(ABORT,'test rejection'); END;",
        nullptr,nullptr,nullptr) == SQLITE_OK);
    assert(!telemetry_store::SaveRouteStatus(database_service, agent,
        Json::parse(R"({"routes":[{"prefix":"good"},{"prefix":"bad"}]})")));
    assert(ScalarLong(database.get(), "SELECT COUNT(*) FROM route_table") == before_failed_batch);
    database_service.Close();

    if (g_failures != 0)
    {
        std::cerr << "telemetry_store_test: " << g_failures << " failure(s)\n";
        return 1;
    }

    std::cout << "telemetry_store_test: all checks passed\n";
    return 0;
}
