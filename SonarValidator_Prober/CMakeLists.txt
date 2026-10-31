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
find_package(OpenSSL REQUIRED COMPONENTS Crypto)

option(SONAR_STATIC_LINK "Link Prober dependencies statically" OFF)
if(SONAR_STATIC_LINK)
    set(_SONAR_SAVED_LIBRARY_SUFFIXES "${CMAKE_FIND_LIBRARY_SUFFIXES}")
    set(CMAKE_FIND_LIBRARY_SUFFIXES ".a")
    find_package(SQLite3 REQUIRED)
    find_library(SONAR_OPENSSL_CRYPTO_LIBRARY NAMES crypto REQUIRED)
    set(CMAKE_FIND_LIBRARY_SUFFIXES "${_SONAR_SAVED_LIBRARY_SUFFIXES}")
    unset(_SONAR_SAVED_LIBRARY_SUFFIXES)
    find_package(OpenSSL REQUIRED COMPONENTS Crypto)
else()
    find_package(OpenSSL REQUIRED COMPONENTS Crypto)
    find_package(SQLite3 REQUIRED)
endif()

# --------------------------------------------------------------- nlohmann/json --
# 시스템 패키지 매니저(APT)로 설치된 nlohmann_json 연동
find_package(nlohmann_json 3.11 REQUIRED)

add_library(sonar_json INTERFACE)
target_link_libraries(sonar_json INTERFACE nlohmann_json::nlohmann_json)

# ---------------------------------------------------------------------- Boost --
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
#  ANTLR4 (Homebrew 자동 탐색 및 경로 보정)
# =============================================================================
# Homebrew 표준 절대 경로 스캔 및 자동 인지
if(NOT ANTLR4_HOME)
    if(EXISTS "/home/linuxbrew/.linuxbrew")
        set(ANTLR4_HOME "/home/linuxbrew/.linuxbrew")
    elseif(EXISTS "$ENV{HOME}/.linuxbrew")
        set(ANTLR4_HOME "$ENV{HOME}/.linuxbrew")
    elseif(EXISTS "/opt/homebrew")
        set(ANTLR4_HOME "/opt/homebrew")
    else()
        execute_process(
            COMMAND brew --prefix
            OUTPUT_VARIABLE BREW_PREFIX
            OUTPUT_STRIP_TRAILING_WHITESPACE
            ERROR_QUIET
        )
        if(BREW_PREFIX)
            set(ANTLR4_HOME "${BREW_PREFIX}")
        endif()
    endif()
endif()

message(STATUS "ANTLR4 Homebrew 루트 감지: ${ANTLR4_HOME}")

set(SONAR_ANTLR4_GENERATED_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/components/parser/generated")

# 커밋된 생성 소스를 그대로 컴파일한다.
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

# 문법 재생성 타깃 정의
add_custom_target(regenerate_parser
    COMMAND sh "${CMAKE_CURRENT_SOURCE_DIR}/components/parser/cmake/regenerate_parser.sh"
    WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}"
    COMMENT "ANTLR4: components/parser/grammar/*.g4 재생성"
    USES_TERMINAL)

# 라이브러리 링크 및 인클루드 패스 바인딩 (antlr4-runtime 헤더 진입 경로 완벽 보정)
link_directories(${ANTLR4_HOME}/lib)
set(SONAR_ANTLR4_INCLUDE_DIRS
    ${SONAR_ANTLR4_GENERATED_DIR}
    ${SONAR_ANTLR4_GENERATED_DIR}/grammar
    ${ANTLR4_HOME}/include
    ${ANTLR4_HOME}/include/antlr4-runtime)

# =============================================================================
#  소스 그룹
# =============================================================================
set(SONAR_PARSER_SOURCES
    components/parser/cli_output_parser.cpp
    ${SONAR_ANTLR4_GENERATED_SOURCES})

