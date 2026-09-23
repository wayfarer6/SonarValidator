# =============================================================================
#  ANTLR4 C++ 런타임/툴체인 탐색
#
#  jar/런타임 설치 위치는 환경마다 다르므로 아래 순서로 찾는다.
#    1) CMake 캐시 변수 (ANTLR4_JAR / ANTLR4_RUNTIME_ROOT)
#    2) 환경변수 (ANTLR4_JAR / ANTLR4_RUNTIME_ROOT)
#    3) 일반적인 설치 경로
#    4) PATH 의 antlr4 실행 파일
#  런타임을 못 찾으면 SONAR_ANTLR4_AVAILABLE=FALSE 로 두고,
#  상위 CMakeLists 가 문법 생성을 건너뛴다(정적 파서 폴백 유지).
# =============================================================================

find_package(Java QUIET)

# ---------------------------------------------------------------- ANTLR jar --
if(NOT ANTLR4_JAR)
    if(DEFINED ENV{ANTLR4_JAR})
        set(ANTLR4_JAR "$ENV{ANTLR4_JAR}")
    else()
        foreach(_candidate
                "$ENV{HOME}/tools/antlr.jar"
                "$ENV{HOME}/antlr/antlr-4.13.2-complete.jar"
                "/usr/local/lib/antlr-4.13.2-complete.jar"
                "/usr/share/java/antlr4.jar")
            if(EXISTS "${_candidate}")
                set(ANTLR4_JAR "${_candidate}")
                break()
            endif()
        endforeach()
    endif()
endif()

# ------------------------------------------------------------ ANTLR runtime --
if(NOT ANTLR4_RUNTIME_ROOT)
    if(DEFINED ENV{ANTLR4_RUNTIME_ROOT})
        set(ANTLR4_RUNTIME_ROOT "$ENV{ANTLR4_RUNTIME_ROOT}")
    else()
        foreach(_candidate
                "$ENV{HOME}/tools/antlr4-install"
                "$ENV{HOME}/.local"
                "/usr/local"
                "/usr")
            if(EXISTS "${_candidate}/include/antlr4-runtime/antlr4-runtime.h")
                set(ANTLR4_RUNTIME_ROOT "${_candidate}")
                break()
            endif()
        endforeach()
    endif()
endif()

set(SONAR_ANTLR4_AVAILABLE FALSE)

if(ANTLR4_JAR AND EXISTS "${ANTLR4_JAR}" AND ANTLR4_RUNTIME_ROOT)
    # 주의: find_library 호출 전에 결과 변수를 미리 set() 하면
    #       "이미 정의됨" 으로 판단되어 탐색이 건너뛰어진다. (set 금지)
    find_library(SONAR_ANTLR4_RUNTIME_LIB
        NAMES antlr4-runtime
        PATHS "${ANTLR4_RUNTIME_ROOT}/lib"
        NO_DEFAULT_PATH)

    if(SONAR_ANTLR4_RUNTIME_LIB)
        set(SONAR_ANTLR4_AVAILABLE TRUE)
        message(STATUS "ANTLR4: jar=${ANTLR4_JAR}")
        message(STATUS "ANTLR4: runtime=${SONAR_ANTLR4_RUNTIME_LIB}")
        message(STATUS "ANTLR4: headers=${ANTLR4_RUNTIME_ROOT}/include/antlr4-runtime")
    else()
        message(STATUS "ANTLR4: runtime library not found under ${ANTLR4_RUNTIME_ROOT}")
    endif()
else()
    message(STATUS "ANTLR4: toolchain not found (jar=${ANTLR4_JAR} root=${ANTLR4_RUNTIME_ROOT})")
endif()
