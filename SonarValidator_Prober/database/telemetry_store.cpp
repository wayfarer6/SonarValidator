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

// ---------------------------------------------------------------------------
//  JSON 접근 헬퍼 — 키가 없거나 타입이 달라도 예외를 던지지 않습니다.
//  (수집 루프가 파싱 실패로 멈추면 안 되므로 "없으면 건너뛴다" 를 기본으로 합니다.)
// ---------------------------------------------------------------------------

// object 에서 key 를 찾습니다. 없거나 null 이면 nullptr 입니다.
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

// 배열이면 포인터를, 아니면 nullptr 을 돌려줍니다.
const Json* FindArray(const Json& object, const char* key)
{
    const Json* value = FindMember(object, key);
    return (value != nullptr && value->is_array()) ? value : nullptr;
}

// 문자열이면 그대로, 숫자/불리언이면 문자열로 바꿔 돌려줍니다. 그 외에는 빈 문자열.
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
        // metric 이 "110/200" 같은 문자열로 오지만 간혹 숫자로 올 수 있습니다.
        return value.dump();
    }
    return {};
}

// 문자열 리스트(flags, ports, trunk_vlans 등)를 JSON 배열 문자열로 직렬화합니다.
// 배열이 아니면 nullptr 을 돌려줘 호출부가 건너뛰게 합니다.
std::string ListToJsonText(const Json& object, const char* key)
{
    const Json* array = FindArray(object, key);
    if (array == nullptr)
    {
        return "[]";
    }
    return array->dump();
}

// ---------------------------------------------------------------------------
//  바인딩 값 표현 — "값 없음(NULL)" 과 "0/빈 문자열" 을 구분해야 하므로
//  present 플래그를 둡니다.
// ---------------------------------------------------------------------------
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
        column.present = true;  // 빈 문자열도 "값 있음" 으로 저장합니다.
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

    // JSON 에서 문자열/숫자를 읽어 TEXT 로 담습니다. 없으면 NULL.
    static ColumnValue FromText(const Json& object, const char* key)
    {
        const Json* value = FindMember(object, key);
        return value == nullptr ? ColumnValue{} : Text(JsonToText(*value));
    }

    // JSON 에서 정수를 읽습니다. 숫자가 아니면 NULL. (예: "index")
    static ColumnValue FromInteger(const Json& object, const char* key)
    {
        const Json* value = FindMember(object, key);
        if (value == nullptr || !value->is_number_integer())
        {
            return ColumnValue{};
        }
        return Integer(value->get<long long>());
    }

    // JSON 불리언을 0/1 INTEGER 로 저장합니다. (예: selected, connected)
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

    // 배열을 JSON 텍스트로 저장합니다. 키가 없어도 "[]" 을 남깁니다.
    static ColumnValue FromList(const Json& object, const char* key)
    {
        return Text(ListToJsonText(object, key));
    }
};

using Row = std::vector<ColumnValue>;

// 두 후보 중 "값이 있는" 첫 번째를 고릅니다.
// 파서/벤더에 따라 같은 의미의 키 이름이 다를 때 사용합니다.
//   예: 라우트 목적지가 prefix(FRR/Cisco) 또는 destination(Linux)
inline ColumnValue FirstOf(ColumnValue first, ColumnValue second)
{
    return first.present ? first : second;
}

