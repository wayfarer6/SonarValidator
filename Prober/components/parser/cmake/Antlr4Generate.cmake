# =============================================================================
#  ANTLR4 문법 생성 함수 (문법을 실제로 고칠 때만 사용)
#
#  grammar/*.g4 -> ${SONAR_ANTLR4_GENERATED_DIR}/grammar/*.cpp|*.h (+ visitor)
#
#  평상시 빌드 경로에서는 이 함수를 호출하지 않는다.
#  생성 코드(components/parser/generated/grammar)가 저장소에 커밋되어 있어
#  Java/jar 없이 그대로 컴파일되기 때문이다.
#
#  문법을 수정했을 때는 다음 중 하나를 쓴다.
#    - cmake --build build --target regenerate_parser
#    - sh components/parser/cmake/regenerate_parser.sh
# =============================================================================

function(sonar_add_antlr4_grammar grammar_file)
    get_filename_component(_grammar_name "${grammar_file}" NAME_WE)
    set(_out_dir "${SONAR_ANTLR4_GENERATED_DIR}/grammar")

    file(GLOB _generated_sources
        "${_out_dir}/${_grammar_name}Lexer.cpp"
        "${_out_dir}/${_grammar_name}Parser.cpp"
        "${_out_dir}/${_grammar_name}BaseVisitor.cpp"
        "${_out_dir}/${_grammar_name}Visitor.cpp")

    add_custom_command(
        OUTPUT ${_generated_sources}
        COMMAND ${CMAKE_COMMAND} -E make_directory "${_out_dir}"
        COMMAND "${Java_JAVA_EXECUTABLE}" -jar "${ANTLR4_JAR}"
                -Dlanguage=Cpp
                -visitor
                -no-listener
                -lib "${CMAKE_CURRENT_SOURCE_DIR}/components/parser/grammar"
                -o "${SONAR_ANTLR4_GENERATED_DIR}"
                -Xexact-output-dir
                "${grammar_file}"
        DEPENDS "${grammar_file}"
        COMMENT "ANTLR4: generating ${_grammar_name} parser"
        VERBATIM)

    set(SONAR_ANTLR4_GENERATED_SOURCES
        ${SONAR_ANTLR4_GENERATED_SOURCES} ${_generated_sources}
        PARENT_SCOPE)
endfunction()
