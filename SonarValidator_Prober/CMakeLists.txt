cmake_minimum_required(VERSION 3.20)

project(SonarValidatorProber LANGUAGES CXX)

enable_testing()

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(SQLite3 REQUIRED)
find_package(nlohmann_json 3.11 QUIET)

if(NOT nlohmann_json_FOUND)
    include(FetchContent)
    FetchContent_Declare(
        nlohmann_json
        URL https://github.com/nlohmann/json/releases/download/v3.11.3/json.tar.xz
    )
    FetchContent_MakeAvailable(nlohmann_json)
endif()

# =============================================================================
#  ANTLR4 (벤더 CLI 조회 출력 파서)
#
#  프로버는 조회 명령어 출력을 파싱해 JSON 으로 서버에 올린다.
#  문법은 parser/grammar/*.g4 이고, 생성 코드는 빌드 시 만든다.
#
#  툴체인(jar + C++ 런타임)이 없으면 SONAR_ANTLR4_AVAILABLE=FALSE 가 되고
#  cli_output_parser 없이 기존 수집 경로만으로 빌드된다(빌드 실패 방지).
#    - jar      : ANTLR4_JAR          (기본 탐색: ~/tools/antlr.jar 등)
#    - 런타임    : ANTLR4_RUNTIME_ROOT (기본 탐색: ~/tools/antlr4-install 등)
# =============================================================================
set(SONAR_ANTLR4_GENERATED_DIR "${CMAKE_CURRENT_SOURCE_DIR}/parser/generated")
include(${CMAKE_CURRENT_SOURCE_DIR}/parser/cmake/FindANTLR4Runtime.cmake)

set(SONAR_ANTLR4_GENERATED_SOURCES "")
set(SONAR_CLI_PARSER_SOURCES "")

if(SONAR_ANTLR4_AVAILABLE)
    include(${CMAKE_CURRENT_SOURCE_DIR}/parser/cmake/Antlr4Generate.cmake)

    foreach(_grammar
            IpAddr
            OvsTopology
            FrrRouter
            NftablesRule
            SwitchTopology)
        sonar_add_antlr4_grammar(
            "${CMAKE_CURRENT_SOURCE_DIR}/parser/grammar/${_grammar}.g4")
    endforeach()

    set(SONAR_CLI_PARSER_SOURCES
        parser/cli_output_parser.cpp
        ${SONAR_ANTLR4_GENERATED_SOURCES})
endif()

# 수집기(collector) — 조회 명령 실행 + ANTLR 파서 호출.
# 파서를 직접 호출하므로 ANTLR4 툴체인이 있을 때만 컴파일한다.
set(SONAR_COLLECTOR_SOURCES "")
if(SONAR_ANTLR4_AVAILABLE)
    set(SONAR_COLLECTOR_SOURCES
        collector/command_collector.cpp)
endif()

add_executable(sonar_validator_prober
    main.cpp
    prober_config.cpp
    database/database_service.cpp
    database/schema.cpp
    database/telemetry_store.cpp
    init.cpp
    management_service.cpp
    telemetry/telemetry_service.hpp
    telemetry/telemetry_service.cpp
    telemetry/telemetry_monitor.hpp
    telemetry/telemetry_monitor.cpp
    router/routing_table.hpp
    router/routing_table.cpp
    switch/switch.cpp
    switch/switch.hpp
    firewall/firewall.hpp
    firewall/firewall.cpp
    network.hpp
    device_type.hpp
    envelope.hpp
    policy/policy_json.hpp
    policy/policy_receiver.hpp
    policy/policy_receiver.cpp
    terminal_session.hpp
    terminal_session.cpp
    vm/vm_service.hpp
    vm/vm_service.cpp
    offline/offline_export.hpp
    offline/offline_export.cpp
    collector/command_collector.hpp
    ${SONAR_COLLECTOR_SOURCES}
    ${SONAR_CLI_PARSER_SOURCES}
)

target_include_directories(sonar_validator_prober PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/collector
    ${CMAKE_CURRENT_SOURCE_DIR}/database
    ${CMAKE_CURRENT_SOURCE_DIR}/offline
    ${CMAKE_CURRENT_SOURCE_DIR}/router
    ${CMAKE_CURRENT_SOURCE_DIR}/switch
    ${CMAKE_CURRENT_SOURCE_DIR}/firewall
    ${CMAKE_CURRENT_SOURCE_DIR}/telemetry
    ${CMAKE_CURRENT_SOURCE_DIR}/policy
    ${CMAKE_CURRENT_SOURCE_DIR}/parser
)

