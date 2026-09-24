cmake_minimum_required(VERSION 3.20)

project(SonarValidatorProber LANGUAGES CXX)

enable_testing()

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Debug CACHE STRING "" FORCE)
endif()

# clangd / IDE 가 include 경로를 알 수 있게 한다.
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

find_package(Threads REQUIRED)
find_package(SQLite3 REQUIRED)

# --------------------------------------------------------------- nlohmann/json --
# 시스템에 설치된 헤더를 우선 쓰고, 없으면 FetchContent 로 받는다.
# 두 경우 모두 sonar_json 인터페이스 타깃 하나로 감싼다.
find_package(nlohmann_json 3.11 QUIET)

add_library(sonar_json INTERFACE)
if(nlohmann_json_FOUND)
    target_link_libraries(sonar_json INTERFACE nlohmann_json::nlohmann_json)
else()
    find_path(SONAR_NLOHMANN_JSON_INCLUDE_DIR nlohmann/json.hpp)
    if(NOT SONAR_NLOHMANN_JSON_INCLUDE_DIR)
        include(FetchContent)
        FetchContent_Declare(
            nlohmann_json
            URL https://github.com/nlohmann/json/releases/download/v3.11.3/json.tar.xz
        )
        FetchContent_MakeAvailable(nlohmann_json)
        target_link_libraries(sonar_json INTERFACE nlohmann_json::nlohmann_json)
    else()
        message(STATUS "nlohmann/json: ${SONAR_NLOHMANN_JSON_INCLUDE_DIR}")
        target_include_directories(sonar_json INTERFACE
            ${SONAR_NLOHMANN_JSON_INCLUDE_DIR})
    endif()
endif()

# ---------------------------------------------------------------------- Boost --
# 관리/텔레메트리 통신은 Boost.Asio + Beast 를 헤더 전용으로 쓴다.
# (링크할 라이브러리가 없어 boost_system 은 넣지 않는다.)
find_package(Boost QUIET)

add_library(sonar_boost INTERFACE)
if(Boost_FOUND)
    target_include_directories(sonar_boost INTERFACE ${Boost_INCLUDE_DIRS})
    message(STATUS "Boost: ${Boost_INCLUDE_DIRS}")
else()
    find_path(SONAR_BOOST_INCLUDE_DIR boost/asio.hpp)
    if(NOT SONAR_BOOST_INCLUDE_DIR)
        message(FATAL_ERROR "Boost.Asio 헤더(boost/asio.hpp)를 찾지 못했습니다.")
    endif()
    message(STATUS "Boost: ${SONAR_BOOST_INCLUDE_DIR}")
    target_include_directories(sonar_boost INTERFACE ${SONAR_BOOST_INCLUDE_DIR})
endif()

# =============================================================================
#  ANTLR4 (벤더 CLI 조회 출력 파서)
#
#  components/parser/grammar/*.g4 에서 생성한 C++ 파서는
#  components/parser/generated/grammar 에 커밋되어 있다.
#  따라서 일반 빌드에는 ANTLR4 "C++ 런타임 라이브러리" 만 있으면 되고,
#  Java/jar 는 문법을 고칠 때만 쓴다 (components/parser/cmake/regenerate_parser.sh).
#
#  문법을 바꿨다면
#    1) regenerate_parser.sh 로 생성 코드 갱신
#    2) 빌드는 평소처럼 cmake --build build
# =============================================================================
set(SONAR_ANTLR4_GENERATED_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/components/parser/generated")

include(${CMAKE_CURRENT_SOURCE_DIR}/components/parser/cmake/FindANTLR4Runtime.cmake)

# 커밋된 생성 소스를 그대로 컴파일한다. (재생성은 스크립트로만)
file(GLOB SONAR_ANTLR4_GENERATED_SOURCES CONFIGURE_DEPENDS
    "${SONAR_ANTLR4_GENERATED_DIR}/grammar/*Lexer.cpp"
    "${SONAR_ANTLR4_GENERATED_DIR}/grammar/*Parser.cpp"
    "${SONAR_ANTLR4_GENERATED_DIR}/grammar/*BaseVisitor.cpp"
    "${SONAR_ANTLR4_GENERATED_DIR}/grammar/*Visitor.cpp")

list(LENGTH SONAR_ANTLR4_GENERATED_SOURCES _sonar_generated_count)
if(_sonar_generated_count EQUAL 0)
    message(FATAL_ERROR
        "생성된 ANTLR 파서가 없습니다: ${SONAR_ANTLR4_GENERATED_DIR}/grammar\n"
        "components/parser/grammar/*.g4 를 regenerate_parser.sh 로 생성하세요.")
endif()
message(STATUS "ANTLR4: 커밋된 생성 소스 ${_sonar_generated_count}개")

# 문법을 고쳤을 때만 실행하는 편의 타깃. (jar 필요)
add_custom_target(regenerate_parser
    COMMAND sh "${CMAKE_CURRENT_SOURCE_DIR}/components/parser/cmake/regenerate_parser.sh"
    WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}"
    COMMENT "ANTLR4: components/parser/grammar/*.g4 재생성"
    USES_TERMINAL)

