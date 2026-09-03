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

add_executable(sonar_validator_prober
    main.cpp
    prober_config.cpp
    database/database_service.cpp
    init.cpp
    management_service.cpp
    telemetry/telemetry_service.hpp
    telemetry/telemetry_service.cpp
    router/routing_table.hpp
    router/routing_table.cpp
    switch/switch.cpp
    switch/switch.hpp
    firewall/firewall.hpp
    firewall/firewall.cpp
    network.hpp
)

target_include_directories(sonar_validator_prober PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/database
    ${CMAKE_CURRENT_SOURCE_DIR}/router
    ${CMAKE_CURRENT_SOURCE_DIR}/switch
    ${CMAKE_CURRENT_SOURCE_DIR}/firewall
    ${CMAKE_CURRENT_SOURCE_DIR}/telemetry
)

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



target_link_libraries(database_service_test PRIVATE
    SQLite::SQLite3
)

target_compile_definitions(database_service_test PRIVATE
    SONAR_VALIDATOR_PROBER_SOURCE_DIR="${CMAKE_CURRENT_SOURCE_DIR}"
)

target_link_libraries(routing_table_test PRIVATE
    nlohmann_json::nlohmann_json
)

add_test(NAME database_service_test
    COMMAND database_service_test)

add_test(NAME routing_table_test
    COMMAND routing_table_test)

target_link_libraries(prober_config_test PRIVATE
    nlohmann_json::nlohmann_json
)

add_test(NAME prober_config_test 
    COMMAND prober_config_test)


add_test(NAME telemetry_service_test
    COMMAND telemetry_service_test)

