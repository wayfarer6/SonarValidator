#include "utils/log.hpp"
#include "database/telemetry_store.hpp"

#include <chrono>
#include <ctime>
#include <iostream>
#include <utility>
#include <vector>

#include <sqlite3.h>

using Json = nlohmann::json;

namespace
{

const Json* FindMember(const Json& object, const char* key)
{
    if (!object.is_object())
    {
        return nullptr;
    }

    const auto iterator = object.find(key);
    if (iterator == object.end() || iterator->is_null())
    {
        return nullptr;
    }
    return &(*iterator);
}

const Json* FindArray(const Json& object, const char* key)
{
    const Json* value = FindMember(object, key);
    return (value != nullptr && value->is_array()) ? value : nullptr;
}

std::string JsonToText(const Json& value)
{
    if (value.is_string())
    {
        return value.get<std::string>();
    }
    if (value.is_boolean())
    {
        return value.get<bool>() ? "1" : "0";
    }
    if (value.is_number())
    {

        return value.dump();
    }
    return {};
}

std::string ListToJsonText(const Json& object, const char* key)
{
    const Json* array = FindArray(object, key);
    if (array == nullptr)
    {
        return "[]";
    }
    return array->dump();
}

struct ColumnValue
{
    enum class Kind
    {
        kText,
        kInteger
    };

    Kind kind = Kind::kText;
    std::string text;
    long long number = 0;
    bool present = false;

    static ColumnValue Text(std::string value)
    {
        ColumnValue column;
        column.kind = Kind::kText;
        column.text = std::move(value);
        column.present = true;
        return column;
    }

    static ColumnValue Integer(long long value)
    {
        ColumnValue column;
        column.kind = Kind::kInteger;
        column.number = value;
        column.present = true;
        return column;
    }

    static ColumnValue FromText(const Json& object, const char* key)
    {
        const Json* value = FindMember(object, key);
        return value == nullptr ? ColumnValue{} : Text(JsonToText(*value));
    }

    static ColumnValue FromInteger(const Json& object, const char* key)
    {
        const Json* value = FindMember(object, key);
        if (value == nullptr || !value->is_number_integer())
        {
            return ColumnValue{};
        }
        return Integer(value->get<long long>());
    }

    static ColumnValue FromBoolean(const Json& object, const char* key)
    {
        const Json* value = FindMember(object, key);
        if (value == nullptr)
        {
            return ColumnValue{};
        }
        if (value->is_boolean())
        {
            return Integer(value->get<bool>() ? 1 : 0);
        }
        if (value->is_number_integer())
        {
            return Integer(value->get<long long>() != 0 ? 1 : 0);
        }
        if (value->is_string())
        {
            const std::string raw = value->get<std::string>();
            return Integer(raw == "1" || raw == "true" ? 1 : 0);
        }
        return ColumnValue{};
    }