// ---------------------------------------------------------------------------
//  공통 저장 루틴
//
//  - 행이 없으면 아무것도 하지 않고 true 를 돌려줍니다(호출부가 분기할 필요 없음).
//  - 한 스냅샷을 트랜잭션 하나로 묶습니다. 중간 실패 시 롤백해 반쪽 저장을 막습니다.
//  - 여기서는 예외를 던지지 않고 실패를 false 로 알립니다.
// ---------------------------------------------------------------------------
bool PushRows(DatabaseQueue& database_queue,
              const char* insert_sql,
              const std::string& agent,
              const std::string& collected_at,
              std::vector<Row> rows)
{
    if (rows.empty())
    {
        return true;  // 저장할 행이 없습니다.
    }

    // 태스크 실행 중 원본이 사라져도 안전하도록 값은 전부 복사 캡처합니다.
    DatabaseTask task;
    task.execute = [insert_sql, agent, collected_at, rows = std::move(rows)](DbHandle& handle)
        -> DatabaseResult {
        // DB 워커가 set_value 를 못 하면(예외로 빠지면) 호출부 future 가 영원히
        // 대기하므로, 이 람다는 어떤 경우에도 예외를 밖으로 내보내지 않습니다.
        DatabaseResult result;
        result.sql_task = insert_sql;

        sqlite3* database = handle.get();
        if (database == nullptr)
        {
            return result;
        }

        // 아래 모든 실패는 finalize 후 조용히 return 합니다.
        sqlite3_stmt* statement = nullptr;
        if (sqlite3_prepare_v2(database, insert_sql, -1, &statement, nullptr) != SQLITE_OK)
        {
            return result;
        }

        // 앞 부분(agent, collected_at)은 모든 INSERT 문에서 공통입니다.
        const int first_row_parameter = 3;
        if (sqlite3_bind_text(statement, 1, agent.c_str(), -1, SQLITE_TRANSIENT) != SQLITE_OK ||
            sqlite3_bind_text(statement, 2, collected_at.c_str(), -1, SQLITE_TRANSIENT) != SQLITE_OK)
        {
            sqlite3_finalize(statement);
            return result;
        }

        bool began_transaction = sqlite3_exec(database, "BEGIN", nullptr, nullptr, nullptr) == SQLITE_OK;
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
            }
        }

        if (all_rows_saved)
        {
            result.db_task_result.push_back("ok");
        }
        return result;
    };

    return database_queue.Push(std::move(task));
}

// ---------------------------------------------------------------------------
//  테이블별 행 추출
// ---------------------------------------------------------------------------

// route_table: 한 번 수집분의 라우팅 엔트리를 NULLABLE 컬럼 8개로 옮깁니다.
// (agent, collected_at 은 PushRows 가 채웁니다.)
constexpr const char* kInsertRouteSql =
    "INSERT INTO route_table (agent, collected_at, protocol, prefix, next_hop, metric,"
    " interface_name, selected, fib, connected) VALUES (?,?,?,?,?,?,?,?,?,?)";

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
            continue;  // 형식이 다른 원소는 저장하지 않습니다.
        }

        rows.push_back(Row{
            ColumnValue::FromText(route, "protocol"),
            // Linux `ip route show` 파서는 목적지를 destination 으로,
            // FRR/Cisco 파서는 prefix 로 내보낸다. 두 표기를 모두 받는다.
            FirstOf(ColumnValue::FromText(route, "prefix"),
                    ColumnValue::FromText(route, "destination")),
            // 다음 홉도 via / next_hop 두 표기가 있다.
            FirstOf(ColumnValue::FromText(route, "next_hop"),
                    ColumnValue::FromText(route, "via")),
            ColumnValue::FromText(route, "metric"),
            ColumnValue::FromText(route, "interface_name"),
            ColumnValue::FromBoolean(route, "selected"),
            ColumnValue::FromBoolean(route, "fib"),
            ColumnValue::FromBoolean(route, "connected"),
        });
    }
    return rows;
}

// nic_info: 인터페이스 한 개 = 한 행. link_type 은 파서 버전에 따라 없을 수 있습니다.
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

// nic_address: 인터페이스별 addresses 배열을 펼칩니다.
// 주소 객체 자체에 "interface" 가 있으면 그 값을, 없으면 부모 인터페이스 이름을 씁니다.
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

        // 부모 인터페이스 이름은 주소 객체에 "interface" 가 없을 때 대신 씁니다.
        std::string interface_name;
        if (const Json* name = FindMember(interface, "name"))
        {
            interface_name = JsonToText(*name);
        }

        const Json* addresses = FindArray(interface, "addresses");
        if (addresses == nullptr)
        {
            continue;  // 주소가 없는 인터페이스는 nic_info 에만 남습니다.
        }

        for (const Json& address : *addresses)
        {
            if (!address.is_object())
            {
                continue;
            }

            // 주소 객체의 "interface" 우선, 없으면 부모 이름.
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

// collected_at 이 비어 있으면 현재 UTC 시각으로 채웁니다.
std::string ResolveCollectedAt(const std::string& collected_at)
{
    return collected_at.empty() ? telemetry_store::CurrentUtcTimestamp() : collected_at;
}

} // namespace