set(SONAR_DEVICE_SOURCES
    components/device/router/router.cpp
    components/device/switch/switch_interface/switch.cpp
    components/device/switch/vendor/openvswitch/openvswitch.cpp
    components/device/switch/vendor/arista/aristavEOS.cpp
    components/device/router/routing_table/routing_table.cpp
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
    components/policy/policy_executor.cpp
    components/policy/policy_actions.cpp
    components/policy/policy_command.cpp
    components/policy/policy_receiver.cpp
    components/policy/quarantine_handler.cpp
    components/terminal/command_runner.cpp
    components/terminal/terminal_session.cpp
    utils/cli_options.cpp
    utils/data_directory.cpp)

# -----------------------------------------------------------------------------
#  공통 설정 헬퍼
# -----------------------------------------------------------------------------
function(sonar_configure_target target)
    target_include_directories(${target} PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${SONAR_ANTLR4_INCLUDE_DIRS})

    target_link_libraries(${target} PUBLIC
        sonar_json
        sonar_boost
        Threads::Threads
        antlr4-runtime)

    target_compile_definitions(${target} PUBLIC SONAR_HAS_ANTLR4=1)
endfunction()

# =============================================================================
#  프로버 실행 파일
# =============================================================================
add_library(sonar_prober_core STATIC
    workers/TerminalAgentWorker.cpp
    ${SONAR_PARSER_SOURCES}
    ${SONAR_DEVICE_SOURCES}
    ${SONAR_MODULE_SOURCES}
    ${SONAR_DATABASE_SOURCES}
    ${SONAR_SUPPORT_SOURCES})
sonar_configure_target(sonar_prober_core)
if(SONAR_STATIC_LINK)
    target_link_libraries(sonar_prober_core PUBLIC SQLite::SQLite3 ${SONAR_OPENSSL_CRYPTO_LIBRARY})
else()
    target_link_libraries(sonar_prober_core PUBLIC SQLite::SQLite3 OpenSSL::Crypto)
endif()

add_executable(sonar_validator_prober main.cpp)
target_link_libraries(sonar_validator_prober PRIVATE sonar_prober_core)
target_compile_definitions(sonar_validator_prober PRIVATE $<$<CONFIG:Debug>:SONAR_DEBUG_BUILD=1>)

option(SONAR_BUILD_TESTS "Build Prober contract and regression tests" ON)
if(SONAR_BUILD_TESTS)
    file(GLOB_RECURSE SONAR_TEST_SOURCES CONFIGURE_DEPENDS
        "${CMAKE_CURRENT_SOURCE_DIR}/components/*_test.cpp"
        "${CMAKE_CURRENT_SOURCE_DIR}/module/*_test.cpp"
        "${CMAKE_CURRENT_SOURCE_DIR}/database/*_test.cpp")
    foreach(source IN LISTS SONAR_TEST_SOURCES)
        get_filename_component(name "${source}" NAME_WE)
        add_executable("sonar_test_${name}" "${source}")
        target_link_libraries("sonar_test_${name}" PRIVATE sonar_prober_core)
        target_compile_options("sonar_test_${name}" PRIVATE -UNDEBUG)
        add_test(NAME "${name}" COMMAND "sonar_test_${name}")
        set_tests_properties("${name}" PROPERTIES TIMEOUT 120)
    endforeach()
    add_test(NAME ios_snapshot_publisher COMMAND bash "${CMAKE_CURRENT_SOURCE_DIR}/../tests/prober/ios-snapshot-publisher.sh")
    set_tests_properties(ios_snapshot_publisher PROPERTIES TIMEOUT 30)
endif()

add_custom_target(installer_bundle
    COMMAND ${CMAKE_COMMAND} -E make_directory "${CMAKE_CURRENT_SOURCE_DIR}/Installer/systemd"
    COMMAND ${CMAKE_COMMAND} -E make_directory "${CMAKE_CURRENT_SOURCE_DIR}/Installer/bin"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "$<TARGET_FILE:sonar_validator_prober>" "${CMAKE_CURRENT_SOURCE_DIR}/Installer/sonar_validator_prober"
    DEPENDS sonar_validator_prober)