    static ColumnValue FromList(const Json& object, const char* key)
    {
        return Text(ListToJsonText(object, key));
    }
};

using Row = std::vector<ColumnValue>;

inline ColumnValue FirstOf(ColumnValue first, ColumnValue second)
{
    return first.present ? first : second;
}

bool SaveRows(DatabaseService& database_service,
              const char* insert_sql,
              const std::string& agent,
              const std::string& collected_at,
              std::vector<Row> rows)
{
    if (rows.empty())
    {
        return true;
    }

    return RunDatabaseWrite(database_service, [insert_sql, agent, collected_at, rows = std::move(rows)](DbHandle& handle)
        -> DatabaseResult {

        DatabaseResult result;
        result.sql_task = insert_sql;

        sqlite3* database = handle.get();
        if (database == nullptr)
        {
            return result;
        }

        sqlite3_stmt* statement = nullptr;
        if (sqlite3_prepare_v2(database, insert_sql, -1, &statement, nullptr) != SQLITE_OK)
        {
            return result;
        }

        const int first_row_parameter = 3;
        if (sqlite3_bind_text(statement, 1, agent.c_str(), -1, SQLITE_TRANSIENT) != SQLITE_OK ||
            sqlite3_bind_text(statement, 2, collected_at.c_str(), -1, SQLITE_TRANSIENT) != SQLITE_OK)
        {
            sqlite3_finalize(statement);
            return result;
        }

        const bool began_transaction = sqlite3_exec(database, "BEGIN", nullptr, nullptr, nullptr) == SQLITE_OK;
        if (!began_transaction) return result;
        bool all_rows_saved = true;

        for (const Row& row : rows)
        {
            sqlite3_reset(statement);
            sqlite3_clear_bindings(statement);
            sqlite3_bind_text(statement, 1, agent.c_str(), -1, SQLITE_TRANSIENT);
            sqlite3_bind_text(statement, 2, collected_at.c_str(), -1, SQLITE_TRANSIENT);

            bool bind_ok = true;
            for (std::size_t index = 0; index < row.size(); ++index)
            {
                const ColumnValue& column = row[index];
                const int parameter = static_cast<int>(index) + first_row_parameter;
                if (!column.present)
                {
                    bind_ok = sqlite3_bind_null(statement, parameter) == SQLITE_OK;
                }
                else if (column.kind == ColumnValue::Kind::kInteger)
                {
                    bind_ok = sqlite3_bind_int64(statement, parameter, column.number) == SQLITE_OK;
                }
                else
                {
                    bind_ok = sqlite3_bind_text(
                                  statement, parameter, column.text.c_str(), -1, SQLITE_TRANSIENT) ==
                              SQLITE_OK;
                }

                if (!bind_ok)
                {
                    break;
                }
            }

            if (!bind_ok || sqlite3_step(statement) != SQLITE_DONE)
            {
                all_rows_saved = false;
                break;
            }
        }

        sqlite3_finalize(statement);

        if (began_transaction)
        {
            const char* finish = all_rows_saved ? "COMMIT" : "ROLLBACK";
            if (sqlite3_exec(database, finish, nullptr, nullptr, nullptr) != SQLITE_OK)
            {
                all_rows_saved = false;
                sqlite3_exec(database, "ROLLBACK", nullptr, nullptr, nullptr);
            }
        }

        if (all_rows_saved)
        {
            result.db_task_result.push_back("ok");
        }
        return result;
    });
}

constexpr const char* kInsertRouteSql =
    "INSERT INTO route_table (agent, collected_at, protocol, prefix, next_hop, metric,"
    " interface_name, selected, fib, connected, distance, metric_raw)"
    " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

std::vector<Row> ExtractRouteRows(const Json& route_status)
{
    std::vector<Row> rows;
    const Json* routes = FindArray(route_status, "routes");
    if (routes == nullptr)
    {
        return rows;
    }

    for (const Json& route : *routes)
    {
        if (!route.is_object())
        {
            continue;
        }

        rows.push_back(Row{
            ColumnValue::FromText(route, "protocol"),

            FirstOf(ColumnValue::FromText(route, "prefix"),
                    ColumnValue::FromText(route, "destination")),

            FirstOf(ColumnValue::FromText(route, "next_hop"),
                    ColumnValue::FromText(route, "via")),

            ColumnValue::FromInteger(route, "metric"),
            ColumnValue::FromText(route, "interface_name"),
            ColumnValue::FromBoolean(route, "selected"),
            ColumnValue::FromBoolean(route, "fib"),
            ColumnValue::FromBoolean(route, "connected"),

            ColumnValue::FromInteger(route, "distance"),

            ColumnValue::FromText(route, "metric_raw"),
        });
    }
    return rows;
}

constexpr const char* kInsertNicInfoSql =
    "INSERT INTO nic_info (agent, collected_at, name, index_number, mac, mtu, state,"
    " flags, link_type, parent) VALUES (?,?,?,?,?,?,?,?,?,?)";

std::vector<Row> ExtractNicInfoRows(const Json& nic_status)
{
    std::vector<Row> rows;
    const Json* interfaces = FindArray(nic_status, "interfaces");
    if (interfaces == nullptr)
    {
        return rows;
    }

    for (const Json& interface : *interfaces)
    {
        if (!interface.is_object())
        {
            continue;
        }

        rows.push_back(Row{
            ColumnValue::FromText(interface, "name"),
            ColumnValue::FromInteger(interface, "index"),
            ColumnValue::FromText(interface, "mac"),
            ColumnValue::FromText(interface, "mtu"),
            ColumnValue::FromText(interface, "state"),
            ColumnValue::FromList(interface, "flags"),
            ColumnValue::FromText(interface, "link_type"),
            ColumnValue::FromText(interface, "parent"),
        });
    }
    return rows;
}

constexpr const char* kInsertNicAddressSql =
    "INSERT INTO nic_address (agent, collected_at, interface_name, family, address,"
    " prefix_len, scope) VALUES (?,?,?,?,?,?,?)";

std::vector<Row> ExtractNicAddressRows(const Json& nic_status)
{
    std::vector<Row> rows;
    const Json* interfaces = FindArray(nic_status, "interfaces");
    if (interfaces == nullptr)
    {
        return rows;
    }

    for (const Json& interface : *interfaces)
    {
        if (!interface.is_object())
        {
            continue;
        }

        std::string interface_name;
        if (const Json* name = FindMember(interface, "name"))
        {
            interface_name = JsonToText(*name);
        }

        const Json* addresses = FindArray(interface, "addresses");
        if (addresses == nullptr)
        {
            continue;
        }

        for (const Json& address : *addresses)
        {
            if (!address.is_object())
            {
                continue;
            }

            ColumnValue owner = ColumnValue::FromText(address, "interface");
            if (!owner.present && !interface_name.empty())
            {
                owner = ColumnValue::Text(interface_name);
            }

            rows.push_back(Row{
                std::move(owner),
                ColumnValue::FromText(address, "family"),
                ColumnValue::FromText(address, "address"),
                ColumnValue::FromInteger(address, "prefix_len"),
                ColumnValue::FromText(address, "scope"),
            });
        }
    }
    return rows;
}

constexpr const char* kInsertVlanSql =
    "INSERT INTO vlan_status (agent, collected_at, vlan_id, name, status, ports)"
    " VALUES (?,?,?,?,?,?)";

std::vector<Row> ExtractVlanRows(const Json& vlan_status)
{
    std::vector<Row> rows;
    const Json* vlans = FindArray(vlan_status, "vlans");
    if (vlans == nullptr)
    {
        return rows;
    }

    for (const Json& vlan : *vlans)
    {
        if (!vlan.is_object())
        {
            continue;
        }

        rows.push_back(Row{
            ColumnValue::FromInteger(vlan, "vlan_id"),
            ColumnValue::FromText(vlan, "name"),
            ColumnValue::FromText(vlan, "status"),
            ColumnValue::FromList(vlan, "ports"),
        });
    }
    return rows;
}

constexpr const char* kInsertTrunkSql =
    "INSERT INTO trunk_status (agent, collected_at, port_name, mode, access_vlan,"
    " trunk_vlans, vlan_mode, admin_enabled) VALUES (?,?,?,?,?,?,?,?)";

std::vector<Row> ExtractTrunkRows(const Json& trunk_status)
{
    std::vector<Row> rows;
    const Json* ports = FindArray(trunk_status, "ports");
    if (ports == nullptr)
    {
        return rows;
    }

    for (const Json& port : *ports)
    {
        if (!port.is_object())
        {
            continue;
        }

        rows.push_back(Row{
            ColumnValue::FromText(port, "name"),
            ColumnValue::FromText(port, "mode"),
            ColumnValue::FromInteger(port, "access_vlan"),
            ColumnValue::FromList(port, "trunk_vlans"),
            ColumnValue::FromText(port, "vlan_mode"),
            ColumnValue::FromBoolean(port, "admin_enabled"),
        });
    }
    return rows;
}

constexpr const char* kInsertArpSql =
    "INSERT INTO arp_table (agent, collected_at, address, mac, interface_name, state,"
    " age, type, interfaces) VALUES (?,?,?,?,?,?,?,?,?)";

std::vector<Row> ExtractArpRows(const Json& arp_table)
{
    std::vector<Row> rows;
    const Json* entries = FindArray(arp_table, "entries");
    if (entries == nullptr)
    {
        return rows;
    }

    for (const Json& entry : *entries)
    {
        if (!entry.is_object())
        {
            continue;
        }

        rows.push_back(Row{
            ColumnValue::FromText(entry, "address"),
            ColumnValue::FromText(entry, "mac"),
            ColumnValue::FromText(entry, "interface"),
            ColumnValue::FromText(entry, "state"),
            ColumnValue::FromText(entry, "age"),
            ColumnValue::FromText(entry, "type"),
            ColumnValue::FromList(entry, "interfaces"),
        });
    }
    return rows;
}

std::string ResolveCollectedAt(const std::string& collected_at)
{
    return collected_at.empty() ? telemetry_store::CurrentUtcTimestamp() : collected_at;
}

}

