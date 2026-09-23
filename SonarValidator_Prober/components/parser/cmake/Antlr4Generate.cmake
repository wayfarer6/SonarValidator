# =============================================================================
#  ANTLR4 문법 생성 규칙
#
#  grammar/*.g4 -> generated/grammar/*.cpp|*.h (+ visitor)
#
#  생성 코드는 저장소에 커밋하지 않고 빌드 시 만든다(툴 버전 편차 방지).
#  ANTLR4 툴체인이 없는 환경에서는 SONAR_ANTLR4_AVAILABLE=FALSE 이므로
#  이 파일을 include 하지 말고 정적 파서 폴백을 쓰면 된다.
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
