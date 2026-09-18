#include "schema.hpp"

namespace database_schema
{

// ---------------------------------------------------------------------------
//  스키마 버전
//
//  DDL 을 바꿀 때는 여기 문자열도 함께 고치고, 아래 표의 "변경 이력"에 남깁니다.
// ---------------------------------------------------------------------------
const std::string& CreateTablesSql()
{
    // clang-format off
    static const std::string kCreateTables =
        // --- 기존 테이블 ---------------------------------------------------
        // 텔레메트리 원본(JSON 스냅샷)과 key-value 설정입니다.
        // 다른 코드/테스트가 쓰고 있으므로 정의를 바꾸지 않습니다.
        "CREATE TABLE IF NOT EXISTS nic_status ("
        "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "agent TEXT NOT NULL,"
        "collected_at TEXT DEFAULT CURRENT_TIMESTAMP,"
        "payload TEXT NOT NULL"
        ");"
        "CREATE TABLE IF NOT EXISTS settings ("
        "key TEXT PRIMARY KEY,"
        "value TEXT NOT NULL"
        ");"

        // --- 신규: 수집 네트워크 상태 -------------------------------------
        // 모두 (agent, collected_at) 로 "어느 장비를 언제 수집했는지" 를 묶습니다.
        // 같은 시각 값이면 한 번의 수집(스냅샷)에서 나온 행으로 볼 수 있습니다.
        // 시각은 수집기가 직접 넣는 UTC 문자열(예: 2026-09-18T04:11:22Z)입니다.

        // 1) 라우팅 테이블 — `show ip route` 한 줄 = 한 행
        "CREATE TABLE IF NOT EXISTS route_table ("
        "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "agent TEXT NOT NULL,"
        "collected_at TEXT NOT NULL,"
        "protocol TEXT,"             // ospf, connected, kernel ...
        "prefix TEXT,"               // 10.20.111.0/24
        "next_hop TEXT,"             // 10.99.10.5 (direct 는 NULL/빈 값)
        "metric TEXT,"               // "110/200" 처럼 문자열로 오는 경우가 있어 TEXT
        "interface_name TEXT,"
        "selected INTEGER,"          // FIB 설치 여부(선택된 경로) 0/1
        "fib INTEGER,"               // fib 플래그 0/1
        "connected INTEGER"          // directly connected 여부 0/1
        ");"
        "CREATE INDEX IF NOT EXISTS idx_route_table_snapshot "
        "ON route_table (agent, collected_at);"

        // 2) NIC 정보 — `ip a` 의 인터페이스 한 개 = 한 행
        "CREATE TABLE IF NOT EXISTS nic_info ("
        "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "agent TEXT NOT NULL,"
        "collected_at TEXT NOT NULL,"
        "name TEXT,"                 // eth1.131
        "index_number INTEGER,"      // ifindex (index 는 SQL 키워드라 이름 회피)
        "mac TEXT,"
        "mtu TEXT,"                  // "1500" 문자열로 오는 경우가 있어 TEXT
        "state TEXT,"                // UP/DOWN/UNKNOWN
        "flags TEXT,"                // JSON 배열 문자열 ["BROADCAST","UP",...]
        "link_type TEXT,"            // ether, loopback ...
        "parent TEXT"                // eth1 (VLAN 서브인터페이스의 부모)
        ");"
        "CREATE INDEX IF NOT EXISTS idx_nic_info_snapshot "
        "ON nic_info (agent, collected_at);"

        // 3) NIC 주소 — 인터페이스의 주소 한 개 = 한 행 (nic_info 1:N)
        "CREATE TABLE IF NOT EXISTS nic_address ("
        "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "agent TEXT NOT NULL,"
        "collected_at TEXT NOT NULL,"
        "interface_name TEXT,"
        "family TEXT,"               // inet / inet6
        "address TEXT,"
        "prefix_len INTEGER,"
        "scope TEXT"                 // global, link, host
        ");"
        "CREATE INDEX IF NOT EXISTS idx_nic_address_snapshot "
        "ON nic_address (agent, collected_at);"

        // 4) VLAN 런타임 상태 — `show vlan brief` 한 줄 = 한 행
        //
        // 주의: 기존 vlan_table 은 "설계(토폴로지)" 스키마(vlan_id PK + subnet_id)라
        // 같은 테이블을 재사용하면 설계 데이터를 덮어씁니다. 그래서 이름을 달리해
        // 런타임 수집 결과를 별도로 쌓습니다.
        "CREATE TABLE IF NOT EXISTS vlan_status ("
        "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "agent TEXT NOT NULL,"
        "collected_at TEXT NOT NULL,"
        "vlan_id INTEGER,"
        "name TEXT,"
        "status TEXT,"               // active / act/unsup ...
        "ports TEXT"                 // JSON 배열 문자열 ["Cpu","Et2"]
        ");"
        "CREATE INDEX IF NOT EXISTS idx_vlan_status_snapshot "
        "ON vlan_status (agent, collected_at);"

        // 5) 트렁크/스위치 포트 상태 — `show interfaces switchport` 포트 1개 = 1행
        "CREATE TABLE IF NOT EXISTS trunk_status ("
        "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "agent TEXT NOT NULL,"
        "collected_at TEXT NOT NULL,"
        "port_name TEXT,"            // 인터페이스 이름 (Ethernet1, eth1 ...)
        "mode TEXT,"                 // access / trunk / ...
        "access_vlan INTEGER,"
        "trunk_vlans TEXT,"          // JSON 배열 문자열 [111,112] (범위는 {"start","end"})
        "vlan_mode TEXT,"
        "admin_enabled INTEGER"      // 관리자 enabled 0/1
        ");"
        "CREATE INDEX IF NOT EXISTS idx_trunk_status_snapshot "
        "ON trunk_status (agent, collected_at);"

        // 6) ARP/이웃 테이블 — `ip neigh show` 항목 1개 = 1행
        "CREATE TABLE IF NOT EXISTS arp_table ("
        "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "agent TEXT NOT NULL,"
        "collected_at TEXT NOT NULL,"
        "address TEXT,"
        "mac TEXT,"
        "interface_name TEXT,"
        "state TEXT,"                // REACHABLE / STALE ...
        "age TEXT,"                  // "2:31:51" (경과 시간 표기 그대로)
        "type TEXT,"                 // ARPA ...
        "interfaces TEXT"            // LAG 구성원 등 부가 목록(JSON 배열 문자열)
        ");"
        "CREATE INDEX IF NOT EXISTS idx_arp_table_snapshot "
        "ON arp_table (agent, collected_at);";
    // clang-format on

    return kCreateTables;
}

} // namespace database_schema