set(SONAR_ANTLR4_INCLUDE_DIRS
    ${SONAR_ANTLR4_GENERATED_DIR}
    ${SONAR_ANTLR4_GENERATED_DIR}/grammar)

if(SONAR_ANTLR4_AVAILABLE)
    list(APPEND SONAR_ANTLR4_INCLUDE_DIRS
        ${ANTLR4_RUNTIME_ROOT}/include/antlr4-runtime)
else()
    message(FATAL_ERROR
        "ANTLR4 C++ 런타임을 찾지 못했습니다. (파서 없이는 프로버가 동작하지 않습니다)\n"
        "  ANTLR4_RUNTIME_ROOT 로 lib/libantlr4-runtime.a 와\n"
        "  include/antlr4-runtime/ 가 있는 디렉터리를 지정하세요.")
endif()

# =============================================================================
#  소스 그룹
#
#  components/  : 재사용 가능한 부품 (장치/통신/정책/터미널/파서)
#  module/      : 조립·실행 단위 (설정/초기화/관리/텔레메트리/오프라인)
#  database/    : SQLite 영속 계층
#  utils/       : CLI 옵션 / 경로 결정
#  workers/     : 스레드 진입점 (헤더 전용)
# =============================================================================

set(SONAR_PARSER_SOURCES
    components/parser/cli_output_parser.cpp
    ${SONAR_ANTLR4_GENERATED_SOURCES})

# 장치별 구현. 모두 "JSON -> 모델" 로만 변환하고 파싱은 parser 에 위임한다.
set(SONAR_DEVICE_SOURCES
    components/device/switch/switch_interface/switch.cpp
    components/device/switch/vendor/openvswitch/openvswitch.cpp
    components/device/switch/vendor/arista/aristavEOS.cpp
    components/device/router/routing_table/routing_table.cpp
    components/device/router/vendor/cisco/cisco8000v.cpp
    components/device/firewall/firewall_interface/firewall.cpp
    components/device/vm/ubuntu/vm_service.cpp)

set(SONAR_MODULE_SOURCES
    module/configuration_module/prober_config.cpp
    module/initializing_module/init.cpp
    module/management_module/management_service.cpp
    module/offline_export_module/offline_export.cpp
    module/telemetry_module/telemetry_monitor.cpp
    module/telemetry_module/telemetry_service.cpp
    module/telemetry_module/command_collector.cpp)

set(SONAR_DATABASE_SOURCES
    database/database_service.cpp
    database/schema.cpp
    database/telemetry_store.cpp)

set(SONAR_SUPPORT_SOURCES
    components/policy/policy_receiver.cpp
    components/terminal/terminal_session.cpp
    utils/cli_options.cpp
    utils/data_directory.cpp)

# -----------------------------------------------------------------------------
#  공통 설정 헬퍼
#   - include 경로는 "저장소 루트 하나" 만 쓴다. (모든 include 는 root 기준)
#   - ANTLR 생성 헤더 경로와 런타임 include 를 함께 넣는다.
# -----------------------------------------------------------------------------
function(sonar_configure_target target)
    target_include_directories(${target} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${SONAR_ANTLR4_INCLUDE_DIRS})

    target_link_libraries(${target} PRIVATE
        sonar_json
        sonar_boost
        Threads::Threads
        ${SONAR_ANTLR4_RUNTIME_LIB})

    target_compile_definitions(${target} PRIVATE SONAR_HAS_ANTLR4=1)
endfunction()

# =============================================================================
#  프로버 실행 파일
# =============================================================================
add_executable(sonar_validator_prober
    main.cpp
    ${SONAR_PARSER_SOURCES}
    ${SONAR_DEVICE_SOURCES}
    ${SONAR_MODULE_SOURCES}
    ${SONAR_DATABASE_SOURCES}
    ${SONAR_SUPPORT_SOURCES})

sonar_configure_target(sonar_validator_prober)

target_link_libraries(sonar_validator_prober PRIVATE SQLite::SQLite3)

# =============================================================================
#  도구: 스키마 DDL 덤프
#   ./build/schema_dump | sqlite3 Installer/default_template.sqlite
# =============================================================================
add_executable(schema_dump
    database/schema_dump.cpp
    database/schema.cpp)

sonar_configure_target(schema_dump)

# =============================================================================
#  테스트
# =============================================================================

# 설정 파싱 (settings.conf <-> ProberConfig)
add_executable(prober_config_test
    module/configuration_module/prober_config_test.cpp
    module/configuration_module/prober_config.cpp)
sonar_configure_target(prober_config_test)
target_link_libraries(prober_config_test PRIVATE SQLite::SQLite3)
add_test(NAME prober_config_test COMMAND prober_config_test)

# DB 큐 / 태스크 (sqlite3 핸들은 DB 스레드만 만진다)
add_executable(database_service_test
    database/database_service_test.cpp
    database/database_service.cpp)