namespace telemetry_store
{

std::string CurrentUtcTimestamp()
{
    const std::time_t now = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
    std::tm utc_time{};
    gmtime_r(&now, &utc_time);

    char buffer[32] = {};
    std::strftime(buffer, sizeof(buffer), "%Y-%m-%dT%H:%M:%SZ", &utc_time);
    return buffer;
}

bool SaveRouteStatus(DatabaseService& database_service,
                            const std::string& agent,
                            const Json& route_status,
                            const std::string& collected_at)
{
    try
    {
        return SaveRows(database_service,
                        kInsertRouteSql,
                        agent,
                        ResolveCollectedAt(collected_at),
                        ExtractRouteRows(route_status));
    }
    catch (const std::exception& exception)
    {
        SV_LOG_WARN << "[WARN] route status save skipped: " << exception.what() << '\n';
        return false;
    }
}

bool SaveNicInfo(DatabaseService& database_service,
                        const std::string& agent,
                        const Json& nic_status,
                        const std::string& collected_at)
{
    try
    {

        const std::string snapshot_time = ResolveCollectedAt(collected_at);
        const bool interfaces_saved = SaveRows(
            database_service, kInsertNicInfoSql, agent, snapshot_time, ExtractNicInfoRows(nic_status));
        const bool addresses_saved = SaveRows(database_service,
                                              kInsertNicAddressSql,
                                              agent,
                                              snapshot_time,
                                              ExtractNicAddressRows(nic_status));
        return interfaces_saved && addresses_saved;
    }
    catch (const std::exception& exception)
    {
        SV_LOG_WARN << "[WARN] nic info save skipped: " << exception.what() << '\n';
        return false;
    }
}

bool SaveVlanStatus(DatabaseService& database_service,
                           const std::string& agent,
                           const Json& vlan_status,
                           const std::string& collected_at)
{
    try
    {
        return SaveRows(database_service,
                        kInsertVlanSql,
                        agent,
                        ResolveCollectedAt(collected_at),
                        ExtractVlanRows(vlan_status));
    }
    catch (const std::exception& exception)
    {
        SV_LOG_WARN << "[WARN] vlan status save skipped: " << exception.what() << '\n';
        return false;
    }
}

bool SaveTrunkStatus(DatabaseService& database_service,
                            const std::string& agent,
                            const Json& trunk_status,
                            const std::string& collected_at)
{
    try
    {
        return SaveRows(database_service,
                        kInsertTrunkSql,
                        agent,
                        ResolveCollectedAt(collected_at),
                        ExtractTrunkRows(trunk_status));
    }
    catch (const std::exception& exception)
    {
        SV_LOG_WARN << "[WARN] trunk status save skipped: " << exception.what() << '\n';
        return false;
    }
}

bool SaveArpTable(DatabaseService& database_service,
                         const std::string& agent,
                         const Json& arp_table,
                         const std::string& collected_at)
{
    try
    {
        return SaveRows(database_service,
                        kInsertArpSql,
                        agent,
                        ResolveCollectedAt(collected_at),
                        ExtractArpRows(arp_table));
    }
    catch (const std::exception& exception)
    {
        SV_LOG_WARN << "[WARN] arp table save skipped: " << exception.what() << '\n';
        return false;
    }
}

}