namespace telemetry_store
{

// 수집 시각은 로컬 타임존이 아니라 UTC 로 통일합니다.
// (여러 장비의 수집 결과를 서버에서 시간순으로 합칠 때 혼선이 없도록)
std::string CurrentUtcTimestamp()
{
    const std::time_t now = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
    std::tm utc_time{};
    gmtime_r(&now, &utc_time);

    char buffer[32] = {};
    std::strftime(buffer, sizeof(buffer), "%Y-%m-%dT%H:%M:%SZ", &utc_time);
    return buffer;
}

bool EnqueueRouteStatusSave(DatabaseQueue& database_queue,
                            const std::string& agent,
                            const Json& route_status,
                            const std::string& collected_at)
{
    try
    {
        return PushRows(database_queue,
                        kInsertRouteSql,
                        agent,
                        ResolveCollectedAt(collected_at),
                        ExtractRouteRows(route_status));
    }
    catch (const std::exception& exception)
    {
        std::cerr << "[WARN] route status save skipped: " << exception.what() << '\n';
        return false;
    }
}

bool EnqueueNicInfoSave(DatabaseQueue& database_queue,
                        const std::string& agent,
                        const Json& nic_status,
                        const std::string& collected_at)
{
    try
    {
        // 인터페이스와 주소가 같은 시각을 쓰도록 여기서 한 번만 시각을 정합니다.
        const std::string snapshot_time = ResolveCollectedAt(collected_at);
        const bool interfaces_saved = PushRows(
            database_queue, kInsertNicInfoSql, agent, snapshot_time, ExtractNicInfoRows(nic_status));
        const bool addresses_saved = PushRows(database_queue,
                                              kInsertNicAddressSql,
                                              agent,
                                              snapshot_time,
                                              ExtractNicAddressRows(nic_status));
        return interfaces_saved && addresses_saved;
    }
    catch (const std::exception& exception)
    {
        std::cerr << "[WARN] nic info save skipped: " << exception.what() << '\n';
        return false;
    }
}

bool EnqueueVlanStatusSave(DatabaseQueue& database_queue,
                           const std::string& agent,
                           const Json& vlan_status,
                           const std::string& collected_at)
{
    try
    {
        return PushRows(database_queue,
                        kInsertVlanSql,
                        agent,
                        ResolveCollectedAt(collected_at),
                        ExtractVlanRows(vlan_status));
    }
    catch (const std::exception& exception)
    {
        std::cerr << "[WARN] vlan status save skipped: " << exception.what() << '\n';
        return false;
    }
}

bool EnqueueTrunkStatusSave(DatabaseQueue& database_queue,
                            const std::string& agent,
                            const Json& trunk_status,
                            const std::string& collected_at)
{
    try
    {
        return PushRows(database_queue,
                        kInsertTrunkSql,
                        agent,
                        ResolveCollectedAt(collected_at),
                        ExtractTrunkRows(trunk_status));
    }
    catch (const std::exception& exception)
    {
        std::cerr << "[WARN] trunk status save skipped: " << exception.what() << '\n';
        return false;
    }
}

bool EnqueueArpTableSave(DatabaseQueue& database_queue,
                         const std::string& agent,
                         const Json& arp_table,
                         const std::string& collected_at)
{
    try
    {
        return PushRows(database_queue,
                        kInsertArpSql,
                        agent,
                        ResolveCollectedAt(collected_at),
                        ExtractArpRows(arp_table));
    }
    catch (const std::exception& exception)
    {
        std::cerr << "[WARN] arp table save skipped: " << exception.what() << '\n';
        return false;
    }
}

} // namespace telemetry_store