if(SONAR_ANTLR4_AVAILABLE)
    target_include_directories(sonar_validator_prober PRIVATE
        ${SONAR_ANTLR4_GENERATED_DIR}/grammar
        ${ANTLR4_RUNTIME_ROOT}/include/antlr4-runtime
    )
    target_link_libraries(sonar_validator_prober PRIVATE ${SONAR_ANTLR4_RUNTIME_LIB})
    target_compile_definitions(sonar_validator_prober PRIVATE SONAR_HAS_ANTLR4=1)
else()
    target_compile_definitions(sonar_validator_prober PRIVATE SONAR_HAS_ANTLR4=0)
endif()

target_link_libraries(sonar_validator_prober PRIVATE
    SQLite::SQLite3
    nlohmann_json::nlohmann_json
)

add_executable(prober_config_test
    prober_config_test.cpp
    prober_config.cpp
)

add_executable(database_service_test
    database/database_service_test.cpp
    database/database_service.cpp
)

# 수집 상태(라우팅/NIC/VLAN/트렁크/ARP) 저장 헬퍼 검증 테스트.
# schema.cpp 로 테이블을 만든 뒤 실제로 INSERT 되는지, 키가 없을 때
# 조용히 건너뛰는지(예외 없음)를 확인합니다.
add_executable(telemetry_store_test
    database/telemetry_store_test.cpp
    database/telemetry_store.cpp
    database/schema.cpp
    database/database_service.cpp
)

# 스키마 DDL 을 표준 출력으로 내보내는 도구.
# 템플릿 DB 를 코드와 같은 내용으로 맞출 때 사용합니다:
#   ./build/schema_dump | sqlite3 Installer/default_template.sqlite
add_executable(schema_dump
    database/schema_dump.cpp
    database/schema.cpp
)

