grammar Calculator;

// 파서(Parser) 규칙 (소문자로 시작)
expr: expr ('*'|'/') expr   # MulDiv
    | expr ('+'|'-') expr   # AddSub
    | INT                   # Int
    | '(' expr ')'          # Parens
    ;

// 렉서(Lexer) 규칙 (대문자로 시작)
INT : [0-9]+ ;
WS  : [ \t\r\n]+ -> skip ; // 공백은 무시