sonar_configure_target(database_service_test)
target_link_libraries(database_service_test PRIVATE SQLite::SQLite3)
target_compile_definitions(database_service_test PRIVATE
    SONAR_VALIDATOR_PROBER_SOURCE_DIR="${CMAKE_CURRENT_SOURCE_DIR}")
add_test(NAME database_service_test COMMAND database_service_test)

# 수집 상태 저장 헬퍼 (라우팅/NIC/VLAN/트렁크/ARP)
add_executable(telemetry_store_test
    database/telemetry_store_test.cpp
    database/telemetry_store.cpp
    database/schema.cpp
    database/database_service.cpp)
sonar_configure_target(telemetry_store_test)
target_link_libraries(telemetry_store_test PRIVATE SQLite::SQLite3)
add_test(NAME telemetry_store_test COMMAND telemetry_store_test)

# 통신 프로토콜 봉투 (네트워크 불필요)
add_executable(envelope_test
    components/backend_communication/envelope_test.cpp)
sonar_configure_target(envelope_test)
add_test(NAME envelope_test COMMAND envelope_test)

# 라우팅 테이블 (ANTLR ParseRouteStatus 위임 검증)
add_executable(routing_table_test
    components/device/router/routing_table/routing_table_test.cpp
    components/device/router/routing_table/routing_table.cpp
    ${SONAR_PARSER_SOURCES})
sonar_configure_target(routing_table_test)
add_test(NAME routing_table_test COMMAND routing_table_test)

# 스위치 (ANTLR ParseOvsTopology / ParseRunningConfig 위임 검증)
add_executable(switch_test
    components/device/switch/switch_interface/switch_test.cpp
    components/device/switch/switch_interface/switch.cpp
    components/device/switch/vendor/openvswitch/openvswitch.cpp
    components/device/switch/vendor/arista/aristavEOS.cpp
    components/device/router/vendor/cisco/cisco8000v.cpp
    components/device/router/routing_table/routing_table.cpp
    ${SONAR_PARSER_SOURCES})
sonar_configure_target(switch_test)
add_test(NAME switch_test COMMAND switch_test)

# 방화벽 (ANTLR ParseFirewallRules 위임 검증)
add_executable(firewall_test
    components/device/firewall/firewall_interface/firewall_test.cpp
    components/device/firewall/firewall_interface/firewall.cpp
    ${SONAR_PARSER_SOURCES})
sonar_configure_target(firewall_test)
add_test(NAME firewall_test COMMAND firewall_test)

# 텔레메트리 WebSocket 클라이언트 (네트워크 불필요 — 계약만 검증)
add_executable(telemetry_service_test
    module/telemetry_module/telemetry_service_test.cpp
    module/telemetry_module/telemetry_service.cpp)
sonar_configure_target(telemetry_service_test)
add_test(NAME telemetry_service_test COMMAND telemetry_service_test)

# 오프라인 스냅샷 export (서버/장비 불필요)
add_executable(offline_export_test
    module/offline_export_module/offline_export_test.cpp
    module/offline_export_module/offline_export.cpp)
sonar_configure_target(offline_export_test)
add_test(NAME offline_export_test COMMAND offline_export_test)

# 관리 서비스 통합 (mock 서버 없으면 SKIP → 종료코드 0)
add_executable(management_service_integration_test
    module/management_module/management_service_integration_test.cpp
    ${SONAR_MODULE_SOURCES}
    ${SONAR_DEVICE_SOURCES}
    ${SONAR_SUPPORT_SOURCES}
    ${SONAR_DATABASE_SOURCES}
    ${SONAR_PARSER_SOURCES})
sonar_configure_target(management_service_integration_test)
target_link_libraries(management_service_integration_test PRIVATE SQLite::SQLite3)
add_test(NAME management_service_integration_test
    COMMAND management_service_integration_test)

# ANTLR CLI 출력 파서 (docs 의 실제 조회 출력 샘플 169 케이스)
add_executable(cli_output_parser_test
    components/parser/cli_output_parser_test.cpp
    ${SONAR_PARSER_SOURCES})
sonar_configure_target(cli_output_parser_test)
add_test(NAME cli_output_parser_test COMMAND cli_output_parser_test)

# 실제 장비 출력 검증용 CLI 도구 (probe.py 가 수집한 파일을 먹인다)
add_executable(cli_output_parser_probe
    components/parser/cli_output_parser_probe.cpp
    ${SONAR_PARSER_SOURCES})
sonar_configure_target(cli_output_parser_probe)

# 수집기 — 조회 명령 실행 + 파서 호출
add_executable(command_collector_test
    module/telemetry_module/command_collector_test.cpp
    ${SONAR_MODULE_SOURCES}
    ${SONAR_DEVICE_SOURCES}
    ${SONAR_SUPPORT_SOURCES}
    ${SONAR_DATABASE_SOURCES}
    ${SONAR_PARSER_SOURCES})
sonar_configure_target(command_collector_test)
target_link_libraries(command_collector_test PRIVATE SQLite::SQLite3)
add_test(NAME command_collector_test COMMAND command_collector_test)