target_include_directories(schema_dump PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

add_executable(routing_table_test
    router/routing_table_test.cpp
    router/routing_table.cpp
)

add_executable(
    telemetry_service_test
    telemetry/telemetry_service_test.cpp
    telemetry/telemetry_service.cpp
)

add_executable(
    switch_test
    switch/switch_test.cpp
    switch/switch.cpp
    router/routing_table.cpp
)

# 봉투(통신 프로토콜) 단위 테스트. 네트워크 없이 생성/파싱 계약만 검증합니다.
add_executable(
    envelope_test
    envelope_test.cpp
)

# 오프라인 스냅샷 export 단위 테스트.
# 서버에 연결할 수 없는 장비에서 설정을 파일로 남기는 경로를 검증합니다.
# 네트워크/SQLite 불필요 → CI 에서 항상 실행됩니다.
add_executable(
    offline_export_test
    offline/offline_export_test.cpp
    offline/offline_export.cpp
)

target_include_directories(offline_export_test PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/offline
)

target_link_libraries(offline_export_test PRIVATE
    nlohmann_json::nlohmann_json
)

add_test(NAME offline_export_test COMMAND offline_export_test)

# 통신 통합 테스트. mock 서버(Agent_Test/mock_API) 또는 실제 백엔드를 상대로
# hello -> ack, policy-request -> policy-response 왕복을 검증합니다.
# 서버가 없으면 SKIP(종료 코드 0)이라 CI를 막지 않습니다.
add_executable(
    management_service_integration_test
    management_service_integration_test.cpp
    management_service.cpp
    terminal_session.cpp
    switch/switch.cpp
    firewall/firewall.cpp
    router/routing_table.cpp
)



target_link_libraries(database_service_test PRIVATE
    SQLite::SQLite3
)

target_link_libraries(telemetry_store_test PRIVATE
    SQLite::SQLite3
    nlohmann_json::nlohmann_json
)

target_include_directories(telemetry_store_test PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/database
)

target_compile_definitions(database_service_test PRIVATE
    SONAR_VALIDATOR_PROBER_SOURCE_DIR="${CMAKE_CURRENT_SOURCE_DIR}"
)

target_link_libraries(routing_table_test PRIVATE
    nlohmann_json::nlohmann_json
)

add_test(NAME database_service_test
    COMMAND database_service_test)

add_test(NAME telemetry_store_test
    COMMAND telemetry_store_test)

add_test(NAME routing_table_test
    COMMAND routing_table_test)

target_link_libraries(prober_config_test PRIVATE
    nlohmann_json::nlohmann_json
)

add_test(NAME prober_config_test 
    COMMAND prober_config_test)


add_test(NAME telemetry_service_test
    COMMAND telemetry_service_test)

# telemetry_service_test 는 봉투 헬퍼(envelope.hpp)를 사용하므로
# 루트와 telemetry 디렉터리를 모두 include 경로에 넣어야 합니다.
target_include_directories(telemetry_service_test PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/telemetry
)

target_link_libraries(telemetry_service_test PRIVATE
    nlohmann_json::nlohmann_json
)

target_link_libraries(envelope_test PRIVATE
    nlohmann_json::nlohmann_json
)

add_test(NAME envelope_test
    COMMAND envelope_test)

target_link_libraries(management_service_integration_test PRIVATE
    nlohmann_json::nlohmann_json
)

target_include_directories(management_service_integration_test PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/telemetry
    ${CMAKE_CURRENT_SOURCE_DIR}/policy
    ${CMAKE_CURRENT_SOURCE_DIR}/router
    ${CMAKE_CURRENT_SOURCE_DIR}/switch
    ${CMAKE_CURRENT_SOURCE_DIR}/firewall
)

# =============================================================================
#  ANTLR CLI 출력 파서 테스트
#
#  docs/Agent_Command.md 의 실제 조회 명령어 출력 샘플을 그대로 넣고
#  JSON 변환 결과를 검증한다. 장비/네트워크가 필요 없다.
#  ANTLR4 툴체인이 없으면 이 테스트는 생성되지 않는다.
# =============================================================================
if(SONAR_ANTLR4_AVAILABLE)
    add_executable(cli_output_parser_test
        parser/cli_output_parser_test.cpp
        ${SONAR_CLI_PARSER_SOURCES}
    )

    target_include_directories(cli_output_parser_test PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/parser
        ${SONAR_ANTLR4_GENERATED_DIR}/grammar
        ${ANTLR4_RUNTIME_ROOT}/include/antlr4-runtime
    )

    target_link_libraries(cli_output_parser_test PRIVATE
        nlohmann_json::nlohmann_json
        ${SONAR_ANTLR4_RUNTIME_LIB}
    )

    add_test(NAME cli_output_parser_test
        COMMAND cli_output_parser_test)

    # 실제 장비 출력 검증용 CLI 도구 (node_probe.py 가 수집한 파일을 먹인다)
    add_executable(cli_output_parser_probe
        parser/cli_output_parser_probe.cpp
        ${SONAR_CLI_PARSER_SOURCES}
    )

    target_include_directories(cli_output_parser_probe PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/parser
        ${SONAR_ANTLR4_GENERATED_DIR}/grammar
        ${ANTLR4_RUNTIME_ROOT}/include/antlr4-runtime
    )

    target_link_libraries(cli_output_parser_probe PRIVATE
        nlohmann_json::nlohmann_json
        ${SONAR_ANTLR4_RUNTIME_LIB}
    )

    # =========================================================================
    #  수집기(command_collector) 테스트
    #
    #  docs/Agent_Command.md 의 실제 조회 출력 샘플을 BuildStateFromOutputs() 에
    #  직접 넣고 검증한다. 장치/네트워크가 필요 없다.
    #  CollectState() 가 ManagementService 의 명령 실행 메서드를 호출하므로
    #  command_collector.cpp 를 링크하려면 management_service 도 함께 링크해야 한다.
    #  ANTLR4 툴체인이 없으면 이 테스트는 생성되지 않는다.
    # =========================================================================
    add_executable(command_collector_test
        collector/command_collector_test.cpp
        collector/command_collector.cpp
        management_service.cpp
        prober_config.cpp
        terminal_session.cpp
        switch/switch.cpp
        firewall/firewall.cpp
        router/routing_table.cpp
        ${SONAR_CLI_PARSER_SOURCES}
    )

    target_include_directories(command_collector_test PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/collector
        ${CMAKE_CURRENT_SOURCE_DIR}/parser
        ${CMAKE_CURRENT_SOURCE_DIR}/policy
        ${CMAKE_CURRENT_SOURCE_DIR}/router
        ${CMAKE_CURRENT_SOURCE_DIR}/switch
        ${CMAKE_CURRENT_SOURCE_DIR}/firewall
        ${CMAKE_CURRENT_SOURCE_DIR}/telemetry
        ${SONAR_ANTLR4_GENERATED_DIR}/grammar
        ${ANTLR4_RUNTIME_ROOT}/include/antlr4-runtime
    )

    target_link_libraries(command_collector_test PRIVATE
        nlohmann_json::nlohmann_json
        ${SONAR_ANTLR4_RUNTIME_LIB}
    )

    add_test(NAME command_collector_test
        COMMAND command_collector_test)
endif()

